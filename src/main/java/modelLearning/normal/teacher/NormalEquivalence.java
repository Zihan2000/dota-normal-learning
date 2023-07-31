package modelLearning.normal.teacher;

import dbm.ActionGuard;
import dbm.DBMUtil;
import dbm.TransitionState;
import modelLearning.frame.plainEquivalenceQuery;
import modelLearning.util.LogUtil;
import modelLearning.util.TimeWordHelper;
import ta.Clock;
import ta.TaLocation;
import ta.TimeGuard;
import ta.ota.DOTA;
import ta.ota.LogicTimeWord;
import ta.ota.LogicTimedAction;
import timedAction.DelayTimedAction;
import timedWord.DelayTimeWord;

import java.util.*;

public class NormalEquivalence extends plainEquivalenceQuery<DelayTimeWord> {

    public static final DelayTimeWord NotFoundDelayTimedWord = DelayTimeWord.emptyWord();

    public NormalEquivalence(DOTA dota) {
        super(dota);
    }

    public DelayTimeWord findCounterExample(DOTA hypothesis) {
        DelayTimeWord ctx = super.findCounterExample(hypothesis);
        if (ctx != null) {
            TaLocation dotaLocation = dota.reach(ctx);
            TaLocation hypothesisLocation = hypothesis.reach(ctx);
            System.out.println("反例是："+ctx + "\n" +
                    "teacher location : sink: " + dotaLocation.isSink() + ", accept: " + dotaLocation.isAccept() + "\n" +
                    "hypothesis location : sink: " + hypothesisLocation.isSink() + ", accept: " + hypothesisLocation.isAccept());
        }
        return ctx;
    }

    @Override
    protected DelayTimeWord analyzeByLowBound(List<TransitionState> stateList, DOTA hypothesis) {
        return analyzeByLowBoundForDTW2(stateList, hypothesis);
    }

    protected DelayTimeWord analyzeByLowBoundForDTW2(List<TransitionState> stateList, DOTA hypothesis) {
        List<ActionGuard> dotaActionGuards = DBMUtil.transfer(stateList, 1);
        List<ActionGuard> hypothesisActionGuards = DBMUtil.transfer(stateList, 2);

        Clock dotaClock = dota.getClock();
        Clock hypothesisClock = hypothesis.getClock();

        DelayTimeWord ctx = DFSFindCTX(dotaActionGuards, hypothesisActionGuards, 0, new ClockRecorder(dotaClock), new ClockRecorder(hypothesisClock));

        if (ctx == null) {
            throw new RuntimeException("no hypothesis found!!!!!!!!!");
        }
        return ctx;
    }

    private static DelayTimeWord DFSFindCTX(List<ActionGuard> guards1, List<ActionGuard> guards2, int idx, ClockRecorder c1, ClockRecorder c2) {
        DelayTimeWord ctx;
        if (idx == guards1.size()) {
            DelayTimedAction[] actionsArray = new DelayTimedAction[idx];
            ClockRecorder c = c1.pre;
            for (int i = idx - 1; i >= 0; i--) {
                if (c == null) {
                    throw new RuntimeException("recorder 不完整");
                }
                actionsArray[i] = new DelayTimedAction(guards1.get(i).getSymbol(), c.incVal);
                c = c.pre;
            }
            return new DelayTimeWord(Arrays.asList(actionsArray));
        }

        ActionGuard guard1 = guards1.get(idx);
        ActionGuard guard2 = guards2.get(idx);
        if (idx == 0) {
            double initVal = Math.max(guard1.getLowerValue(), guard2.getLowerValue());
            int intVal = (int) initVal;
            double doubleVal = initVal - intVal;
            ClockRecorder.increase(intVal, doubleVal, c1, c2);
        }
        boolean hasNext = true;
        for (; hasNext; hasNext = ClockRecorder.autoIncrease(c1, c2)) {
            TimeGuard timeGuard1 = guard1.getTimeGuard();
            TimeGuard timeGuard2 = guard2.getTimeGuard();
            if (!timeGuard1.isPassUpperBound(c1.getVal()) || !timeGuard2.isPassUpperBound(c2.getVal())) {
                // 已经不满足guard的上界, 不可能求解，返回null
                return null;
            }

            if (!timeGuard1.isPassLowerBound(c1.getVal()) || !timeGuard2.isPassLowerBound(c2.getVal())) {
                // guard不满足约束，继续上升
                continue;
            }

            ctx = DFSFindCTX(guards1, guards2, idx + 1, c1.nextRecorder(guard1.getResetClock()), c2.nextRecorder(guard2.getResetClock()));
            if (ctx != null) {
                return ctx;
            }
        }
        return null;
    }

