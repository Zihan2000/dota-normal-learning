package modelLearning.normal.learner.logicTable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import modelLearning.frame.Learner;
import modelLearning.normal.teacher.BooleanAnswer;
import modelLearning.normal.teacher.NormalTeacher;
import modelLearning.util.LogUtil;
import modelLearning.util.TimeWordHelper;
import ta.Clock;
import ta.TaLocation;
import ta.TaTransition;
import ta.TimeGuard;
import ta.ota.*;
import timedAction.DelayTimedAction;
import timedAction.ResetDelayAction;
import timedWord.DelayTimeWord;
import timedWord.ResetDelayTimeWord;

import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;

public class ObservationTable {
    //    private static final boolean[] TRUE_FALSE_GROUP = {true, false};
    private static final AtomicInteger idGenerator = new AtomicInteger(0);
    @Getter
    private int id;
    @Getter
    private String reason;
    @Getter
    private int parentId;
    private static final boolean[] TRUE_FALSE_GROUP = {true, false};
    private static final boolean[] TRUE_GROUP = {true};

    private static final boolean[] FALSE_GROUP = {false};

    private final NormalTeacher teacher;

    protected Set<ResetLogicTimeWord> S;
    protected Set<ResetLogicTimeWord> R;
    protected List<LogicTimeWord> E;
    protected Set<String> sigma;
    protected Map<ResetLogicTimeWord, Map<LogicTimeWord, Pair>> tableElement;
    private DOTA hypothesis;
    private Map<ResetLogicTimeWord, ResetLogicTimeWord> E2SRows;

    /**
     * 记录当前观察表的父级完整观察表
     */
    private Recorder recorder;

    private ObservationTable() {
        teacher = null;
    }


    public ObservationTable(ObservationTable table, String reason, int parentId) {
        if (parentId != -1) {
            id = idGenerator.incrementAndGet();
            this.reason = reason;
            this.parentId = parentId;
        } else {
            this.reason = "template table";
            this.id = table.id;
            this.parentId = table.parentId;
        }

        this.S = new HashSet<>(table.S);
        this.R = new HashSet<>(table.R);
        this.E = new ArrayList<>(table.E);
        this.sigma = table.sigma;
        this.tableElement = new HashMap<>();
        for (ResetLogicTimeWord key : table.tableElement.keySet()) {
            tableElement.put(key, new HashMap<>(table.tableElement.get(key)));
        }
        this.teacher = table.teacher;
        this.recorder = table.recorder;
    }

    private ObservationTable(Set<String> sigma, NormalTeacher teacher) {
        id = idGenerator.incrementAndGet();
        reason = "root";
        parentId = -1;
        S = new HashSet<>();
        R = new HashSet<>();
        E = new ArrayList<>();
        S.add(ResetLogicTimeWord.emptyWord());
        E.add(LogicTimeWord.emptyWord());
        this.sigma = sigma;
        this.teacher = teacher;
        this.tableElement = new HashMap<>();
        Pair initial = new Pair(S.iterator().next(), E.get(0), null);
        initial.setAnswer(teacher.membership(initial.getDTW()));
        Map<LogicTimeWord, Pair> suffixesMap = new HashMap<>();
        suffixesMap.put(E.get(0), initial);
        tableElement.put(S.iterator().next(), suffixesMap);
    }

    /**
     * 根据字母表初始化观察表, 返回所有可能的初始化观察表
     */
    public static List<ObservationTable> init(NormalTeacher teacher, Set<String> sigma) {

        ObservationTable origin = new ObservationTable(sigma, teacher);
        List<ObservationTable> result = new ArrayList<>();
        // obtain all possible prefix conditions
        List<List<ResetLogicTimeWord>> prefixConditions = appendInitialSigmaForPrefix(null, teacher, sigma);

        for (List<ResetLogicTimeWord> prefixCondition : prefixConditions) {
            result.addAll(addNewPrefixes(origin, prefixCondition, "init"));
        }
        if (result.size() == 0) {
            result.add(origin);
        }
        return result;
    }

