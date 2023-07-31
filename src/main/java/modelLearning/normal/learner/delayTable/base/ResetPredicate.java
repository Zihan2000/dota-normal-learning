package modelLearning.normal.learner.delayTable.base;

import lombok.AllArgsConstructor;
import lombok.Getter;
import timedWord.DelayTimeWord;

import java.util.Set;

@AllArgsConstructor
public class ResetPredicate {
    @Getter
    private Set<DelayTimeWord> mustResetPrefixes;
    @Getter
    private Set<DelayTimeWord> mustNotResetPrefixes;

    /**
     * 判断给定的ResetPredicate是否和当前等价
     */
    public boolean equivalent(ResetPredicate o) {
        return mustNotResetPrefixes.size() == o.getMustNotResetPrefixes().size()
                && mustResetPrefixes.size() == o.getMustResetPrefixes().size()
                && mustNotResetPrefixes.containsAll(o.getMustNotResetPrefixes())
                && mustResetPrefixes.containsAll(o.getMustResetPrefixes());
    }
}
