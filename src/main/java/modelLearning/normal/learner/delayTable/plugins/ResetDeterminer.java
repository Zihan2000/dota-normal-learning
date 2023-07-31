package modelLearning.normal.learner.delayTable.plugins;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import modelLearning.normal.learner.delayTable.Prefix;
import modelLearning.normal.teacher.BooleanAnswer;
import modelLearning.normal.teacher.NormalTeacher;
import timedAction.DelayTimedAction;
import timedAction.ResetDelayAction;
import timedWord.DelayTimeWord;
import timedWord.ResetDelayTimeWord;

import java.math.BigDecimal;
import java.util.*;

/**
 * 尽可能提前确定重置信息的插件
 */
public class ResetDeterminer {

    // 前缀闭包的
    @Getter
    private final Map<DelayTimeWord, Boolean> reset;
    private final NormalTeacher teacher;

    public ResetDeterminer(NormalTeacher teacher) {
        this.reset = new HashMap<>();
        this.teacher = teacher;
    }

    public static void main(String[] args) {
        ValSolver solver = ValSolver.getDifferentRegionSolver(7);
        // d_1 +d_2和d_1 + x属于同一region
        ValGuard sameRegionGuard = ValGuard.getSameRegionGuard(3.5);
        solver.increase(-3.5);
        solver.and(sameRegionGuard);
        List<Double> result = solver.solve();
    }

    /**
     * 确定倒数第二个action的重置信息，并记录，true表示重置，false表示不重置，null表示无法确定
     */
    public Boolean determineReset(Prefix<DelayTimeWord> prefix, DelayTimeWord checkResetWord) {
        DelayTimeWord word = prefix.getTimedWord();
        if (!preCheck(word)) return null;
        if (checkResetWord == null) {
            checkResetWord = word.subWord(0, word.size() - 1);
        }
        Boolean result = reset.get(checkResetWord);
        if (result != null) {
            return result;
        }
        BigDecimal d1 = getTime(word, word.size() - 2).add(BigDecimal.valueOf(word.get(word.size() - 2).getValue()));
        BigDecimal d2 = BigDecimal.valueOf(word.get(word.size() - 1).getValue());
        BigDecimal d1PlusD2 = d1.add(d2);
        double d1Val = d1.doubleValue();
        double d2Val = d2.doubleValue();
        double d1PlusD2Val = d1PlusD2.doubleValue();
        if (mustResetCheck(prefix, d1Val, d2Val, d1PlusD2Val)) {
            reset.put(checkResetWord, true);
            return true;
        }
        if (mustNotResetCheck(prefix, d1Val, d2Val, d1PlusD2Val)) {
            reset.put(checkResetWord, false);
            return false;
        }
        return null;
    }

    /**
     * 确定 prefix 倒数第二个action的重置信息, 如果可以确定重置信息，重新检查 leftPrefixes 里的所有前缀，尝试确定前缀倒数第二个action的重置信息
     */
    public void determineAndRecheck(Prefix<DelayTimeWord> prefix, List<Prefix<DelayTimeWord>> leftPrefixes) {
        if (prefix.getTimedWord().size() <= 1) {
            return;
        }
        DelayTimeWord word = prefix.getTimedWord();
        DelayTimeWord checkResetWord = word.subWord(0, word.size() - 1);
        Boolean check = determineReset(prefix, checkResetWord);
        if (check == null) {
            return;
        }
        leftPrefixes.stream().filter(p -> {
                    if (p.getTimedWord().size() <= word.size()) {
                        return false;
                    }
                    for (int i = 0; i < checkResetWord.size(); i++) {
                        if (!checkResetWord.get(i).equals(p.getTimedWord().get(i))) {
                            return false;
                        }
                    }
                    return true;
                }).sorted(Comparator.comparingInt(p -> p.getTimedWord().size()))
                .forEach(p -> determineReset(p, null));
    }

    public ResetDelayTimeWord getResetWord(DelayTimeWord word) {
        if (!reset.containsKey(word)) return null;
        LinkedList<ResetDelayAction> resetDelayActions = new LinkedList<>();
        for (int i = word.size() - 1; i >= 0; i--) {
            DelayTimeWord subWord = word.subWord(0, i + 1);
            Boolean result = reset.get(subWord);
            if (result == null) {
                throw new RuntimeException("记录的结果不是前缀闭包的");
            }
            DelayTimedAction templateAction = word.get(i);
            ResetDelayAction action = new ResetDelayAction(templateAction.getSymbol(), templateAction.getValue(), result);
            resetDelayActions.addFirst(action);
        }
        return new ResetDelayTimeWord(resetDelayActions);
    }