    /**
     * 为一个前缀添加一个初始action，并猜测重置信息，返回所有可能的结果
     */
    private static List<List<ResetLogicTimeWord>> appendInitialSigmaForPrefix(ResetLogicTimeWord resetLogicTimeWord, NormalTeacher teacher, Set<String> sigma) {
        List<List<ResetLogicTimeWord>> prefixConditions = new ArrayList<>();
        prefixConditions.add(new ArrayList<>());
        if (resetLogicTimeWord == null) {
            resetLogicTimeWord = ResetLogicTimeWord.emptyWord();
        } else {
            // 不能接任何初始后缀
            if (!TimeWordHelper.isValidWord(resetLogicTimeWord) || (resetLogicTimeWord.getLastResetAction().getValue() > 0 && !resetLogicTimeWord.getLastResetAction().isReset())) {
                return prefixConditions;
            }
        }

        for (String symbol : sigma) {
            List<List<ResetLogicTimeWord>> tmp = new ArrayList<>();
            ResetLogicTimeWord curTimeWordExample = resetLogicTimeWord.concat(new ResetLogicAction(symbol, 0d, true));
            boolean[] guessReset = teacher.membership(TimeWordHelper.RLTW2DTW(curTimeWordExample)).isSink() ? TRUE_GROUP : TRUE_FALSE_GROUP;
            for (List<ResetLogicTimeWord> curPrefixes : prefixConditions) {
                for (boolean reset : guessReset) {
                    List<ResetLogicTimeWord> toBeAdded = new ArrayList<>(curPrefixes);
                    ResetLogicTimeWord toBeAddedWord = resetLogicTimeWord.concat(new ResetLogicAction(symbol, 0d, reset));
                    toBeAdded.add(toBeAddedWord);
                    tmp.add(toBeAdded);
                }
            }
            prefixConditions = tmp;
        }
        return prefixConditions;
    }

    /**
     * 添加新的前缀, 并补充观察表
     */
    private static List<ObservationTable> addNewPrefixes(ObservationTable template, List<ResetLogicTimeWord> prefixes, String reason) {
        prefixes = prefixes.stream().filter(p -> !template.S.contains(p) && !template.R.contains(p)).collect(Collectors.toList());
        if (prefixes.size() == 0) {
            List<ObservationTable> result = new ArrayList<>();
            return result;
        }
        ObservationTable copy = new ObservationTable(template, reason, -1);
        copy.R.addAll(prefixes);
        return fillTable(copy, prefixes, template.E, reason);
    }

    /**
     * 添加新的后缀, 并补充观察表
     */
    private static List<ObservationTable> addNewSuffix(ObservationTable template, LogicTimeWord suffix, String reason) {
        ObservationTable copy = new ObservationTable(template, reason, -1);
        copy.E.add(suffix);
        List<ResetLogicTimeWord> prefixes = template.getPrefixList();
        return fillTable(copy, prefixes, Collections.singletonList(suffix), reason);
    }

    /**
     * 填充观察表指定前缀和后缀对应的位置
     */
    private static List<ObservationTable> fillTable(ObservationTable template, List<ResetLogicTimeWord> prefixes, List<LogicTimeWord> suffixes, String reason) {
        List<ObservationTable> result = new ArrayList<>();
        result.add(template);
        for (ResetLogicTimeWord prefix : prefixes) {
            List<ObservationTable> tmp = new ArrayList<>();
            DelayTimeWord dPrefix;
            try {
                dPrefix = TimeWordHelper.RLTW2DTW(prefix);
            } catch (RuntimeException e) {
                dPrefix = null;
            }
            boolean needGuess = dPrefix != null && !template.teacher.membership(dPrefix).isSink();
            List<List<List<Boolean>>> resetForETemplateArray = guessResetForRow(template, prefix, suffixes, needGuess);
            if (resetForETemplateArray.size() == 0) {
                LogUtil.warn("无任何可猜测的后缀, 无法生成猜测观察表");
                return tmp;
            }
            for (ObservationTable curResult : result) {
                for (List<List<Boolean>> resetForETemplate : resetForETemplateArray) {
                    ObservationTable copy = new ObservationTable(curResult, reason, template.id);
                    fillRow(copy, prefix, suffixes, resetForETemplate);
                    tmp.add(copy);
                }
            }
            result = tmp;
        }
        return result;
    }