    private static class ClockRecorder {

        ClockRecorder pre = null;
        // 当前时钟的整数部分
        int intVal = 0;
        // 当前时钟的小数部分
        double doubleVal = 0;
        // 从初始状态到
        double incVal = 0;

        Clock c;

        public ClockRecorder(int intVal, double doubleVal, Clock c) {
            this.intVal = intVal;
            this.doubleVal = doubleVal;
            this.c = c;
        }

        public ClockRecorder(Clock c) {
            this.c = c;
        }

        public double getVal() {
            return intVal + doubleVal;
        }

        public ClockRecorder nextRecorder(Set<Clock> resetClock) {
            int intVal = this.intVal;
            double doubleVal = this.doubleVal;
            if (resetClock.contains(c)) {
                intVal = 0;
                doubleVal = 0;
            }
            ClockRecorder copy = new ClockRecorder(intVal, doubleVal, this.c);
            copy.pre = this;
            return copy;
        }

        public static boolean autoIncrease(ClockRecorder c1, ClockRecorder c2) {
            if (c1.getVal() > TimeGuard.MAX_TIME || c2.getVal() > TimeGuard.MAX_TIME) {
                return false;
            }

            // 都是整数
            if (c1.doubleVal == 0 && c2.doubleVal == 0) {
                increase(0, 0.5, c1, c2);
                return true;
            }

            // 都是分数
            if (c1.doubleVal != 0 && c2.doubleVal != 0) {
                double increaseVal = 1 - Math.max(c1.doubleVal, c2.doubleVal);
                increaseVal = (double) Math.round(increaseVal * 100) / 100;
                increase(0, increaseVal, c1, c2);
                return true;
            }

            // 一个整数一个分数
            double increaseVal;
            if (c1.doubleVal != 0) {
                increaseVal = Math.round((10*(1- c1.doubleVal) / 2)) / 10.0;
            } else {
                increaseVal = Math.round((10*(1- c2.doubleVal) / 2)) / 10.0;
            }
            increase(0, increaseVal, c1, c2);
            return true;
        }

        public static void increase(int intVal, double doubleVal, ClockRecorder... cs) {
            for (ClockRecorder c : cs) {
                c.increase(intVal, doubleVal);
            }
        }

        private void increase(int intVal, double doubleVal) {
            this.intVal += intVal;
            this.doubleVal += doubleVal;
            if (this.doubleVal >= 1) {
                this.doubleVal -=1;
                this.intVal +=1;
            }
            this.doubleVal = (double) Math.round(this.doubleVal * 100) / 100;
            this.incVal = this.incVal + intVal + doubleVal;
            this.incVal = (double) Math.round(this.incVal * 100) / 100;
        }
    }

