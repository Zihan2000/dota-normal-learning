package modelLearning.normal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import modelLearning.frame.Learner;
import modelLearning.normal.teacher.BooleanAnswer;
import modelLearning.normal.teacher.NormalTeacher;
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

public class ObservationTable {
    private static final boolean[] TRUE_FALSE_GROUP = {true, false};
    private static final boolean[] TRUE_GROUP = {true};

    protected Set<ResetLogicTimeWord> S;
    protected Set<ResetLogicTimeWord> R;
    protected List<LogicTimeWord> E;
    protected Set<String> sigma;
    protected Map<ResetLogicTimeWord, Map<LogicTimeWord, Pair>> tableElement;
    private final NormalTeacher teacher;
    private DOTA hypothesis;

    public ObservationTable(ObservationTable table) {
        this.S = new HashSet<>(table.S);
        this.R = new HashSet<>(table.R);
        this.E = new ArrayList<>(table.E);
        this.sigma = table.sigma;
        this.tableElement = new HashMap<>();
        for (ResetLogicTimeWord key : table.tableElement.keySet()) {
            tableElement.put(key, new HashMap<>(table.tableElement.get(key)));
        }
        this.teacher = table.teacher;
    }

    private ObservationTable(Set<String> sigma, NormalTeacher teacher) {
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
            result.addAll(addNewPrefixes(origin, prefixCondition));
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
    private static List<ObservationTable> addNewPrefixes(ObservationTable template, List<ResetLogicTimeWord> prefixes) {
        prefixes = prefixes.stream().filter(p -> !template.S.contains(p) && !template.R.contains(p)).collect(Collectors.toList());
        ObservationTable copy = new ObservationTable(template);
        copy.R.addAll(prefixes);
        return fillTable(copy, prefixes, template.E);
    }

    /**
     * 添加新的后缀, 并补充观察表
     */
    private static List<ObservationTable> addNewSuffix(ObservationTable template, LogicTimeWord suffix) {
        ObservationTable copy = new ObservationTable(template);
        copy.E.add(suffix);
        List<ResetLogicTimeWord> prefixes = template.getPrefixList();
        return fillTable(copy, prefixes, Collections.singletonList(suffix));
    }

    /**
     * 填充观察表指定前缀和后缀对应的位置
     */
    private static List<ObservationTable> fillTable(ObservationTable template, List<ResetLogicTimeWord> prefixes, List<LogicTimeWord> suffixes) {
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
            List<List<List<Boolean>>> resetForETemplateArray = guessResetForRow(prefix, suffixes, needGuess);
            for (ObservationTable curResult : result) {
                for (List<List<Boolean>> resetForETemplate : resetForETemplateArray) {
                    ObservationTable copy = new ObservationTable(curResult);
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
    private static List<List<List<Boolean>>> guessResetForRow(ResetLogicTimeWord prefix, List<LogicTimeWord> suffixes, boolean needGuess) {
        List<List<List<Boolean>>> result = new ArrayList<>();
        result.add(new ArrayList<>());
        for (LogicTimeWord suffix : suffixes) {
            // 不需要重置信息的后缀
            if (suffix.size() <= 1) {
                List<Boolean> emptyTemplate = new ArrayList<>();
                for (List<List<Boolean>> curResult : result) {
                    curResult.add(emptyTemplate);
                }
                continue;
            }
            // 全是reset的重置信息的后缀
            if (!needGuess ||
                    (prefix.size() >= 0 && !prefix.getLastResetAction().isReset() && prefix.getLastResetAction().getValue() > suffix.get(0).getValue())) {
                List<Boolean> resetArrayTemplate = new ArrayList<>(suffix.size() - 1);
                for (int i = 0; i < suffix.size() - 1; i++) {
                    resetArrayTemplate.add(true);
                }
                for (List<List<Boolean>> curResult : result) {
                    curResult.add(resetArrayTemplate);
                }
                continue;
            }

            // 需要猜测重置信息的后缀
            List<List<List<Boolean>>> tmp = new ArrayList<>();
            List<List<Boolean>> resetTemplateArray = guessResetForOneSuffix(suffix);
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
    private static List<List<Boolean>> guessResetForOneSuffix(LogicTimeWord suffix) {
        List<List<Boolean>> result = new ArrayList<>();
        result.add(new ArrayList<>());
        result.add(new ArrayList<>());
        result.get(0).add(true);
        result.get(1).add(false);
        for (int i = 1; i < suffix.size() - 1; i++) {
            double beforeSuffixTime = suffix.getTimedActions().get(i - 1).getValue();
            double curSuffixTime = suffix.getTimedActions().get(i).getValue();
            List<List<Boolean>> tmp = new ArrayList<>();
            for (List<Boolean> curResult : result) {
                boolean[] true_false;
                // 前一个不是重置, 且前一个时钟值比当前大
                if (!curResult.get(curResult.size() - 1) && beforeSuffixTime > curSuffixTime) {
                    true_false = TRUE_GROUP;
                } else {
                    true_false = TRUE_FALSE_GROUP;
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


    public static void main(String[] args) {
        List<LogicTimedAction> timeWords = new ArrayList<>();
        timeWords.add(new LogicTimedAction("a", 3.0));
        timeWords.add(new LogicTimedAction("a", 2.0));
        timeWords.add(new LogicTimedAction("a", 3.0));
        LogicTimeWord suffix = new LogicTimeWord(timeWords);
        guessResetForOneSuffix(suffix);
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

    /**
     * check whether guessPrefix can be added into prefix
     */
    private static boolean checkGuessReset(ObservationTable template, ResetLogicTimeWord guessPrefix) {
        List<ResetLogicTimeWord> prefixes = template.getPrefixList();
        List<ResetLogicAction> guessActions = guessPrefix.getTimedActions();
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
        NextR:
        for (ResetLogicTimeWord r : R) {
            for (ResetLogicTimeWord s : S) {
                if (isRowEqual(s, r) == null) {
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
        R.remove(resetLogicTimeWord);
        S.add(resetLogicTimeWord);
        List<List<ResetLogicTimeWord>> prefixConditions = appendInitialSigmaForPrefix(resetLogicTimeWord, teacher, sigma);
        List<ObservationTable> result = new ArrayList<>();
        for (List<ResetLogicTimeWord> prefixCondition : prefixConditions) {
            result.addAll(addNewPrefixes(this, prefixCondition));
        }
        if (result.size() == 0) {
            result.add(this);
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
        return addNewSuffix(this, newSuffix);
    }

    public List<ObservationTable> refineCtx(DelayTimeWord delayTimeWord) {
        List<ResetDelayTimeWord> RDTW = guessResetForCtx(this, delayTimeWord);
        List<ResetLogicTimeWord> RLTW = RDTW.stream().map(TimeWordHelper::RDTW2RLTW).collect(Collectors.toList());
        List<ObservationTable> result = new ArrayList<>();
        for (ResetLogicTimeWord ctx : RLTW) {
            List<ResetLogicTimeWord> subCtx = new ArrayList<>();
            for (int i = 1; i < ctx.size(); i++) {
                ResetLogicTimeWord curSub = ctx.subWord(0, i);
                if (checkGuessReset(this, curSub)) {
                    subCtx.add(curSub);
                }
            }
            if (subCtx.size() == 0) {
                continue;
            }
            result.addAll(addNewPrefixes(this, subCtx));
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
        return evidenceDOTA;
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
            List<ResetLogicAction> resetLogicActions = new ArrayList<>(prefix.size() + suffix.size());
            resetLogicActions.addAll(prefix.getTimedActions());
            List<LogicTimedAction> logicTimedActions = suffix.getTimedActions();
            for (int i = 0; i < logicTimedActions.size(); i++) {
                LogicTimedAction curLTA = logicTimedActions.get(i);
                ResetLogicAction curRLTA =
                        i == logicTimedActions.size() - 1 ?
                                new ResetLogicAction(curLTA.getSymbol(), curLTA.getValue(), resetForSuffix.get(i))
                                :
                                new ResetLogicAction(curLTA.getSymbol(), curLTA.getValue(), true);
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
                delayTimeWord = null;
            }
            return delayTimeWord;
        }


        public LogicTimeWord getLTW() {
            LogicTimeWord pre = prefix.logicTimeWord();
            return pre.concat(suffix);
        }
    }
}