    /**
     * 猜测对应于prefix的suffixes的重置信息
     * 返回的list维度分别是猜测, 后缀, 重置信息
     */
    private static List<List<List<Boolean>>> guessResetForRow(ObservationTable template, ResetLogicTimeWord prefix, List<LogicTimeWord> suffixes, boolean needGuess) {
        List<List<List<Boolean>>> result = new ArrayList<>();
        result.add(new ArrayList<>());
        for (LogicTimeWord suffix : suffixes) {
            // 1. 不需要重置信息的后缀
            if (suffix.size() <= 1) {
                List<Boolean> emptyTemplate = new ArrayList<>();
                for (List<List<Boolean>> curResult : result) {
                    curResult.add(emptyTemplate);
                }
                continue;
            }
            // 2. 全是reset的重置信息的后缀
            if (!needGuess ||
                    (prefix.size() > 0 && !prefix.getLastResetAction().isReset() && prefix.getLastResetAction().getValue() > suffix.get(0).getValue())) {
                List<Boolean> resetArrayTemplate = new ArrayList<>(suffix.size() - 1);
                for (int i = 0; i < suffix.size() - 1; i++) {
                    resetArrayTemplate.add(true);
                }
                for (List<List<Boolean>> curResult : result) {
                    curResult.add(resetArrayTemplate);
                }
                continue;
            }

            // 3. 需要猜测重置信息的后缀
            List<List<List<Boolean>>> tmp = new ArrayList<>();
            List<List<Boolean>> resetTemplateArray = guessResetForOneSuffix(template, prefix, suffix);

            // 过滤一些和观察表已有信息违背的重置信息
            resetTemplateArray = resetTemplateArray.parallelStream()
                    .filter(resetGuess -> checkGuessReset(template, transform2ResetLogicTimeWord(prefix, suffix, resetGuess)))
                    .collect(Collectors.toList());
            if (resetTemplateArray.size() == 0) {
                //存在一个后缀无任何可猜测重置信息，当前前后缀是一个不可能的观察表
                return tmp;
            }

            for (List<List<Boolean>> curResult : result) {
                for (List<Boolean> resetTemplate : resetTemplateArray) {
                    List<List<Boolean>> copy = new ArrayList<>(curResult);
                    copy.add(resetTemplate);
                    tmp.add(copy);
                }
            }
            result = tmp;
        }
        return result;
    }

    /**
     * 获得一个后缀的所有可能重置信息 (后缀所在的前缀一定要是有效的)
     */
    private static List<List<Boolean>> guessResetForOneSuffix(ObservationTable template, ResetLogicTimeWord prefix, LogicTimeWord suffix) {
        List<List<Boolean>> result = new ArrayList<>();
        result.add(new ArrayList<>());
//        for (boolean guess : initialGuessGroup) {
//            List<Boolean> list = new ArrayList<>();
//            list.add(guess);
//            result.add(list);
//        }
        for (int i = 0; i < suffix.size() - 1; i++) {
            List<List<Boolean>> tmp = new ArrayList<>();
            for (List<Boolean> curResult : result) {
                boolean[] true_false = TRUE_FALSE_GROUP;
                LogicTimedAction curAction = suffix.getTimedActions().get(i);
                LogicTimedAction nextAction = suffix.getTimedActions().get(i + 1);
                if (i > 0) {
                    // 1. 根据先前时间和当前时间确定可猜测的重置信息
                    LogicTimedAction beforeAction = suffix.getTimedActions().get(i - 1);
                    double beforeSuffixTime = beforeAction.getValue();
                    double curSuffixTime = curAction.getValue();
                    // 前一个不是重置, 且前一个时钟值比当前大
                    if (!curResult.get(curResult.size() - 1) && beforeSuffixTime > curSuffixTime) {
                        true_false = TRUE_GROUP;
                    }
                }

                // 2. 根据当前action的重置信息是否导致sink猜测重置信息
                ResetLogicTimeWord concated = TimeWordHelper.LTW2RLTW(suffix.subWord(0, i), curResult);
                ResetLogicTimeWord ltwR = prefix.concat(concated)
                        .concat(new ResetLogicAction(curAction.getSymbol(), curAction.getValue(), true))
                        .concat(new ResetLogicAction(nextAction.getSymbol(), nextAction.getValue(), true));
                ResetLogicTimeWord ltwN = prefix.concat(concated)
                        .concat(new ResetLogicAction(curAction.getSymbol(), curAction.getValue(), false))
                        .concat(new ResetLogicAction(nextAction.getSymbol(), nextAction.getValue(), true));
                boolean sinkR = true;
                boolean sinkN = true;
                if (TimeWordHelper.isValidWord(ltwR)) {
                    DelayTimeWord dtwR = TimeWordHelper.RLTW2DTW(ltwR);
                    sinkR = template.teacher.membership(dtwR).isSink();
                }

                if (TimeWordHelper.isValidWord(ltwN)) {
                    DelayTimeWord dtwN = TimeWordHelper.RLTW2DTW(ltwN);
                    sinkN = template.teacher.membership(dtwN).isSink();
                }
                if ((sinkR && sinkN) || (!sinkR && sinkN)) {
                    true_false = TRUE_GROUP;
                } else if (sinkR) {
                    true_false = FALSE_GROUP;
                }


                for (boolean reset : true_false) {
                    List<Boolean> copy = new ArrayList<>(curResult);
                    copy.add(reset);
                    tmp.add(copy);
                }
            }
            result = tmp;
        }
        return result;
    }

