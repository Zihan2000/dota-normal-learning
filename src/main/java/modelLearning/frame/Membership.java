package modelLearning.frame;

import timedWord.TimedWord;

public interface Membership<T extends TimedWord, R extends Answer> {
    R answer(T timedWord);

    int getCount();
}
