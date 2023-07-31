package modelLearning.normal.teacher;

import lombok.Getter;
import modelLearning.frame.Teacher;
import modelLearning.util.LogUtil;
import ta.ota.DOTA;
import ta.ota.DOTAUtil;
import ta.ota.LogicTimeWord;
import timedWord.DelayTimeWord;

import java.util.HashMap;
import java.util.Map;

public class NormalTeacher implements Teacher<DelayTimeWord, modelLearning.normal.teacher.BooleanAnswer, DOTA, LogicTimeWord> {
    private final Map<DelayTimeWord, BooleanAnswer> membershipCache = new HashMap<>();
    private final NormalOTAMembership membership;
    private final NormalEquivalence equivalence;

    @Getter
    private final DOTA dota;

    public NormalTeacher(DOTA dota) {
        this.dota = dota;
        membership = new NormalOTAMembership(dota);
        equivalence = new NormalEquivalence(dota);
    }

    public BooleanAnswer membership(DelayTimeWord timedWord) {
        if (timedWord == null) {
            return new BooleanAnswer(false, true);
        }
        if (membershipCache.containsKey(timedWord)) {
            return membershipCache.get(timedWord);
        } else {
            BooleanAnswer answer = membership.answer(timedWord);
            membershipCache.put(timedWord, answer);
            return answer;
        }
    }

    public DelayTimeWord equivalence(DOTA hypothesis) {
        DelayTimeWord ctx = equivalence.findCounterExample(hypothesis);
        if (DelayTimeWord.emptyWord().equals(ctx)) {
            LogUtil.error("get empty counterexample?????");
        }
        return ctx;
    }

    @Override
    public DelayTimeWord transferWord(LogicTimeWord timeWord) {
        return null;
    }

    @Override
    public int getMemberShipCount() {
        return membership.getCount();
    }

    @Override
    public int getEquivalenceCount() {
        return equivalence.getCount();
    }
}