    /**
     * 为一个前缀填充指定的后缀重置信息
     */
    private static void fillRow(ObservationTable table, ResetLogicTimeWord prefix, List<LogicTimeWord> suffixes, List<List<Boolean>> resetForETemplate) {
        for (int i = 0; i < suffixes.size(); i++) {
            LogicTimeWord suffix = suffixes.get(i);
            List<Boolean> resetInform = resetForETemplate.get(i);
            fillResetForOneSuffix(table, prefix, suffix, resetInform);
        }
    }

    /**
     * 为观察表的一个前缀和一个后缀对应的element填充信息
     *
     * @param table
     * @param prefix
     * @param suffix
     * @param resetInform
     */
    private static void fillResetForOneSuffix(ObservationTable table, ResetLogicTimeWord prefix, LogicTimeWord suffix, List<Boolean> resetInform) {
        Pair pair = new Pair(prefix, suffix, resetInform);
        pair.setAnswer(table.teacher.membership(pair.getDTW()));
        Map<LogicTimeWord, Pair> suffixesMap = table.tableElement.get(prefix);
        if (suffixesMap == null) {
            suffixesMap = new HashMap<>();
        }
        suffixesMap.put(suffix, pair);
        table.tableElement.put(prefix, suffixesMap);
    }

    /**
     * 为一条delayTimeWord猜测重置信息
     *
     * @param template
     * @param delayTimeWord
     * @return
     */
    private static List<ResetDelayTimeWord> guessResetForCtx(ObservationTable template, DelayTimeWord delayTimeWord) {
        List<List<ResetDelayAction>> delayActions = new ArrayList<>();
        delayActions.add(new ArrayList<>());
        for (int i = 0; i < delayTimeWord.size(); i++) {
            List<List<ResetDelayAction>> tmp = new ArrayList<>();
            DelayTimedAction curDelayAction = delayTimeWord.get(i);
            BooleanAnswer answer = template.teacher.membership(delayTimeWord.subWord(0, i));
            boolean[] resetCondition = answer.isSink() ? TRUE_GROUP : TRUE_FALSE_GROUP;
            for (List<ResetDelayAction> actions : delayActions) {
                for (boolean reset : resetCondition) {
                    List<ResetDelayAction> copy = new ArrayList<>(actions);
                    copy.add(new ResetDelayAction(curDelayAction.getSymbol(), curDelayAction.getValue(), reset));
                    tmp.add(copy);
                }
            }
            delayActions = tmp;
        }
        return delayActions.stream().map(ResetDelayTimeWord::new).collect(Collectors.toList());
    }

    private static ResetLogicTimeWord transform2ResetLogicTimeWord(ResetLogicTimeWord prefix, LogicTimeWord suffix, List<Boolean> resetGuess) {
        List<LogicTimedAction> logicTimedActions = suffix.getTimedActions();
        List<ResetLogicAction> resetLogicTimedActions = new ArrayList<>();
        for (int i = 0; i < logicTimedActions.size(); i++) {
            LogicTimedAction e = logicTimedActions.get(i);
            boolean reset = i == logicTimedActions.size() - 1 || resetGuess.get(i);
            ResetLogicAction resetlogicTimedAction = new ResetLogicAction(e.getSymbol(), e.getValue(), reset);
            resetLogicTimedActions.add(resetlogicTimedAction);
        }
        ResetLogicTimeWord guessSuffix = new ResetLogicTimeWord(resetLogicTimedActions);
        return prefix.concat(guessSuffix);
    }

    /**
     * 检查即将添加的逻辑重置时间字信息是合法的重置信息，合法返回true，不合法返回false
     */
    private static boolean checkGuessReset(ObservationTable template, ResetLogicTimeWord guessPrefix) {
        List<ResetLogicTimeWord> prefixes = template.getPrefixList();
        List<ResetLogicAction> guessActions = guessPrefix.getTimedActions();
        // 1. 和前缀比
        for (ResetLogicTimeWord prefix : prefixes) {
            List<ResetLogicAction> prefixActions = prefix.getTimedActions();
            for (int i = 0; i < guessActions.size(); i++) {
                if (i >= prefixActions.size()
                        || !guessActions.get(i).getSymbol().equals(prefixActions.get(i).getSymbol())
                        || !guessActions.get(i).getValue().equals(prefixActions.get(i).getValue())) {
                    break;
                }
                if (guessActions.get(i).isReset() != prefixActions.get(i).isReset()) {
                    return false;
                }
            }
        }

        // 2. 和前缀后缀比
        for (int i = guessActions.size() - 2; i >= 0; i--) {
            if (!isResetSuitableForTable(template, guessPrefix.subWord(0, i), guessPrefix.subWord(i, guessPrefix.size())))
                return false;
        }

        return true;
    }

