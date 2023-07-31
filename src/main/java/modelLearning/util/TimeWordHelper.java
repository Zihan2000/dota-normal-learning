package modelLearning.util;

import ta.ota.LogicTimeWord;
import ta.ota.LogicTimedAction;
import ta.ota.ResetLogicAction;
import ta.ota.ResetLogicTimeWord;
import timedAction.DelayTimedAction;
import timedAction.ResetDelayAction;
import timedWord.DelayTimeWord;
import timedWord.ResetDelayTimeWord;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TimeWordHelper {

    public static boolean isValidWord(ResetLogicTimeWord resetLogicTimeWord) {
        double value = 0d;
        for (ResetLogicAction action : resetLogicTimeWord.getTimedActions()) {
            double v = action.getValue();
            if (v < value) {
                return false;
            }
            value = v;
            if (action.isReset()) {
                value = 0;
            }
        }
        return true;
    }

    /**
     * lastResetIdx ~ delayTimeWord.size() -1 个action不重置
     */
    public static double getDTWTime(DelayTimeWord delayTimeWord, int lastResetIdx) {
        if (lastResetIdx < 0 || lastResetIdx > delayTimeWord.size()) {
            throw new RuntimeException(String.format("illegal resetIdx: %d, max value is %d", lastResetIdx, delayTimeWord.size()));
        }
        List<DelayTimedAction> actions = delayTimeWord.getTimedActions();
        double val = 0f;
        for (int i = lastResetIdx; i < delayTimeWord.size(); i++) {
            val += actions.get(i).getValue();
        }
        return val;
    }

    /**
     * transform reset-logical-time word to delay-timed word
     */
    public static DelayTimeWord RLTW2DTW(ResetLogicTimeWord resetLogicTimeWord) {
        if (!isValidWord(resetLogicTimeWord)) {
            throw new RuntimeException("invalid resetLogicTimeWord: " + resetLogicTimeWord);
        }
        List<DelayTimedAction> delayTimedActions = new ArrayList<>();
        List<ResetLogicAction> resetLogicActions = resetLogicTimeWord.getTimedActions();
        double preTime = 0d;
        for (ResetLogicAction resetLogicAction : resetLogicActions) {
            double logicTime = resetLogicAction.getValue();
            double value = logicTime - preTime;
            DelayTimedAction delayTimedAction = new DelayTimedAction(resetLogicAction.getSymbol(), value);
            delayTimedActions.add(delayTimedAction);
            if (resetLogicAction.isReset()) {
                preTime = 0d;
            } else {
                preTime = logicTime;
            }
        }
        return new DelayTimeWord(delayTimedActions);
    }

    public static ResetLogicTimeWord RDTW2RLTW(ResetDelayTimeWord resetDelayTimeWord) {
        List<ResetLogicAction> resetLogicActions = new ArrayList<>();
        List<ResetDelayAction> resetDelayActions = resetDelayTimeWord.getTimedActions();
        double preTime = 0d;
        for (ResetDelayAction resetDelayAction : resetDelayActions) {
            double delayTime = resetDelayAction.getValue();
            double logicTime = delayTime + preTime;
            ResetLogicAction resetLogicAction = new ResetLogicAction(resetDelayAction.getSymbol(), logicTime, resetDelayAction.isReset());
            resetLogicActions.add(resetLogicAction);
            preTime = resetDelayAction.isReset() ? 0 : logicTime;
        }
        return new ResetLogicTimeWord(resetLogicActions);
    }

    public static ResetLogicTimeWord LTW2RLTW(LogicTimeWord logicTimeWord, List<Boolean> resetInfo) {
        if(resetInfo.size() < logicTimeWord.size() - 1) {
            throw new RuntimeException("illegal reset information, too small !!!");
        }

        if(resetInfo.size() == logicTimeWord.size() - 1) {
            resetInfo = new ArrayList<>(resetInfo);
            resetInfo.add(true);
        }

        List<ResetLogicAction> resetLogicActions = new ArrayList<>(logicTimeWord.size());
        for(int i = 0; i<logicTimeWord.size(); i++) {
            LogicTimedAction logicTimedAction = logicTimeWord.get(i);
            resetLogicActions.add(new ResetLogicAction(logicTimedAction.getSymbol(), logicTimedAction.getValue(), resetInfo.get(i)));
        }

        return new ResetLogicTimeWord(resetLogicActions);
    }

    public static ResetLogicTimeWord DTW2RLTW(DelayTimeWord delayTimeWord, Set<DelayTimeWord> resetDTW) {
        List<ResetLogicAction> resetLogicActions = new ArrayList<>();
        List<DelayTimedAction> resetDelayActions = delayTimeWord.getTimedActions();
        double preTime = 0d;
        for (int i = 0; i < resetDelayActions.size(); i++) {
            DelayTimedAction action = resetDelayActions.get(i);
            boolean reset = resetDTW.contains(delayTimeWord.subWord(0, i + 1));
            double delayTime = action.getValue();
            double logicTime = delayTime + preTime;
            ResetLogicAction resetLogicAction = new ResetLogicAction(action.getSymbol(), logicTime, reset);
            resetLogicActions.add(resetLogicAction);
            preTime = reset ? 0 : logicTime;
        }
        return new ResetLogicTimeWord(resetLogicActions);
    }

    public static void main(String[] args) {
        ResetDelayTimeWord resetLogicTimeWord = ResetDelayTimeWord.emptyWord();
        resetLogicTimeWord = resetLogicTimeWord.concat(new ResetDelayAction("a", 3.0, false))
                .concat(new ResetDelayAction("a", 2.0, true))
                .concat(new ResetDelayAction("a", 5.0, false))
                .concat(new ResetDelayAction("a", 6.0, true));
        ResetLogicTimeWord b = RDTW2RLTW(resetLogicTimeWord);
        DelayTimeWord c = RLTW2DTW(b);
        System.out.println(b);
    }
}
