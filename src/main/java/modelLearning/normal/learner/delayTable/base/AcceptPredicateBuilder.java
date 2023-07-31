package modelLearning.normal.learner.delayTable.base;

import lombok.Getter;
import timedWord.DelayTimeWord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AcceptPredicateBuilder {

    private final List<ResetPredicate> resetPredicates;
    @Getter
    private final Set<DelayTimeWord> keyResetDTW;

    public AcceptPredicateBuilder() {
        resetPredicates = new ArrayList<>();
        keyResetDTW = new HashSet<>();
    }

    public AcceptPredicateBuilder appendPredicate(ResetPredicate resetPredicate) {
        for (ResetPredicate existPredicate : resetPredicates) {
            if (existPredicate.equivalent(resetPredicate)) {
                return this;
            }
        }
        if (!check(resetPredicate)) {
            throw new RuntimeException("非法重置情况，前缀不能既重置又不重置");
        }
        resetPredicates.add(resetPredicate);
        keyResetDTW.addAll(resetPredicate.getMustResetPrefixes());
        keyResetDTW.addAll(resetPredicate.getMustNotResetPrefixes());
        return this;
    }

    /**
     * 检查 mustReset和 mustNotReset 的交集是否为空
     */
    public boolean check(ResetPredicate resetPredicate) {
        Set<DelayTimeWord> mustReset = resetPredicate.getMustResetPrefixes();
        Set<DelayTimeWord> mustNotReset = resetPredicate.getMustNotResetPrefixes();
        for (DelayTimeWord word : mustReset) {
            if (mustNotReset.contains(word)) {
                return false;
            }
        }
        return true;
    }

    public int getPredicateSize() {
        return resetPredicates.size();
    }

    public boolean predicate(Set<DelayTimeWord> reset) {
        for (ResetPredicate resetPredicate : resetPredicates) {
            Set<DelayTimeWord> mustResetPrefixes = resetPredicate.getMustResetPrefixes();
            Set<DelayTimeWord> mustNotResetPrefixes = resetPredicate.getMustNotResetPrefixes();
            if (!reset.containsAll(mustResetPrefixes)) {
                continue;
            }
            if (mustNotResetPrefixes.stream().anyMatch(reset::contains)) {
                continue;
            }
            return true;
        }
        return false;
    }
}
