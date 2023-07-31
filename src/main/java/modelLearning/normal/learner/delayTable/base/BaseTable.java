
package modelLearning.normal.learner.delayTable.base;

import lombok.Data;
import lombok.Getter;
import modelLearning.Config;
import modelLearning.normal.learner.delayTable.plugins.Debugger;
import modelLearning.normal.learner.delayTable.Prefix;
import modelLearning.normal.learner.delayTable.guess.GuessTable;
import modelLearning.normal.learner.delayTable.guess.GuessTableBuilder;
import modelLearning.normal.learner.delayTable.guess.GuessTableRecorder;
import modelLearning.normal.learner.delayTable.plugins.ResetDeterminer;
import modelLearning.normal.learner.delayTable.plugins.SearchTreeCache;
import modelLearning.normal.teacher.BooleanAnswer;
import modelLearning.normal.teacher.NormalTeacher;
import modelLearning.normal.learner.delayTable.plugins.TWNormalizer;
import modelLearning.util.IntegerEntity;
import modelLearning.util.LogUtil;
import modelLearning.util.TimeWordHelper;
import modelLearning.util.time.TimeManager;
import modelLearning.util.time.Timer;
import ta.ota.DOTA;
import ta.ota.DOTAUtil;
import ta.ota.ResetLogicTimeWord;
import timedAction.DelayTimedAction;
import timedWord.DelayTimeWord;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class BaseTable {
    private final Set<String> sigma;
    // key: hash(val)
    // val: val
    @Getter
    private final Map<Integer, Prefix<DelayTimeWord>> S = new HashMap<>();
    private final Map<Integer, Prefix<DelayTimeWord>> R = new HashMap<>();
    private final Set<DelayTimeWord> E = new HashSet<>();

    @Getter
    private final DelayTableRecorder tableRecorder = new DelayTableRecorder();
    private final NormalTeacher teacher;


    /* -------------- 插件 --------------  */
    private final TWNormalizer normalizer = new TWNormalizer();
    private SearchTreeCache searchTreeCache;
    @Getter
    private Debugger debugger;

    private ResetDeterminer resetDeterminer;

    private static final boolean[] TRUE_FALSE = {true, false};

    private static final boolean[] TRUE = {true};

    private static final boolean[] FALSE = {false};

    public BaseTable(Set<String> sigma, NormalTeacher teacher) {
        this.teacher = teacher;
        this.sigma = sigma;
    }

    /**
     * 初始化
     */
    public void init() {
        if (Config.debug) {
            this.debugger = new Debugger(this, teacher.getDota());
        }
        if (Config.useSearchTree) {
            this.searchTreeCache = new SearchTreeCache();
        }
        if (Config.determineReset) {
            this.resetDeterminer = new ResetDeterminer(teacher);
        }
        E.add(DelayTimeWord.emptyWord());
        List<DelayTimeWord> prefixes = new ArrayList<>(1 + sigma.size());
        prefixes.add(DelayTimeWord.emptyWord());
        for (String symbol : sigma) {
            DelayTimedAction action = new DelayTimedAction(symbol, 0d);
            prefixes.add(new DelayTimeWord(Collections.singletonList(action)));
        }
        addNewPrefixes(prefixes, true);
    }

    // refine ctx时添加新的前缀
    // 1. 过滤观察表已有的前缀
    // 2. 填充观察表
    // 3. 一致性检查
    // 4. close检查
    public void addNewPrefixes(List<DelayTimeWord> prefixes, boolean init) {
        // 处理并过滤新前缀，生成最终需要更新到观察表的前缀: 记录到filteredPrefixes中
        List<Prefix<DelayTimeWord>> filteredPrefixes = new ArrayList<>();
        List<Prefix<DelayTimeWord>> prefixEntities = prefixes.stream().filter(timeWord -> {
                    int hash = timeWord.hashCode();
                    return !S.containsKey(hash) && !R.containsKey(hash);
                }).map(timeWord -> new Prefix<>(timeWord, teacher.membership(timeWord)))
                .sorted(Comparator.comparingInt(prefix -> prefix.getTimedWord().size()))
                .collect(Collectors.toList());
        LogUtil.info("新增%d个前缀", prefixEntities.size());
        if (prefixEntities.size() == 0) {
            throw new RuntimeException("无新增前缀，无法进一步学习");
        }
        boolean findSink = false;
        for (Prefix<DelayTimeWord> newPrefix : prefixEntities) {
            if (!init && findSink) {
                break;
            }
            R.put(newPrefix.hashCode(), newPrefix);
            filteredPrefixes.add(newPrefix);
            findSink = newPrefix.getAnswer().isSink();
        }

        // 更新plugin
        if (searchTreeCache != null) {
            searchTreeCache.refresh();
        }
        if (debugger != null) {
            debugger.appendNewPrefixes(filteredPrefixes);
        }
        if (resetDeterminer != null) {
            List<Prefix<DelayTimeWord>> ps = getAllPrefixes();
            for (Prefix<DelayTimeWord> newPrefix : filteredPrefixes) {
                resetDeterminer.determineAndRecheck(newPrefix, ps);
//                resetDeterminer.determineReset(newPrefix, null);
            }
        }

        // 填充观察表
        fillTable(filteredPrefixes, E);
        IntegerEntity intVal = new IntegerEntity();
        while (true) {
            DelayTimeWord newSuffix = isConsistent(filteredPrefixes, intVal);
            if (newSuffix == null) {
                break;
            }
            makeConsistent(newSuffix);
        }
        makeClosed();
    }

    /**
     * 猜测重置信息生成所有的猜测观察表
     */
    public List<List<GuessTable>> guessAllPossibleTables(boolean useFastSearch, int sLimit, boolean cacheNext) {

        Set<DelayTimeWord> keyResetPrefix = Config.aggressiveSearch ? getKeyResetPrefixes() : getAllPrefixSet();
        // 不需要猜测重置信息
        if (keyResetPrefix.size() == 0) {
            List<Prefix<DelayTimeWord>> sortedPrefixes = getAllPrefixesIgnoreSROrderByWeight(keyResetPrefix);
            GuessTable table = getGuessTable2(new HashSet<>(0), sortedPrefixes);
            if (table == null) {
                throw new RuntimeException("需要猜测非关键前缀的重置信息");
            }
            table.makeClosed();
            List<GuessTable> pack = new ArrayList<>();
            pack.add(table);
            List<List<GuessTable>> result = new ArrayList<>();
            result.add(pack);
            return result;
        }
        List<GuessTable> guessTables = useFastSearch ? getGuessTablesInSearchTree(keyResetPrefix, sLimit, cacheNext) : getGuessTablesInLines(keyResetPrefix);
        LogUtil.info("guessTable size: %d\n", guessTables.size());
        if (guessTables.size() == 0) {
            return null;
//            throw new RuntimeException("未构建出任何猜测观察表，请检查代码");
        }
        // 按照s区大小排序merge
        List<List<GuessTable>> result = new ArrayList<>();
        List<GuessTable> firstList = new ArrayList<>();
        firstList.add(guessTables.get(0));
        result.add(firstList);
        for (int i = 1; i < guessTables.size(); i++) {
            GuessTable beforeTable = guessTables.get(i - 1);
            GuessTable curTable = guessTables.get(i);
            if (beforeTable.getS().size() == curTable.getS().size()) {
                List<GuessTable> lastList = result.get(result.size() - 1);
                lastList.add(curTable);
            } else {
                List<GuessTable> newList = new ArrayList<>();
                newList.add(curTable);
                result.add(newList);
            }
        }
        LogUtil.info("guessTable group: %d\n", result.size());
        // 需要猜测重置信息的情况
        return result;
    }

    /**
     *
     *
     */
    public DOTA getHypothesis(int minSLimit, int maxSLimit, String name) {
        Set<DelayTimeWord> keyResetPrefix = Config.aggressiveSearch ? getKeyResetPrefixes() : getAllPrefixSet();
        // 不需要猜测重置信息
        if (keyResetPrefix.size() == 0) {
            List<Prefix<DelayTimeWord>> sortedPrefixes = getAllPrefixesIgnoreSROrderByWeight(keyResetPrefix);
            GuessTable table = getGuessTable2(new HashSet<>(0), sortedPrefixes);
            if (table == null) {
                throw new RuntimeException("需要猜测非关键前缀的重置信息");
            }
            table.makeClosed();
            DOTA hypothesis = table.buildHypothesis(name);
            return hypothesis;
        }
        Timer solveLocationTimer = TimeManager.getDefaultManager().getOrRegisterTimer("基于baseTable构建hypothesis操作");
        solveLocationTimer.start();
        for (int s = minSLimit; s<=maxSLimit; s++) {
            DOTA hypothesis = getGuessTableHypothesis(keyResetPrefix, s, s!=maxSLimit, name);
            if (hypothesis!=null) {
                solveLocationTimer.pause();
                return hypothesis;
            }
            LogUtil.info("在S区间限制%d下无解，即将尝试更大的S区间\n", s);
        }
        solveLocationTimer.pause();
        throw new RuntimeException("无法生成目标假设自动机");
    }

    /**
     * 获得一个前缀的权重
     * 基础权重为前缀的size * 100 + 100
     * 1. 如果前缀是sink的 或者 前缀的重置是确定的， 权重为size - 10
     * 2. 如果前缀是关键前缀，权重为size - 4
     */
    private int getPrefixWeight(Prefix<DelayTimeWord> prefix, Set<DelayTimeWord> keyResetPrefix) {
        int weight = prefix.getTimedWord().size() * 100 + 100;
        if (prefix.getAnswer().isSink()) {
            weight -= 10;
        }
        if (resetDeterminer != null && resetDeterminer.getReset().containsKey(prefix.getTimedWord())) {
            weight -= 10;
        }
        if (keyResetPrefix.contains(prefix.getTimedWord())) {
            weight -= 4;
        }
        return weight;
    }

    public DOTA getGuessTableHypothesis(Set<DelayTimeWord> keyResetPrefix, int maxSLen, boolean cacheNext, String name) {
        List<Prefix<DelayTimeWord>> sortedPrefixes = getAllPrefixesIgnoreSROrderByWeight(keyResetPrefix);
        List<GuessTableBuilder> result = new ArrayList<>();
        Integer beginIdx = searchTreeCache.getFirstIdx();
        if (beginIdx == null) {
            beginIdx = 0;
            GuessTableBuilder init = new GuessTableBuilder(new HashSet<>(), new HashSet<>(), new GuessTableRecorder(new HashMap<>()), sigma);
            init.initBuilder(sortedPrefixes, this, normalizer);
            init.appendPrefix(0, true);
            result.add(init);
        }
        int guessPrefixCount = 0;
        List<boolean[]> guessBooleans = new ArrayList<>(sortedPrefixes.size());
        Set<Prefix<DelayTimeWord>> needGuessPrefixes = new HashSet<>();
        for (Prefix<DelayTimeWord> prefix : sortedPrefixes) {
            boolean[] guessBoolean;
            boolean fixedGuess;
            if (!Config.aggressiveSearch || keyResetPrefix.contains(prefix.getTimedWord())) {
                guessBoolean = TRUE_FALSE;
                fixedGuess = true;
            } else {
                // 激进搜索时的非关键前缀不猜测
                guessBoolean = FALSE;
                fixedGuess = false;
            }
            if (prefix.getAnswer().isSink()) {
                guessBoolean = TRUE;
                fixedGuess = true;
            }
            if (resetDeterminer != null) {
                Boolean reset = resetDeterminer.getReset().get(prefix.getTimedWord());
                if (reset != null) {
                    fixedGuess = true;
                    if (reset) {
                        guessBoolean = TRUE;
                    } else {
                        guessBoolean = FALSE;
                    }
                }
            }
            if (guessBoolean == TRUE_FALSE) {
                guessPrefixCount++;
            }
            if (!fixedGuess || guessBoolean == TRUE_FALSE) {
                needGuessPrefixes.add(prefix);
            }
            guessBooleans.add(guessBoolean);
        }

        LogUtil.info("即将构建猜测观察表，最大数量约为2^%d ~ 2^%d", guessPrefixCount, needGuessPrefixes.size());

        for (int i = beginIdx + 1; i < sortedPrefixes.size(); i++) {
            List<GuessTableBuilder> cachedResult = searchTreeCache.getCache(i - 1);
            if (cachedResult != null) {
                result.addAll(cachedResult);
            }
            List<GuessTableBuilder> tmp = new ArrayList<>(result.size());
            List<GuessTableBuilder> cacheTables = new ArrayList<>();
            boolean[] guessBoolean = guessBooleans.get(i);
            boolean fixedGuess = guessBoolean == TRUE_FALSE || !needGuessPrefixes.contains(sortedPrefixes.get(i));

            boolean lastCheck = i == sortedPrefixes.size()-1;
            AtomicBoolean solved = new AtomicBoolean(false);

            for (boolean guess : guessBoolean) {
                int finalI = i;
                Map<Boolean, List<GuessTableBuilder>> collect = result.parallelStream().map(table -> {
                    if (solved.get()) return null;
                    GuessTableBuilder copy = table.deepClone();
                    if (solved.get()) return null;
                    boolean success = copy.appendPrefix(finalI, guess);
                    if (!success && !fixedGuess) {
                        if (solved.get()) return null;
                        copy = table.deepClone();
                        success = copy.appendPrefix(finalI, !guess);
                    }
                    if (!success || copy.getS().size() > maxSLen + 1) {
                        return null;
                    }
                    if (!lastCheck) {
                        return copy;
                    }
                    if (copy.getS().size() == maxSLen + 1) return copy;
                    if (solved.get()) return null;
                    Map<ResetLogicTimeWord, Integer> model1 = null;
                    try {
                        model1 = copy.solveLocation(true);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (model1!=null && solved.compareAndSet(false, true)) {
                        return copy;
                    }
                    return null;
                }).filter(Objects::nonNull).collect(Collectors.partitioningBy(table -> table.getS().size() == maxSLen + 1));
                tmp.addAll(collect.get(false));
                if (cacheNext) {
                    cacheTables.addAll(collect.get(true));
                }
            }
            result = tmp;
            if (cacheNext && cacheTables.size() != 0) {
                searchTreeCache.setCache(i, cacheTables);
            }
        }
        List<GuessTableBuilder> lastCache = searchTreeCache.getCache(sortedPrefixes.size() - 1);
        if (lastCache != null) {
            result.addAll(lastCache);
        }
        searchTreeCache.done();
        LogUtil.info("guessTable size: %d\n", result.size());
        for (GuessTableBuilder tableBuilder : result) {
            DOTA dota = tableBuilder.buildHypothesis(name);
            if (dota!=null) {
                return dota;
            }
        }
        return null;
    }


    public List<GuessTable> getGuessTablesInSearchTree(Set<DelayTimeWord> keyResetPrefix, int maxSLen, boolean cacheNext) {
        List<Prefix<DelayTimeWord>> sortedPrefixes = getAllPrefixesIgnoreSROrderByWeight(keyResetPrefix);
        List<GuessTableBuilder> result = new ArrayList<>();
        Integer beginIdx = searchTreeCache.getFirstIdx();
        if (beginIdx == null) {
            beginIdx = 0;
            GuessTableBuilder init = new GuessTableBuilder(new HashSet<>(), new HashSet<>(), new GuessTableRecorder(new HashMap<>()), sigma);
            init.initBuilder(sortedPrefixes, this, normalizer);
            init.appendPrefix(0, true);
            result.add(init);
        }
        int guessPrefixCount = 0;
        List<boolean[]> guessBooleans = new ArrayList<>(sortedPrefixes.size());
        Set<Prefix<DelayTimeWord>> needGuessPrefixes = new HashSet<>();
        for (Prefix<DelayTimeWord> prefix : sortedPrefixes) {
            boolean[] guessBoolean;
            boolean fixedGuess;
            if (!Config.aggressiveSearch || keyResetPrefix.contains(prefix.getTimedWord())) {
                guessBoolean = TRUE_FALSE;
                fixedGuess = true;
            } else {
                // 激进搜索时的非关键前缀不猜测
                guessBoolean = FALSE;
                fixedGuess = false;
            }
            if (prefix.getAnswer().isSink()) {
                guessBoolean = TRUE;
                fixedGuess = true;
            }
            if (resetDeterminer != null) {
                Boolean reset = resetDeterminer.getReset().get(prefix.getTimedWord());
                if (reset != null) {
                    fixedGuess = true;
                    if (reset) {
                        guessBoolean = TRUE;
                    } else {
                        guessBoolean = FALSE;
                    }
                }
            }
            if (guessBoolean == TRUE_FALSE) {
                guessPrefixCount++;
            }
            if (!fixedGuess || guessBoolean == TRUE_FALSE) {
                needGuessPrefixes.add(prefix);
            }
            guessBooleans.add(guessBoolean);
        }

        LogUtil.info("即将构建猜测观察表，最大数量约为2^%d ~ 2^%d", guessPrefixCount, needGuessPrefixes.size());

        for (int i = beginIdx + 1; i < sortedPrefixes.size(); i++) {
            List<GuessTableBuilder> cachedResult = searchTreeCache.getCache(i - 1);
            if (cachedResult != null) {
                result.addAll(cachedResult);
            }
            List<GuessTableBuilder> tmp = new ArrayList<>(result.size());
            List<GuessTableBuilder> cacheTables = new ArrayList<>();
            boolean[] guessBoolean = guessBooleans.get(i);
            boolean fixedGuess = guessBoolean == TRUE_FALSE || !needGuessPrefixes.contains(sortedPrefixes.get(i));

            for (boolean guess : guessBoolean) {
                int finalI = i;
                Map<Boolean, List<GuessTableBuilder>> collect = result.parallelStream().map(table -> {
                    GuessTableBuilder copy = table.deepClone();
                    boolean success = copy.appendPrefix(finalI, guess);
                    if (!success && !fixedGuess) {
                        success = table.deepClone().appendPrefix(finalI, !guess);
                    }
                    if (!success || copy.getS().size() > maxSLen + 1) {
                        return null;
                    }
                    return copy;
                }).filter(Objects::nonNull).collect(Collectors.partitioningBy(table -> table.getS().size() == maxSLen + 1));
                tmp.addAll(collect.get(false));
                if (cacheNext) {
                    cacheTables.addAll(collect.get(true));
                }
            }
            result = tmp;
            if (cacheNext && cacheTables.size() != 0) {
                searchTreeCache.setCache(i, cacheTables);
            }
        }
        List<GuessTableBuilder> lastCache = searchTreeCache.getCache(sortedPrefixes.size() - 1);
        if (lastCache != null) {
            result.addAll(lastCache);
        }
        searchTreeCache.done();
        return result.stream().sorted(Comparator.comparingInt(a -> a.getS().size())).collect(Collectors.toList());
    }

    private static List<List<Boolean>> getPossibleReset(int number) {
        List<List<Boolean>> result = new ArrayList<>();
        result.add(new ArrayList<>());
        for (int i = 0; i < number; i++) {
            List<List<Boolean>> tmp = new ArrayList<>();
            for (List<Boolean> curResult : result) {
                for (boolean reset : TRUE_FALSE) {
                    List<Boolean> copy = new ArrayList<>(curResult);
                    copy.add(reset);
                    tmp.add(copy);
                }
            }
            result = tmp;
        }
        return result;
    }

    public List<GuessTable> getGuessTablesInLines(Set<DelayTimeWord> keyResetPrefix) {
        // 需要猜测重置信息的情况
        List<List<Boolean>> possibleReset = getPossibleReset(keyResetPrefix.size());
        List<DelayTimeWord> prefixesList = new ArrayList<>(keyResetPrefix);
        return possibleReset.parallelStream().map(resetBoolean -> {
            Set<DelayTimeWord> resetDTW = new HashSet<>();
            for (int i = 0; i < resetBoolean.size(); i++) {
                if (resetBoolean.get(i)) {
                    resetDTW.add(prefixesList.get(i));
                }
            }
            return getGuessTable(resetDTW);
        }).filter(Objects::nonNull).sorted(Comparator.comparingInt(a -> a.getS().size())).collect(Collectors.toList());
    }

    public GuessTable getGuessTable(Set<DelayTimeWord> resetDTW) {
        return getGuessTable2(resetDTW, getAllPrefixesIgnoreSROrderByWeight(resetDTW));
    }

    private void initSearchCache() {
        if (searchTreeCache == null) {
            searchTreeCache = new SearchTreeCache();
        } else {
            searchTreeCache.refresh();
        }
    }

    /**
     * 获得a和b所有最后重置位置的组合
     */
    private static int[][] getLastResetIdxPairs(DelayTimeWord a, DelayTimeWord b) {
        List<int[]> pairs = new ArrayList<>();
        int aSize = a.size();
        int bSize = b.size();
        List<DelayTimedAction> aActionList = a.getTimedActions();
        List<DelayTimedAction> bActionList = b.getTimedActions();
        int shortSize = Math.min(aSize, bSize);
        int maxSize = Math.max(aSize, bSize);
        int sameLen = 0;
        for (; sameLen < shortSize && aActionList.get(sameLen).equals(bActionList.get(sameLen)); sameLen++) ;
        for (int i = 0; i < sameLen; i++) {
            pairs.add(new int[]{i, i});
            for (int j = 1 + sameLen; j <= maxSize; j++) {
                if (j <= aSize) {
                    pairs.add(new int[]{j, i});
                }

                if (j <= bSize) {
                    pairs.add(new int[]{i, j});
                }
            }
        }

        for (int i = sameLen; i <= aSize; i++) {
            for (int j = sameLen; j <= bSize; j++) {
                pairs.add(new int[]{i, j});
            }
        }

        return pairs.toArray(new int[][]{});
    }

    private Prefix<DelayTimeWord> getPrefix(DelayTimeWord DTW) {
        int hash = DTW.hashCode();
        Prefix<DelayTimeWord> result = S.get(hash);
        if (result == null) {
            result = R.get(hash);
        }
        return result;
    }

    public Set<DelayTimeWord> getKeyResetPrefixes() {
        List<Element> elements = tableRecorder.allElements;
        Set<DelayTimeWord> result = new HashSet<>();
        for (Element element : elements) {
            AcceptPredicateBuilder predicateBuilder = element.getPredicateBuilder();
            if (predicateBuilder == null) {
                continue;
            }
            result.addAll(predicateBuilder.getKeyResetDTW());
        }
        return result;
    }

    private BooleanAnswer testSuffix(DelayTimeWord prefix, DelayTimeWord suffix, double shift) {
        if (suffix.isEmpty()) {
            return teacher.membership(prefix);
        }
        DelayTimedAction suffixFirstAction = suffix.getTimedActions().get(0);
        DelayTimedAction firstAction = new DelayTimedAction(suffixFirstAction.getSymbol(), suffixFirstAction.getValue() + shift);
        List<DelayTimedAction> timeWords = new ArrayList<>(prefix.getTimedActions());
        timeWords.add(firstAction);
        timeWords.addAll(suffix.getTimedActions().subList(1, suffix.getTimedActions().size()));
        return teacher.membership(new DelayTimeWord(timeWords));
    }

    /**
     * 在给定的重置条件下搜索是否有可以区分前缀的后缀
     */
    private DelayTimeWord getDistinguishedSuffix(Prefix<DelayTimeWord> i, Prefix<DelayTimeWord> j, Collection<DelayTimeWord> suffixes, int resetAti, int resetAtj) {
        if (!i.getAnswer().equals(j.getAnswer())) {
            return DelayTimeWord.emptyWord();
        }
        DelayTimeWord iWord = i.getTimedWord();
        DelayTimeWord jWord = j.getTimedWord();
        double iTime = TimeWordHelper.getDTWTime(iWord, resetAti);
        double jTime = TimeWordHelper.getDTWTime(jWord, resetAtj);
        double shiftI = 0;
        double shiftJ = 0;
        if (iTime > jTime) {
            shiftJ = iTime - jTime;
        } else if (iTime < jTime) {
            shiftI = jTime - iTime;
        }
        for (DelayTimeWord suffix : suffixes) {
            BooleanAnswer iAnswer = testSuffix(iWord, suffix, shiftI);
            BooleanAnswer jAnswer = testSuffix(jWord, suffix, shiftJ);
            if (!iAnswer.equals(jAnswer)) {
                return suffix;
            }
        }
        return null;
    }

    /**
     * 填充观察表信息，
     */
    private void fillTable(Collection<Prefix<DelayTimeWord>> newPrefixes, Collection<DelayTimeWord> suffixes) {
        List<Prefix<DelayTimeWord>> currentPrefixes = getAllPrefixes();
        for (Prefix<DelayTimeWord> r : currentPrefixes) {
            for (Prefix<DelayTimeWord> newPrefix : newPrefixes) {
                Element element = tableRecorder.getElement(newPrefix, r);
                if (Element.canBeDistinguished(element)) {
                    // 已经被其他后缀区分，不需要再比较
                    continue;
                }

                if (r.equals(newPrefix)) {
                    // 同一个timeWord不需要判断直接false
                    tableRecorder.updateAndSaveElementByConstant(newPrefix, r, false);
                    continue;
                }
                if (!r.getAnswer().equals(newPrefix.getAnswer())) {
                    // answer不能的timed word直接可区分
                    tableRecorder.updateAndSaveElementByConstant(newPrefix, r, true);
                    continue;
                }
                int[][] pairs = getLastResetIdxPairs(newPrefix.getTimedWord(), r.getTimedWord());
                int distinguishedCounter = 0;
                for (int[] pair : pairs) {
                    int newPrefixResetIdx = pair[0];
                    int rPrefixResetIdx = pair[1];
                    DelayTimeWord keySuffix = getDistinguishedSuffix(newPrefix, r, suffixes, newPrefixResetIdx, rPrefixResetIdx);
                    if (keySuffix != null) {
                        // 在当前pair的重置情况下可以被区分，更新predicate
                        distinguishedCounter++;
                        ResetPredicate predicate = generateResetPredicate(newPrefix, r, newPrefixResetIdx, rPrefixResetIdx);
                        element = tableRecorder.updateElement(element, predicate, null);
                    } else {
                        // 重置情况1. 在当前pair的重置情况下无法被区分
                        element = tableRecorder.updateElement(element, null, false);
                    }
                    element.setMaxResetConditionNumber(pairs.length);
                }
                if (distinguishedCounter == pairs.length) {
                    // 2. 在所有重置情况下都可以被某一个后缀区分
                    // 需要更新keyResetPrefix，删除一部分 predicate使用的关键字母表
                    element = tableRecorder.updateElement(element, null, true);
                }
                tableRecorder.saveElement(newPrefix, r, element);
            }
        }
    }

    private static ResetPredicate generateResetPredicate(Prefix<DelayTimeWord> i, Prefix<DelayTimeWord> j, int resetAti, int resetAtj) {
        Set<DelayTimeWord>[] iMustResetAndMustNotReset = i.getMustResetAndMustNotResetSet(resetAti);
        Set<DelayTimeWord>[] jMustResetAndMustNotReset = j.getMustResetAndMustNotResetSet(resetAtj);
        iMustResetAndMustNotReset[0].addAll(jMustResetAndMustNotReset[0]);
        iMustResetAndMustNotReset[1].addAll(jMustResetAndMustNotReset[1]);
        Set<DelayTimeWord> mustResetPrefixes = iMustResetAndMustNotReset[0];
        Set<DelayTimeWord> mustNotResetPrefixes = iMustResetAndMustNotReset[1];
        return new ResetPredicate(mustResetPrefixes, mustNotResetPrefixes);
    }

    /**
     * 新增的前缀or后缀不会使已经检查过满足一致性的两行后继timeWord重新不满足一致性，所以不需要比较
     */
    private DelayTimeWord isConsistent(List<Prefix<DelayTimeWord>> prefixes, IntegerEntity intVal) {
        List<Prefix<DelayTimeWord>> existPrefixes = getAllPrefixes();
        for (int i = intVal.getVal(); i < prefixes.size(); i = intVal.increase()) {
            Prefix<DelayTimeWord> newPrefix = prefixes.get(i);
            if (newPrefix.getTimedWord().size() == 0) {
                // 前缀检查1：empty的前缀不可能是后继前缀
                continue;
            }
            for (Prefix<DelayTimeWord> r : existPrefixes) {
                if (r.getTimedWord().size() == 0 || r.equals(newPrefix)) {
                    // 前缀检查1：empty的前缀不可能是后继前缀, 相同的前缀不需要比较
                    continue;
                }
                DelayTimedAction newPrefixLastAction = newPrefix.getTimedWord().getLastAction();
                DelayTimedAction rLastAction = r.getTimedWord().getLastAction();
                if (!rLastAction.getSymbol().equals(newPrefixLastAction.getSymbol())) {
                    // 前缀检查2：动作不同的后继前缀不需要判断一致性
                    continue;
                }

                DelayTimeWord beforeNewDTW = newPrefix.getTimedWord().subWord(0, newPrefix.getTimedWord().size() - 1);
                DelayTimeWord beforeRDTW = r.getTimedWord().subWord(0, r.getTimedWord().size() - 1);

                Prefix<DelayTimeWord> beforeNewPrefix = getPrefix(beforeNewDTW);
                Prefix<DelayTimeWord> beforeRPrefix = getPrefix(beforeRDTW);
                if (beforeRPrefix == null || beforeNewPrefix == null) {
                    throw new RuntimeException("not prefix closure");
                }
                if (!beforeRPrefix.getAnswer().equals(beforeNewPrefix.getAnswer()) || beforeRPrefix.equals(beforeNewPrefix)) {
                    // 前缀检查3：接收情况不同的前缀不需要判断一致性，同一个前缀不需要判断一致性
                    continue;
                }

                // 前置检查完毕，开始进行consistent check
                int[][] resets = getLastResetIdxPairs(beforeNewDTW, beforeRDTW);
                for (int[] reset : resets) {
                    int beforeNewDTWResetIdx = reset[0];
                    int beforeRDTWResetIdx = reset[1];
                    Set<DelayTimeWord> resetPrefixes = new HashSet<>();
                    if (beforeNewDTWResetIdx > 0) {
                        resetPrefixes.add(beforeNewPrefix.getMustResetWord(beforeNewDTWResetIdx));
                    }
                    if (beforeRDTWResetIdx > 0) {
                        resetPrefixes.add(beforeRPrefix.getMustResetWord(beforeRDTWResetIdx));
                    }
                    if (tableRecorder.getDistinguishResult(beforeNewPrefix, beforeRPrefix, resetPrefixes)) {
                        // 前缀可以区分，不需要比较一致性
                        continue;
                    }
                    double timeVal1 = TimeWordHelper.getDTWTime(beforeNewDTW, beforeNewDTWResetIdx);
                    double timeVal2 = TimeWordHelper.getDTWTime(beforeRDTW, beforeRDTWResetIdx);
                    if (!DOTAUtil.isSameRegion(timeVal1 + newPrefixLastAction.getValue(), timeVal2 + rLastAction.getValue())) {
                        continue;
                    }

                    for (boolean lastActionReset : TRUE_FALSE) {
                        int newDTWResetIdx = beforeNewDTWResetIdx;
                        int rResetIdx = beforeRDTWResetIdx;
                        if (lastActionReset) {
                            newDTWResetIdx = beforeNewDTW.size() + 1;
                            rResetIdx = beforeRDTW.size() + 1;
                        }
                        DelayTimeWord suffix = getDistinguishedSuffix(newPrefix, r, this.E, newDTWResetIdx, rResetIdx);
                        if (suffix == null) {
                            // 满足一致性
                            continue;
                        }

                        // 不满足一致性, 说明在当前重置下，前缀是可以直接区分的
                        ResetPredicate predicate = generateResetPredicate(beforeNewPrefix, beforeRPrefix, beforeNewDTWResetIdx, beforeRDTWResetIdx);
                        tableRecorder.updateAndSaveElementByPredicate(beforeNewPrefix, beforeRPrefix, predicate);

                        List<DelayTimedAction> suffixDTAs = new ArrayList<>(1 + suffix.size());
                        suffixDTAs.add(new DelayTimedAction(newPrefixLastAction.getSymbol(), Math.min(newPrefixLastAction.getValue(), rLastAction.getValue())));
                        suffixDTAs.addAll(suffix.getTimedActions());
                        DelayTimeWord suffixDTW = new DelayTimeWord(suffixDTAs);
                        if (E.contains(suffixDTW)) {
                            LogUtil.error("%s已经在E区了，重复添加", suffixDTW);
                        }
                        return suffixDTW;
                    }
                }


            }
        }
        return null;
    }

    // 1. 向E区新添加一个后缀，
    // 2. 基于这个后缀重新更新
    private void makeConsistent(DelayTimeWord suffix) {
        if (E.contains(suffix)) {
            LogUtil.error("%s已经在E区了，重复添加", suffix);
            return;
//            throw new RuntimeException(suffixDTW + "已经在E区了，重复添加!!!!");
        }
        E.add(suffix);
        fillTable(getAllPrefixes(), Collections.singleton(suffix));
    }

    private void makeClosed() {
        List<Prefix<DelayTimeWord>> tobeMovedPrefixes = new ArrayList<>();
        Collection<Prefix<DelayTimeWord>> s = S.values();
        Collection<Prefix<DelayTimeWord>> r = R.values();
        // check closed
        checkNextPrefix:
        for (Prefix<DelayTimeWord> rPrefix : r) {
            for (Prefix<DelayTimeWord> sPrefix : s) {
                if (!tableRecorder.isDistinguished(sPrefix, rPrefix)) {
                    continue checkNextPrefix;
                }
            }
            for (Prefix<DelayTimeWord> newSPrefix : tobeMovedPrefixes) {
                if (!tableRecorder.isDistinguished(newSPrefix, rPrefix)) {
                    continue checkNextPrefix;
                }
            }

            // S区所有前缀都和当前前缀区分，则这个前缀需要加到S区
            tobeMovedPrefixes.add(rPrefix);
        }

        // make closed
        for (Prefix<DelayTimeWord> p : tobeMovedPrefixes) {
            int hash = p.hashCode();
            R.remove(hash);
            S.put(hash, p);
        }
    }

    private List<Prefix<DelayTimeWord>> getAllPrefixes() {
        List<Prefix<DelayTimeWord>> prefixes = new ArrayList<>(S.values());
        prefixes.addAll(R.values());
        return prefixes;
    }

    private Set<DelayTimeWord> getAllPrefixSet() {
        Set<DelayTimeWord> prefixes = new HashSet<>(S.values().size()+R.values().size());
        prefixes.addAll(S.values().stream().map(Prefix::getTimedWord).collect(Collectors.toSet()));
        prefixes.addAll(S.values().stream().map(Prefix::getTimedWord).collect(Collectors.toSet()));
        return prefixes;
    }

    private List<Prefix<DelayTimeWord>> getAllSortedPrefixesIgnoreSR() {
        List<Prefix<DelayTimeWord>> prefixes = new ArrayList<>(S.size() + R.size());
        prefixes.addAll(S.values());
        prefixes.addAll(R.values());
        prefixes.sort(Comparator.comparingInt(prefix -> prefix.getTimedWord().size()));
        return prefixes;
    }

    private List<Prefix<DelayTimeWord>> getAllPrefixesIgnoreSROrderByWeight(Set<DelayTimeWord> keyResetPrefix) {
        List<Prefix<DelayTimeWord>> prefixes = new ArrayList<>(S.size() + R.size());
        prefixes.addAll(S.values());
        prefixes.addAll(R.values());
        prefixes.sort(Comparator.comparingInt(prefix -> getPrefixWeight(prefix, keyResetPrefix)));
        return prefixes;
    }

    public GuessTable getGuessTable2(Set<DelayTimeWord> resetPrefixes, List<Prefix<DelayTimeWord>> allPrefixes) {
        GuessTableBuilder table = new GuessTableBuilder(new HashSet<>(), new HashSet<>(), new GuessTableRecorder(new HashMap<>()), sigma);
        table.initBuilder(allPrefixes, this, normalizer);
        for (int i = 0; i < allPrefixes.size(); i++) {
            DelayTimeWord word = allPrefixes.get(i).getTimedWord();
            boolean reset = resetPrefixes.contains(word);
            boolean fixReset = reset;
            if (resetDeterminer != null) {
                Boolean determineReset = resetDeterminer.getReset().get(word);
                if (determineReset != null) {
                    fixReset = true;
                    reset = determineReset;
                }
            }
            GuessTableBuilder copy = table.deepClone();
            boolean success = copy.appendPrefix(i, reset);
            if (!success && !fixReset) {
                if (!table.deepClone().appendPrefix(i, true)) return null;
            }
            table = copy;
        }
        return table;
    }


    public class DelayTableRecorder {
        private final Map<Prefix<DelayTimeWord>, Map<Prefix<DelayTimeWord>, Element>> recorder = new HashMap<>();
        private final List<Element> allElements = new ArrayList<>();

        public DelayTableRecorder() {
        }

        private Element getElement(Prefix<DelayTimeWord> a, Prefix<DelayTimeWord> b) {
            Map<Prefix<DelayTimeWord>, Element> second = recorder.get(a);
            if (second == null) {
                return null;
            }
            return second.get(b);
        }

        /**
         * 新增后缀后更新观察表内容
         * 该方法适用于新后缀在所有可能的重置条件下都可以/不能区分a和b
         */
        public void updateAndSaveElementByConstant(Prefix<DelayTimeWord> a, Prefix<DelayTimeWord> b, boolean distinguished) {
            updateElement(a, b, null, distinguished);
        }

        /**
         * 新增后缀后更新观察表内容
         * 该方法适用于新后缀在所有可能的重置条件下都可以/不能区分a和b
         */
        public void updateAndSaveElementByPredicate(Prefix<DelayTimeWord> a, Prefix<DelayTimeWord> b, ResetPredicate predicate) {
            updateElement(a, b, predicate, null);
        }

        /**
         * 新增后缀后更新观察表内容
         * 该方法适用于新后缀是否可以区分a和b取决于 重置信息的猜测
         */
        public Element updateElement(Element element, ResetPredicate predicate, Boolean distinguished) {
            if (element == null) {
                AcceptPredicateBuilder predicateBuilder = null;
                if (predicate != null) {
                    predicateBuilder = new AcceptPredicateBuilder().appendPredicate(predicate);
                }
                element = new Element(predicateBuilder, distinguished);
                allElements.add(element);
                return element;
            }
            element.update(predicate, distinguished);
            return element;
        }

        public void saveElement(Prefix<DelayTimeWord> a, Prefix<DelayTimeWord> b, Element element) {
            Map<Prefix<DelayTimeWord>, Element> second = recorder.getOrDefault(a, new HashMap<>());
            second.put(b, element);
            recorder.put(a, second);
            second = recorder.getOrDefault(b, new HashMap<>());
            second.put(a, element);
            recorder.put(b, second);
        }

        /**
         * 给定一种重置情况返回对应的recorder
         */
//        public GuessTable getGuessTable(Set<DelayTimeWord> resetPrefixes) {
//            List<Prefix<DelayTimeWord>> DTPrefixes = getAllPrefixes();
//            List<Prefix<ResetLogicTimeWord>> RLTPrefixes = DTPrefixes.stream()
//                    .map(DTPrefix -> new Prefix<>(TimeWordHelper.DTW2RLTW(DTPrefix.getTimedWord(), resetPrefixes), DTPrefix.getAnswer()))
//                    .collect(Collectors.toList());
//
//            Set<Prefix<ResetLogicTimeWord>> guessS = new HashSet<>();
//            Set<Prefix<ResetLogicTimeWord>> guessR = new HashSet<>();
//            Map<ResetLogicTimeWord, Map<ResetLogicTimeWord, Boolean>> guessRecorder = new HashMap<>(recorder.size());
//
//            for (int i = 0; i < RLTPrefixes.size(); i++) {
//                Prefix<DelayTimeWord> iDTPrefix = DTPrefixes.get(i);
//                Prefix<ResetLogicTimeWord> iRLTPrefix = RLTPrefixes.get(i);
//                Prefix<ResetLogicTimeWord> iNormalizedPrefix = new Prefix<>(normalizer.normalizeRLT(iRLTPrefix.getTimedWord()), iRLTPrefix.getAnswer());
//                Map<ResetLogicTimeWord, Boolean> i2Map = guessRecorder.getOrDefault(iNormalizedPrefix.getTimedWord(), new HashMap<>(recorder.size()));
//
//                for (int j = 0; j < RLTPrefixes.size(); j++) {
//                    Prefix<DelayTimeWord> jDTPrefix = DTPrefixes.get(j);
//                    Prefix<ResetLogicTimeWord> jRLTPrefix = RLTPrefixes.get(j);
//                    Prefix<ResetLogicTimeWord> jNormalizedPrefix = new Prefix<>(normalizer.normalizeRLT(jRLTPrefix.getTimedWord()), jRLTPrefix.getAnswer());
//                    if (i == j) continue;
//
//                    // 1. 前置检查，存在两个region相同的logic timed word 接收情况不同，说明当前猜测观察表的重置猜测是错误的
//                    if (ResetLogicTimeWord.isLogicTimeEq(iNormalizedPrefix.getTimedWord(), jNormalizedPrefix.getTimedWord())) {
//                        if (!ResetLogicTimeWord.isResetEq(iNormalizedPrefix.getTimedWord(), jNormalizedPrefix.getTimedWord())) {
//                            // 1.1 重置检查：如果存在两个tw 正则化后属于同一region，但是重置信息不同，则说明当前猜测是错误的
//                            return null;
//                        }
//                        if (getDistinguishResult(iDTPrefix, jDTPrefix, resetPrefixes)) {
//                            // 1.2 正则化检查: 如果存在正则化后属于同一region却可以区分的两个timedWord, 则说明当前猜测是错误的
//                            return null;
//                        }
//                    }
//
//                    // 2. 重复set检查
//                    Map<ResetLogicTimeWord, Boolean> j2Map = guessRecorder.getOrDefault(jNormalizedPrefix.getTimedWord(), new HashMap<>(recorder.size()));
//                    if (i2Map.containsKey(jNormalizedPrefix.getTimedWord()) || j2Map.containsKey(iNormalizedPrefix.getTimedWord())) {
//                        continue;
//                    }
//
//                    // 3. set
//                    boolean result = getDistinguishResult(iDTPrefix, jDTPrefix, resetPrefixes);
//                    i2Map.put(jNormalizedPrefix.getTimedWord(), result);
//                    j2Map.put(iNormalizedPrefix.getTimedWord(), result);
//                    guessRecorder.put(iNormalizedPrefix.getTimedWord(), i2Map);
//                    guessRecorder.put(jNormalizedPrefix.getTimedWord(), j2Map);
//                }
//                if (S.containsKey(iDTPrefix.hashCode())) {
//                    guessS.add(iNormalizedPrefix);
//                } else {
//                    guessR.add(iNormalizedPrefix);
//                }
//            }
//
//            GuessTable table = new GuessTable(guessS, guessR, new GuessTableRecorder(guessRecorder), sigma);
//            table.makeClosed();
//            return table;
//        }

        /**
         * 判断两个前缀是在当前所有后缀下都 可以区分/不可以区分
         * 可以区分 返回true
         * 不能区分/区分情况依靠部分重置信息 返回false
         */
        public boolean isDistinguished(Prefix<DelayTimeWord> a, Prefix<DelayTimeWord> b) {
            return Element.canBeDistinguished(getElement(a, b));
        }

        /**
         * 判断两个前缀是否在当前所有后缀下，在给定的重置信息resetPrefixes下 可以区分/不可以区分
         * 可以区分 返回true
         * 不能 返回false
         */
        public boolean getDistinguishResult(Prefix<DelayTimeWord> a, Prefix<DelayTimeWord> b, Set<DelayTimeWord> resetPrefixes) {
            Element element = getElement(a, b);
            if (element == null)
                throw new RuntimeException("given prefix " + a + " and " + b + "are not in tableRecorder");
            return element.getResult(resetPrefixes);
        }

        /**
         *
         */
        private void updateElement(Prefix<DelayTimeWord> a, Prefix<DelayTimeWord> b, ResetPredicate predicate, Boolean distinguished) {
            Element findElement = getElement(a, b);
            Element setElement = this.updateElement(findElement, predicate, distinguished);
            saveElement(a, b, setElement);
        }
    }

    @Data
    private static class Element {

        //
        int maxResetConditionNumber = -1;
        AcceptPredicateBuilder predicateBuilder;
        Boolean distinguished;

        public Element(AcceptPredicateBuilder predicateBuilder, Boolean distinguished) {
            this.predicateBuilder = predicateBuilder;
            this.distinguished = distinguished;
        }

        public static boolean canBeDistinguished(Element element) {
            return element != null && element.getPredicateBuilder() == null && element.getDistinguished();
        }

        // 新增后缀时需要对已有的信息进行更新
        // 要么通过 predicate更新，要么通过 distinguished更新，两个入参有且只有一个是 null
        // 如果 this.distinguished 是true表示基于原有的后缀已经可以区分，不需要再更新
        //
        public void update(ResetPredicate resetPredicate, Boolean distinguished) {
            // 已经可以区分，不需要更新
            if (this.distinguished != null && this.distinguished) return;

            // distinguished为true表示可以区分，false表示当前后缀对于区分当前element无效
            if (distinguished != null) {
                if (distinguished) {
                    this.distinguished = true;
                    this.predicateBuilder = null;
                }
                return;
            }

            // 取决于重置信息
            this.distinguished = null;
            if (this.predicateBuilder == null) {
                this.predicateBuilder = new AcceptPredicateBuilder();
            }
            this.predicateBuilder = this.predicateBuilder.appendPredicate(resetPredicate);
            if (maxResetConditionNumber == this.predicateBuilder.getPredicateSize()) {
                // 所有重置情况下都可以区分，直接更新为 true
                this.update(null, true);
            }
        }

        public boolean getResult(Set<DelayTimeWord> resetPrefix) {
            if (resetPrefix == null) {
                resetPrefix = new HashSet<>(0);
            }
            if (this.distinguished != null && this.distinguished) return true;
            if (predicateBuilder != null) {
                return predicateBuilder.predicate(resetPrefix);
            }
            return false;
        }
    }
}
