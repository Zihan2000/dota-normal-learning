package modelLearning.frame;

import dbm.ActionGuard;
import dbm.DBMUtil;
import dbm.TransitionState;
import modelLearning.Config;
import ta.TA;
import ta.TAUtil;
import ta.TaLocation;
import ta.TimeGuard;
import ta.ota.*;
import timedWord.TimedWord;

import java.util.List;
import java.util.function.BiFunction;

public abstract class plainEquivalenceQuery<T extends TimedWord> implements EquivalenceQuery<T, DOTA> {
    protected DOTA dota;
    private int count;

    public plainEquivalenceQuery(DOTA dota) {
        this.dota = dota;
    }

    @Override
    public T findCounterExample(DOTA hypothesis) {
        count++;
        TA negDota = TAUtil.negTA(dota);
        TA negHypothesis = TAUtil.negTA(hypothesis);
        BiFunction<TaLocation, TaLocation, Boolean> predicate = null;
        if (Config.sinkCheckForCounterexample) {
            predicate = (l1, l2) -> (l1.isAccept() && l2.isAccept())
                    || (l1.isSink() && !l2.isSink())
                    || (!l1.isSink() && l2.isSink());
        }
        TA ta1 = TAUtil.parallelCombination(dota, negHypothesis, predicate);
        TA ta2 = TAUtil.parallelCombination(negDota, hypothesis, predicate);

        List<TransitionState> stateTrace1 = TAUtil.reachable(ta1);
        List<TransitionState> stateTrace2 = TAUtil.reachable(ta2);

        List<TransitionState> states = shortTrace(stateTrace1, stateTrace2);
        if (states == null) {
            return null;
        }
        return analyzeByLowBound(states, hypothesis);
    }

    @Override
    public int getCount() {
        return count;
    }

    private List<TransitionState> shortTrace(List<TransitionState> states1, List<TransitionState> states2) {
        if (null == states1 && null == states2) {
            return null;
        }
        if (null == states1) {
            return states2;
        }

        if (null == states2) {
            return states1;
        }
        return states1.size() <= states2.size() ? states1 : states2;
    }

    protected abstract T analyzeByLowBound(List<TransitionState> stateList, DOTA hypothesis);
}