    /**
     * 判断当前, 前后缀的重置信息是否和观察表已有的重置信息有冲突
     */
    private static boolean isResetSuitableForTable(ObservationTable table, ResetLogicTimeWord prefix, ResetLogicTimeWord suffix) {
        if (suffix.size() <= 1) {
            return true;
        }
        Map<LogicTimeWord, Pair> suffixElement = table.tableElement.get(prefix);
        if (suffixElement == null) {
            return true;
        }
        for (int i = 2; i <= suffix.size(); i++) {
            ResetLogicTimeWord subSuffix = suffix.subWord(0, i);
            Pair pair = suffixElement.get(subSuffix.logicTimeWord());
            if (pair == null || pair.resetForSuffix == null) {
                continue;
            }
            for (int j = 0; j < pair.getResetForSuffix().size(); j++) {
                if (pair.getResetForSuffix().get(j) != subSuffix.get(j).isReset()) {
                    return false;
                }
            }
        }
        return true;
    }


    public List<ResetLogicTimeWord> getPrefixList() {
        List<ResetLogicTimeWord> logicTimeWordList = new ArrayList<>();
        logicTimeWordList.addAll(S);
        logicTimeWordList.addAll(R);
        return logicTimeWordList;
    }

    /**
     * 返回所有非sink的前缀
     */
    public int getTableSize() {
        return S.size() + R.size();
    }

    public ResetLogicTimeWord isClosed() {
        if (E2SRows == null) {
            E2SRows = new HashMap<>();
        }
        NextR:
        for (ResetLogicTimeWord r : R) {
            for (ResetLogicTimeWord s : S) {
                if (isRowEqual(s, r) == null) {
                    E2SRows.put(r, s);
                    continue NextR;
                }
            }
            return r;
        }
        return null;
    }

    public ConsistentResult isConsistent() {
        List<ResetLogicTimeWord> prefixes = getPrefixList();
        int count = prefixes.size();
        for (int i = 0; i < count - 1; i++) {
            for (int j = i + 1; j < count; j++) {
                ResetLogicTimeWord s1 = prefixes.get(i);
                ResetLogicTimeWord s2 = prefixes.get(j);
                if (isRowEqual(s1, s2) != null) continue;
                Set<LogicTimedAction> logicActions = getLastActionSet();
                for (LogicTimedAction lastAction : logicActions) {
                    ResetLogicTimeWord possibleS1 = possibleContact(s1, lastAction);
                    if (possibleS1 == null) continue;
                    ResetLogicTimeWord possibleS2 = possibleContact(s2, lastAction);
                    if (possibleS2 == null) continue;
                    LogicTimeWord action = isRowEqual(possibleS1, possibleS2);
                    if (action != null) {
                        return new ConsistentResult(possibleS1, possibleS2, lastAction);
                    }
                }
            }
        }
        return null;
    }

    private ResetLogicTimeWord possibleContact(ResetLogicTimeWord prefix, LogicTimedAction action) {
        ResetLogicTimeWord possibleS1 = prefix.concat(new ResetLogicAction(action.getSymbol(), action.getValue(), true));
        if (tableElement.containsKey(possibleS1)) {
            return possibleS1;
        }
        possibleS1 = prefix.concat(new ResetLogicAction(action.getSymbol(), action.getValue(), false));
        if (tableElement.containsKey(possibleS1)) {
            return possibleS1;
        }
        return null;
    }

    public List<ObservationTable> makeClosed(ResetLogicTimeWord resetLogicTimeWord) {
        ObservationTable copy = new ObservationTable(this, null, -1);
        copy.R.remove(resetLogicTimeWord);
        copy.S.add(resetLogicTimeWord);
        List<List<ResetLogicTimeWord>> prefixConditions = appendInitialSigmaForPrefix(resetLogicTimeWord, teacher, sigma);
        List<ObservationTable> result = new ArrayList<>();
        for (List<ResetLogicTimeWord> prefixCondition : prefixConditions) {
            result.addAll(addNewPrefixes(copy, prefixCondition, "make close"));
        }
        if (result.size() == 0) {
            result.add(copy);
        }
        return result;
    }

