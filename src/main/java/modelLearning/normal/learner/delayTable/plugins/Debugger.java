package modelLearning.normal.learner.delayTable.plugins;

import modelLearning.normal.learner.delayTable.Prefix;
import modelLearning.normal.learner.delayTable.base.BaseTable;
import modelLearning.normal.learner.delayTable.guess.GuessTable;
import ta.ota.DOTA;
import timedWord.DelayTimeWord;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Debugger {

    private final BaseTable baseTable;

    private final DOTA dota;

    private final Set<DelayTimeWord> realResetPrefix = new HashSet<>();

    public Debugger(BaseTable baseTable, DOTA dota) {
        this.baseTable = baseTable;
        this.dota = dota;
    }

    public void appendNewPrefixes(List<Prefix<DelayTimeWord>> filteredPrefixes) {
        for (Prefix<DelayTimeWord> w : filteredPrefixes) {
            DelayTimeWord word = w.getTimedWord();
            if (word.isEmpty()) continue;
            if (dota.transferReset(word).getLastAction().isReset()) {
                realResetPrefix.add(word);
            }
        }
    }

    public GuessTable getTargetGuessTable() {
        Set<DelayTimeWord> keyReset = baseTable.getKeyResetPrefixes();
        Set<DelayTimeWord> filteredPrefix = realResetPrefix.stream().filter(keyReset::contains).collect(Collectors.toSet());
        return baseTable.getGuessTable(filteredPrefix);
    }

    public GuessTable getRealGuessTable() {
        return baseTable.getGuessTable(realResetPrefix);
    }
}