    private boolean preCheck(DelayTimeWord word) {
        int size = word.size();
        // 只有一个action的word无法确定重置
        if (size <= 1) return false;
        if (size == 2) {
            return !isPointRegion(word.get(0).getValue());
        }
        // 0 ~ size -3 个action重置信息已知时才能确定第size - 2的action重置
        DelayTimeWord subWord = word.subWord(0, size - 2);
        return reset.containsKey(subWord) && !isPointRegion(getTime(word, size - 2).add(BigDecimal.valueOf(word.get(size - 2).getValue())).doubleValue());
    }

    /**
     * 对于一个(d,d_1)(b,d_2)，可以构建(d, d_1)(b, x)的delay timed word，其中x满足:
     * d_1 +d_2和d_1 + x属于同一region，但是d_2 和 x 属于不同region
     */
    private boolean mustResetCheck(Prefix<DelayTimeWord> prefix, double d1, double d2, double d1PlusD2) {
        if (isPointRegion(d1PlusD2)) {
            // (d,5.5)(b,11.5), 在(d,5.5,n)(b,11.5)时无法构建满足条件的x
            return false;
        }
        // d_2 和 x 属于不同region
        ValSolver solver = ValSolver.getDifferentRegionSolver(d2);
        // d_1 +d_2和d_1 + x属于同一region
        ValGuard sameRegionGuard = ValGuard.getSameRegionGuard(d1PlusD2);
        sameRegionGuard.increase(-d1);
        solver.and(sameRegionGuard);
        List<Double> result = solver.solve();
        if (result.size() == 0) {
            return false;
        }
        return checkDistinguish(prefix, result);
    }

    /**
     * d_2 和 x 属于同一region，但是d_1 +d_2和d_1 + x属于不同region
     */
    private boolean mustNotResetCheck(Prefix<DelayTimeWord> prefix, double d1, double d2, double d1PlusD2) {
        if (isPointRegion(d2)) {
            // (d,5.5)(b,12), 在(d,5.5,n)(b,11.5)时无法构建满足条件的x
            return false;
        }

        // d_1 +d_2和d_1 + x属于不同region
        ValSolver solver = ValSolver.getDifferentRegionSolver(d1PlusD2);
        solver.increase(-d1);
        // d_2 和 x 属于同一region
        ValGuard sameRegionGuard = ValGuard.getSameRegionGuard(d2);
        solver.and(sameRegionGuard);
        List<Double> result = solver.solve();
        if (result.size() == 0) {
            return false;
        }
        return checkDistinguish(prefix, result);
    }