    public List<ObservationTable> makeConsistent(ConsistentResult consistentResult) {
        ResetLogicTimeWord word1 = consistentResult.fatherRowWord;
        ResetLogicTimeWord word2 = consistentResult.childRowWord;
        LogicTimedAction action = consistentResult.getKeyAction();
        if (word1 == null || word2 == null || action == null) {
            throw new RuntimeException("illegal consistentResult: " + word1 + " " + word2 + " " + action);
        }
        LogicTimeWord e = isRowEqual(word1, word2);
        if (e == null) {
            throw new RuntimeException("wrong consistentResult: " + word1 + " " + word2 + " " + action);
        }
        List<LogicTimedAction> logicTimedActions = new ArrayList<>();
        logicTimedActions.add(action);
        logicTimedActions.addAll(e.getTimedActions());
        LogicTimeWord newSuffix = new LogicTimeWord(logicTimedActions);
        return addNewSuffix(this, newSuffix, "make consistent");
    }

    public List<ObservationTable> refineCtx(DelayTimeWord delayTimeWord) {
        List<ResetDelayTimeWord> RDTW = guessResetForCtx(this, delayTimeWord);
        List<ResetLogicTimeWord> RLTW = RDTW.stream().map(TimeWordHelper::RDTW2RLTW).collect(Collectors.toList());
        List<ObservationTable> result = new ArrayList<>();
        for (ResetLogicTimeWord ctx : RLTW) {
            if (!checkGuessReset(this, ctx)) {
                continue;
            }
            List<ResetLogicTimeWord> subCtx = new ArrayList<>();
            for (int i = 1; i <= ctx.size(); i++) {
                ResetLogicTimeWord curSub = ctx.subWord(0, i);
                subCtx.add(curSub);
            }
            result.addAll(addNewPrefixes(this, subCtx, "refine counterexample"));
        }
        return result;
    }

    public DOTA buildHypothesis() {
        Clock clock = new Clock("c1");
        List<TaLocation> locationList = new ArrayList<>();
        List<TaTransition> transitionList = new ArrayList<>();
        //创建观察表每一行和location的映射关系
        Map<ResetLogicTimeWord, TaLocation> rowLocationMap = new HashMap<>();
        //根据s中的row来创建Location
        int id = 1;
        for (ResetLogicTimeWord sWord : S) {
            boolean init = sWord.equals(ResetLogicTimeWord.emptyWord());
            boolean accepted = tableElement.get(sWord).get(LogicTimeWord.emptyWord()).getAnswer().isAccept();
            TaLocation location = new TaLocation(String.valueOf(id), String.valueOf(id), init, accepted);
            locationList.add(location);
            rowLocationMap.put(sWord, location);
            id++;
        }

        for (ResetLogicTimeWord rWord : R) {
            ResetLogicTimeWord sWord = E2SRows.get(rWord);
            rowLocationMap.put(rWord, rowLocationMap.get(sWord));
        }

        //根据观察表来创建Transition
        List<ResetLogicTimeWord> sr = getPrefixList();
        for (ResetLogicTimeWord word : sr) {
            if (word.isEmpty()) {
                continue;
            }
            ResetLogicTimeWord pre = word.subWord(0, word.size() - 1);
            if (sr.contains(pre)) {
                ResetLogicAction action = word.getLastResetAction();
                TaLocation sourceLocation = rowLocationMap.get(pre);
                TaLocation targetLocation = rowLocationMap.get(word);
                String symbol = action.getSymbol();
                TimeGuard timeGuard = TimeGuard.bottomGuard(word.getLastLogicAction());
                Map<Clock, TimeGuard> clockTimeGuardMap = new HashMap<>();
                clockTimeGuardMap.put(clock, timeGuard);
                Set<Clock> resetClockSet = new HashSet<>();
                if (action.isReset()) {
                    resetClockSet.add(clock);
                }
                TaTransition transition = new TaTransition(
                        sourceLocation, targetLocation, symbol, clockTimeGuardMap, resetClockSet);
                if (!transitionList.contains(transition)) {
                    transitionList.add(transition);
                }
            }
        }

        DOTA evidenceDOTA = new DOTA("name", sigma, locationList, transitionList, clock);
        Learner.evidenceToDOTA(evidenceDOTA);
        this.hypothesis = evidenceDOTA;
        return evidenceDOTA;
    }

