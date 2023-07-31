package modelLearning.normal.learner.delayTable;

import lombok.AllArgsConstructor;
import lombok.Data;
import modelLearning.normal.teacher.BooleanAnswer;
import timedAction.TimedAction;
import timedWord.TimedWord;

import java.util.HashSet;
import java.util.Set;

@Data
@AllArgsConstructor
public class Prefix<T extends TimedWord<? extends TimedAction>> {
    private T timedWord;
    private BooleanAnswer answer;

    /**
     * 如果timeWord最后一个重置动作在第 resetIdx 个action，返回此时必须重置的timeWord和必须不能重置的timeWord
     */
    @SuppressWarnings("unchecked")
    public Set<T>[] getMustResetAndMustNotResetSet(int resetIdx) {
        int wordSize = timedWord.size();
        if (resetIdx < 0 || resetIdx > wordSize) {
            throw new RuntimeException(String.format("illegal resetIdx: %d, max value is %d", resetIdx, wordSize));
        }
        Set<T> mustResetWord = new HashSet<>();
        Set<T> mustNotResetWord = new HashSet<>();
        if (resetIdx > 0) {
            mustResetWord.add((T) timedWord.subWord(0, resetIdx));
        }
        for (int i = resetIdx +1; i<= wordSize; i++) {
            mustNotResetWord.add((T) timedWord.subWord(0, i));
        }
        return new Set[]{mustResetWord, mustNotResetWord};
    }

    @SuppressWarnings("unchecked")
    public T getMustResetWord(int resetIdx) {
        int wordSize = timedWord.size();
        if (resetIdx < 0 || resetIdx > wordSize) {
            throw new RuntimeException(String.format("illegal resetIdx: %d, max value is %d", resetIdx, wordSize));
        }
        if (resetIdx > 0) {
            return (T)timedWord.subWord(0, resetIdx);
        }
        return null;
    }

    @Override
    public int hashCode() {
        return timedWord.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Prefix)) return false;
        return this.timedWord.equals(((Prefix<?>) o).timedWord);
    }

    @Override
    public String toString() {
        return timedWord.toString() + " " + answer.toString();
    }
}
