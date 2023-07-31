package modelLearning.frame;

import ta.TA;
import ta.ota.DOTA;
import timedWord.TimedWord;

public interface EquivalenceQuery<T extends TimedWord, R extends TA> {
    T findCounterExample(R hypothesis);

    int getCount();
}
