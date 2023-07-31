package modelLearning.frame;

import ta.TA;
import timedWord.TimedWord;

public interface Teacher<T extends TimedWord, A extends Answer, M extends TA, W extends TimedWord> {
    A membership(T timedWord);
    T equivalence(M hypothesis);
    T transferWord(W timeWord);

    int getMemberShipCount();

    int getEquivalenceCount();
}
