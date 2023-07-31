package modelLearning.normal.teacher;

import modelLearning.frame.Membership;
import ta.TaLocation;
import ta.ota.DOTA;
import timedWord.DelayTimeWord;

public class NormalOTAMembership implements Membership<DelayTimeWord, BooleanAnswer> {
    private DOTA dota;
    private int count;

    public NormalOTAMembership(DOTA dota) {
        this.dota = dota;
    }

    @Override
    public BooleanAnswer answer(DelayTimeWord timedWord) {
        count++;
        TaLocation taLocation = dota.reach(timedWord);
//        TaLocation taLocation = null;
        boolean accept = null != taLocation && taLocation.isAccept();
        boolean sink = null == taLocation || taLocation.isSink();
        return new BooleanAnswer(accept, sink);
    }

    @Override
    public int getCount() {
        return count;
    }
}