    // todo: check analyzeByLowBoundForDTW的正确性
    protected DelayTimeWord analyzeByLowBoundForDTW(List<TransitionState> stateList, DOTA hypothesis) {
        List<ActionGuard> dotaActionGuards = DBMUtil.transfer(stateList, 1);
        List<ActionGuard> hypothesisActionGuards = DBMUtil.transfer(stateList, 2);

        //初始化最小反例
        LogicTimeWord logicTimeWord = LogicTimeWord.emptyWord();
        for (ActionGuard actionGuard : dotaActionGuards) {
            LogicTimedAction logicAction = new LogicTimedAction(actionGuard.getSymbol(), actionGuard.getLowerValue());
            logicTimeWord = logicTimeWord.concat(logicAction);
        }

        //反例判断
        ex:
        while (true) {
            // normal teacher返回重置信息，因此重置信息不同不能作为反例的依据
//            if (!resetLogicTimeWord1.equals(resetLogicTimeWord2)) {
//                return resetLogicTimeWord1;
//            }
            DelayTimeWord delayTimeWord = null;
            try {
                delayTimeWord = TimeWordHelper.RLTW2DTW(dota.transferReset(logicTimeWord));
            } catch (RuntimeException e) {
                delayTimeWord = DelayTimeWord.emptyWord();
            }

            TaLocation a1 = dota.reach(delayTimeWord);
            TaLocation a2 = hypothesis.reach(delayTimeWord);
            boolean a1Accept = a1 != null && a1.isAccept();
            boolean a2Accept = a2 != null && a2.isAccept();
            // 接收状态不同
            if (a1Accept != a2Accept) {
                return delayTimeWord;
            }
            for (int i = 0; i < logicTimeWord.size(); i++) {
                LogicTimedAction logicAction = logicTimeWord.get(i);
                ActionGuard actionGuard = dotaActionGuards.get(i);
                TimeGuard guard = dotaActionGuards.get(i).getTimeGuard();
                double value = logicAction.getValue();
                if (value == (int) value) {
                    value += 0.5;
                    if (guard.isPass(value)) {
                        logicAction.setValue(value);
                        continue ex;
                    }
                    value = actionGuard.getLowerValue();
                    logicAction.setValue(value);
                    continue;
                }
                value = (int) (value + 1);
                if (guard.isPass(value)) {
                    logicAction.setValue(value);
                    continue ex;
                }
                value = actionGuard.getLowerValue();
                logicAction.setValue(value);
            }
            LogUtil.warn("找不到反例，这次hypothesis的重置信息和原TA有较大不同，已自动忽略当前观察表");
            return NotFoundDelayTimedWord;
        }
    }




    public static void main(String[] args) {
        Clock dotaClock = new Clock("c");
        Clock hypothesisClock = new Clock("c1");
        Set<Clock> dotaClockSet = new HashSet<>();
        dotaClockSet.add(dotaClock);
        Set<Clock> hypothesisClockSet = new HashSet<>();
        hypothesisClockSet.add(hypothesisClock);
        Set<Clock> emptySet = new HashSet<>();
        Set<Clock> allSet = new HashSet<>();
        allSet.add(dotaClock);
        allSet.add(hypothesisClock);

        List<ActionGuard> dotaActionGuards = new ArrayList<>();
        dotaActionGuards.add(new ActionGuard("a", new TimeGuard(true, true, 6, TimeGuard.MAX_TIME), emptySet));
        dotaActionGuards.add(new ActionGuard("a", new TimeGuard(true, false, 6, 8), dotaClockSet));
        dotaActionGuards.add(new ActionGuard("b", new TimeGuard(true, true, 0, 1), hypothesisClockSet));
        dotaActionGuards.add(new ActionGuard("b", new TimeGuard(true, true, 7, 8), allSet));

        List<ActionGuard> hypothesisActionGuards = new ArrayList<>();
        hypothesisActionGuards.add(new ActionGuard("a", new TimeGuard(true, true, 6, TimeGuard.MAX_TIME), emptySet));
        hypothesisActionGuards.add(new ActionGuard("a", new TimeGuard(true, false, 6, 8), dotaClockSet));
        hypothesisActionGuards.add(new ActionGuard("b", new TimeGuard(true, true, 6, 7), hypothesisClockSet));
        hypothesisActionGuards.add(new ActionGuard("b", new TimeGuard(true, true, 6, 7), allSet));

        DelayTimeWord ctx = DFSFindCTX(dotaActionGuards, hypothesisActionGuards, 0, new ClockRecorder(dotaClock), new ClockRecorder(hypothesisClock));
        System.out.println(ctx);
    }
}
