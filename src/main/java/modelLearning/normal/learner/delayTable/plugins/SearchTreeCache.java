package modelLearning.normal.learner.delayTable.plugins;

import modelLearning.normal.learner.delayTable.guess.GuessTableBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

public class SearchTreeCache {
    /**
     * 上一轮搜索缓存的记录
     */
    private TreeMap<Integer, List<GuessTableBuilder>> c1;
    /**
     * 这一轮搜索的记录
     */
    private TreeMap<Integer, List<GuessTableBuilder>> c2;

    public Integer getFirstIdx() {
        if (c1 == null || c1.isEmpty()) {
            return null;
        }
        return c1.firstKey();
    }

    public List<GuessTableBuilder> getCache(int prefixIdx) {
        if (c1 == null) {
            return null;
        }
        return c1.get(prefixIdx);
    }

    public void setCache(int prefixIdx, GuessTableBuilder guessTableBuilder) {
        List<GuessTableBuilder> guessTables = c2.getOrDefault(prefixIdx, new ArrayList<>());
        guessTables.add(guessTableBuilder);
        c2.put(prefixIdx, guessTables);
    }

    public void setCache(int prefixIdx, List<GuessTableBuilder> guessTableBuilders) {
        List<GuessTableBuilder> guessTables = c2.get(prefixIdx);
        if (guessTables == null) {
            guessTables = guessTableBuilders;
        } else {
            guessTables.addAll(guessTableBuilders);
        }
        c2.put(prefixIdx, guessTables);
    }

    public void refresh() {
        c1 = null;
        c2 = new TreeMap<>(Comparator.naturalOrder());
    }

    public void done() {
        c1 = c2;
        c2 = new TreeMap<>(Comparator.naturalOrder());
    }
}