    public DOTA getHypothesis() {
        return hypothesis;
    }

    public Recorder getRecorder() {
        return recorder;
    }

    public void setRecorder(Recorder recorder) {
        this.recorder = recorder;
    }

    /**
     * 比较观察表的两行前缀对应的row是否相等
     */
    private LogicTimeWord isRowEqual(ResetLogicTimeWord s1, ResetLogicTimeWord s2) {
        Map<LogicTimeWord, Pair> row1 = tableElement.get(s1);
        Map<LogicTimeWord, Pair> row2 = tableElement.get(s2);
        for (LogicTimeWord suffix : E) {
            Pair pair1 = row1.get(suffix);
            Pair pair2 = row2.get(suffix);
            if (pair1 == null || pair2 == null) {
                throw new RuntimeException("unfilled table for: " + s1 + ", " + s2 + ", and " + suffix);
            }
            if (!pair1.answerEqual(pair2)) return suffix;
        }
        return null;
    }

    /**
     * 获得观察表所有前缀的最后一个动作
     */
    private Set<LogicTimedAction> getLastActionSet() {
        List<ResetLogicTimeWord> sr = getPrefixList();
        Set<LogicTimedAction> lastActionSet = new HashSet<>();
        for (ResetLogicTimeWord resetWord : sr) {
            if (!resetWord.isEmpty()) {
                LogicTimedAction last = resetWord.getLastLogicAction();
                lastActionSet.add(last);
            }
        }
        return lastActionSet;
    }

    @Data
    @AllArgsConstructor
    public static class ConsistentResult {
        private ResetLogicTimeWord fatherRowWord;
        private ResetLogicTimeWord childRowWord;
        private LogicTimedAction keyAction;
    }

    @Data
    private static class Pair {
        private ResetLogicTimeWord prefix;
        private LogicTimeWord suffix;
        @EqualsAndHashCode.Exclude
        private List<Boolean> resetForSuffix;
        @EqualsAndHashCode.Exclude
        private BooleanAnswer answer;

        public String stringAnswer() {
            if (answer == null) {
                return "no answer";
            }
            StringBuilder sb = new StringBuilder();
            if (resetForSuffix != null) {
                for (boolean reset : resetForSuffix) {
                    sb.append(reset ? "r" : "n");
                }
            }
            sb.append(answer.isAccept() ? "+" : "-");
            return sb.toString();
        }

        public boolean answerEqual(Pair pair) {
            if (this.answer == null || pair.getAnswer() == null) {
                throw new RuntimeException("table is not filled for current pair");
            }
            return answer.isAccept() == pair.getAnswer().isAccept();
        }

        public boolean setResetForSuffix(List<Boolean> resetForSuffix) {
            if (suffix.size() > 1) {
                if (resetForSuffix == null || resetForSuffix.size() != suffix.size() - 1) {
                    throw new RuntimeException("illegal reset information for suffix");
                }
                this.resetForSuffix = resetForSuffix;
            }
            return true;
        }

        /**
         * 对应观察表的一条记录, suffix.size() = resetForSuffix.size() + 1
         */
        public Pair(ResetLogicTimeWord prefix, LogicTimeWord suffix, List<Boolean> resetForSuffix) {
            this.prefix = prefix;
            this.suffix = suffix;
            this.setResetForSuffix(resetForSuffix);
        }

        public Pair(ResetLogicTimeWord prefix, LogicTimeWord suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }

        public static Pair instance(ResetLogicTimeWord prefix, LogicTimeWord suffix) {
            return new Pair(prefix, suffix);
        }

        /**
         * 获得观察表这条记录的逻辑重置时间字
         */
        public ResetLogicTimeWord getRLTW() {
            LogUtil.info("try to getRLTW from prefix: %s, suffix: %s, resetForSuffix: %s", prefix, suffix, resetForSuffix);
            List<ResetLogicAction> resetLogicActions = new ArrayList<>(prefix.size() + suffix.size());
            resetLogicActions.addAll(prefix.getTimedActions());
            List<LogicTimedAction> logicTimedActions = suffix.getTimedActions();
            for (int i = 0; i < logicTimedActions.size(); i++) {
                LogicTimedAction curLTA = logicTimedActions.get(i);
                ResetLogicAction curRLTA =
                        i == logicTimedActions.size() - 1 ?
                                new ResetLogicAction(curLTA.getSymbol(), curLTA.getValue(), true)
                                :
                                new ResetLogicAction(curLTA.getSymbol(), curLTA.getValue(), resetForSuffix.get(i));
                resetLogicActions.add(curRLTA);
            }
            return new ResetLogicTimeWord(resetLogicActions);
        }

