package modelLearning.normal.learner.delayTable.plugins;

import ta.ota.ResetLogicTimeWord;
import timedAction.TimedAction;
import timedWord.TimedWord;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TWNormalizer {
    private final Map<ResetLogicTimeWord, ResetLogicTimeWord> RLTNormalizedCache = new ConcurrentHashMap<>();

    public TWNormalizer() {
    }

    /**
     * 判断当前 TW(Timed word) 是否需要被正则化
     * <p>
     * 所有action的时钟值都是 1 or 0.5 则不需要正则化，否则需要正则化
     * <p>
     * 例如：
     * <p>
     * (a, 1)(b, 2)(c, 0.5) 返回 false
     * <p>
     * (a, 0.7)(b, 2)(c, 0.5) 返回 true
     */
    public boolean shouldNormalize(TimedWord<? extends TimedAction> tw) {
        List<? extends TimedAction> actions = tw.getTimedActions();
        for (TimedAction action : actions) {
            int intVal = action.getValue().intValue();
            double doubleVal = action.getValue() - intVal;
            if (doubleVal != 0 && doubleVal != 0.5) {
                return true;
            }
        }
        return false;
    }

    /**
     * 正则化给定的重置逻辑时间字，该方法是线程安全的
     */
    public ResetLogicTimeWord normalizeRLT(ResetLogicTimeWord tw) {
        if (!shouldNormalize(tw)) return tw;
        ResetLogicTimeWord normalized = RLTNormalizedCache.get(tw);
        if (normalized == null) {
            normalized = tw.normalization();
            RLTNormalizedCache.put(tw, normalized);
        }
        return normalized;
    }
}