    private boolean checkDistinguish(Prefix<DelayTimeWord> prefix, List<Double> checkVal) {
        DelayTimeWord word = prefix.getTimedWord();
        DelayTimeWord subWord = word.subWord(0, word.size() - 1);
        DelayTimedAction lastAction = word.getLastAction();
        List<DelayTimedAction> actions = subWord.getTimedActions();
        for (double val : checkVal) {
            List<DelayTimedAction> tmp = new ArrayList<>(actions);
            tmp.add(new DelayTimedAction(lastAction.getSymbol(), val));
            DelayTimeWord checkWord = new DelayTimeWord(tmp);
            BooleanAnswer answer = teacher.membership(checkWord);
            if (!answer.equals(prefix.getAnswer())) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal getTime(DelayTimeWord word, int toIdx) {
        BigDecimal time = new BigDecimal(0);
        if (toIdx == 0) {
            return time;
        }
        word = word.subWord(0, toIdx);
        for (int i = word.size() - 1; i >= 0; i--) {
            DelayTimeWord subWord = word.subWord(0, i + 1);
            Boolean result = reset.get(subWord);
            if (result == null) {
                throw new RuntimeException("记录的结果不是前缀闭包的");
            }
            DelayTimedAction templateAction = word.get(i);
            if (result) break;
            time = time.add(BigDecimal.valueOf(templateAction.getValue()));
        }
        return time;
    }

    /*
    判断val 是点region还是区间region，点 返回true, 区间返回false
     */
    private static boolean isPointRegion(double val) {
        return (int) val == val;
    }

    private static class ValSolver {
        private final List<ValGuard> guards;

        public ValSolver(ValGuard guard) {
            this.guards = new ArrayList<>();
            guards.add(guard);
        }

        public void and(ValGuard o) {
            for (ValGuard guard : guards) {
                guard.and(o);
            }
        }

        public void or(ValGuard o) {
            guards.add(o);
        }

        public void increase(double val) {
            for (ValGuard guard : guards) {
                guard.increase(val);
            }
        }

        // todo 获得满足条件的最小解和最大解
        public List<Double> solve() {
            List<Double> result = new ArrayList<>(2);
            for (ValGuard guard : guards) {
                Double val = guard.getSomeVal();
                if (val != null) {
                    result.add(val);
                }
            }
            return result;
        }

        /**
         * 与 val 所在的region不属于同一region的值的取值区间
         * val 为7则 (0,7) || (7, INF)
         * val 为 7.5则 (0, 7] || [8, INF)
         */
        public static ValSolver getDifferentRegionSolver(double val) {
            int minBound;
            int maxBound;
            boolean minOpen;
            boolean maxOpen;
            if (isPointRegion(val)) {
                minBound = (int) val;
                maxBound = minBound;
                minOpen = true;
                maxOpen = true;
            } else {
                minBound = (int) val;
                maxBound = minBound + 1;
                minOpen = false;
                maxOpen = false;
            }

            ValGuard guard = new ValGuard(true, minOpen, 0, minBound);
            ValSolver solver = new ValSolver(guard);
            solver.or(new ValGuard(maxOpen, true, maxBound, Double.MAX_VALUE));
            return solver;
        }
    }

    @Data
    @AllArgsConstructor
    private static class ValGuard {
        private boolean minOpen;
        private boolean maxOpen;
        private double minBound;
        private double maxBound;

        public void and(ValGuard o) {
            // update min bound
            if (minBound == o.minBound) {
                minOpen = minOpen || o.minOpen;
            } else if (minBound < o.minBound) {
                minOpen = o.minOpen;
                minBound = o.minBound;
            }

            // update max bound
            if (maxBound == o.maxBound) {
                maxOpen = maxOpen || o.maxOpen;
            } else if (maxBound > o.maxBound) {
                maxOpen = o.maxOpen;
                maxBound = o.maxBound;
            }
        }

        /**
         * 和val所在的region属于同一region的值的取值区间
         * val 为 7则 [7, 7]
         * val 为 7.5则 (7, 8)
         */
        public static ValGuard getSameRegionGuard(double val) {
            int minBound;
            int maxBound;
            boolean minOpen;
            boolean maxOpen;
            if (isPointRegion(val)) {
                minBound = (int) val;
                maxBound = minBound;
                minOpen = false;
                maxOpen = false;
            } else {
                minBound = (int) val;
                maxBound = minBound + 1;
                minOpen = true;
                maxOpen = true;
            }
            return new ValGuard(minOpen, maxOpen, minBound, maxBound);
        }

        public void increase(double val) {
            this.minBound = add(minBound, val);
            this.maxBound = add(maxBound, val);
        }

        public Double getSomeVal() {
            if (getMin(0.01) == null) {
                // 在0.01下无法求解，说明当前guard是一个无解的guard
                return null;
            }
            Double val = getMin(0.1);
            if (val == null) {
                val = getMax(0.1);
            }
            if (val == null) {
                val = getMin(0.01);
            }
            return val;
        }

        public Double getMin(double increaseVal) {
            if (minOpen) {
                return checkAndReturn(add(minBound, increaseVal));
            }
            return checkAndReturn(minBound);
        }

        public Double getMax(double increaseVal) {
            if (!maxOpen) {
                return checkAndReturn(add(maxBound, -increaseVal));
            }
            return checkAndReturn(maxBound);
        }

        private static double add(double val1, double val2) {
            return BigDecimal.valueOf(val1).add(BigDecimal.valueOf(val2)).doubleValue();
        }

        private Double checkAndReturn(double val) {
            if (check(val)) {
                return val;
            }
            return null;
        }

        public boolean check(double val) {
            return checkMin(val) && checkMax(val);
        }

        private boolean checkMin(double val) {
            if (minOpen) {
                return val > minBound;
            }
            return val >= minBound;
        }

        private boolean checkMax(double val) {
            if (maxOpen) {
                return val < maxBound;
            }
            return val <= maxBound;
        }
    }


}