        public DelayTimeWord getDTW() {
            ResetLogicTimeWord resetLogicTimeWord = getRLTW();
            DelayTimeWord delayTimeWord;
            try {
                delayTimeWord = TimeWordHelper.RLTW2DTW(resetLogicTimeWord);
            } catch (RuntimeException exception) {
                LogUtil.warn("RLTW2DTW failed , RLWT= %s, err=%s", resetLogicTimeWord, exception);
                delayTimeWord = null;
            }
            return delayTimeWord;
        }


        public LogicTimeWord getLTW() {
            LogicTimeWord pre = prefix.logicTimeWord();
            return pre.concat(suffix);
        }
    }


    public static void testIsResetSuitableForTable() {
        List<LogicTimedAction> logicTimedActions = new ArrayList<>();
        logicTimedActions.add(new LogicTimedAction("A", 2.0));
        logicTimedActions.add(new LogicTimedAction("B", 3.0));
        LogicTimeWord suffix = new LogicTimeWord(logicTimedActions);

        List<ResetLogicAction> resetLogicActions = new ArrayList<>();
        resetLogicActions.add(new ResetLogicAction("C", 2.0, true));
        ResetLogicTimeWord prefix = new ResetLogicTimeWord(resetLogicActions);

        Pair pair = new Pair(prefix, suffix, Arrays.asList(true));
        Map<ResetLogicTimeWord, Map<LogicTimeWord, Pair>> tableElement = new HashMap<>();
        Map<LogicTimeWord, Pair> element = new HashMap<>();
        element.put(suffix, pair);
        tableElement.put(prefix, element);

        ObservationTable table = new ObservationTable();
        table.tableElement = tableElement;
        table.S = new HashSet<>();
        table.S.add(prefix.concat(new ResetLogicAction("A", 2.0, true)).concat(new ResetLogicAction("B", 3.0, true)));
        table.R = new HashSet<>();
        LogUtil.info("测试结果，预期false, 实际是:" + checkGuessReset(table, transform2ResetLogicTimeWord(prefix, suffix, Arrays.asList(false))));
    }

    public String toString() {
        List<String> stringList = new ArrayList<>();
        List<String> suffixStringList = new ArrayList<>();
        List<ResetLogicTimeWord> prefixList = getPrefixList();
        List<LogicTimeWord> suffixSet = E;
        int maxLen = 0;
        for (ResetLogicTimeWord word : prefixList) {
            String s = word.toString();
            stringList.add(s);
            maxLen = Math.max(maxLen, s.length());
        }
        for (LogicTimeWord words : suffixSet) {
            String s = words.toString();
            suffixStringList.add(s);
        }


        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < maxLen; i++) {
            sb.append(" ");
        }
        sb.append("|");
        for (String s : suffixStringList) {
            sb.append(s);
            sb.append("|");
        }
        sb.append("\n");

        for (int i = 0; i < prefixList.size(); i++) {
            String prefixString = stringList.get(i);
            sb.append(prefixString);
            int slen = S.size();
            for (int k = 0; k < maxLen - prefixString.length(); k++) {
                sb.append(" ");
            }
            sb.append("|");
            for (int j = 0; j < suffixSet.size(); j++) {
                Pair pair = tableElement.get(prefixList.get(i)).get(suffixSet.get(j));
                String answer = pair.stringAnswer();
                sb.append(answer);
                String suffixString = suffixStringList.get(j);
                for (int k = 0; k < suffixString.length() - answer.length(); k++) {
                    sb.append(" ");
                }
                sb.append("|");
            }
            sb.append("\n");

            if (i == slen - 1) {
                for (int k = 0; k < maxLen; k++) {
                    sb.append("-");
                }
                sb.append("|");
                for (String suffixString : suffixStringList) {
                    for (int k = 0; k < suffixString.length(); k++) {
                        sb.append("-");
                    }
                    sb.append("|");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        testIsResetSuitableForTable();
//        List<LogicTimedAction> timeWords = new ArrayList<>();
//        timeWords.add(new LogicTimedAction("a", 3.0));
//        timeWords.add(new LogicTimedAction("a", 2.0));
//        timeWords.add(new LogicTimedAction("a", 3.0));
//        LogicTimeWord suffix = new LogicTimeWord(timeWords);
//        guessResetForOneSuffix(suffix);
    }
}
