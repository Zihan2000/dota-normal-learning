package modelLearning.normal;

import modelLearning.frame.Learner;
import modelLearning.normal.teacher.NormalTeacher;
import ta.ota.DOTA;
import ta.ota.ResetLogicTimeWord;
import timedWord.DelayTimeWord;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

public class NormalLearner implements Learner<DelayTimeWord, DOTA> {
    private final PriorityQueue<ObservationTable> tables = new PriorityQueue<>(Comparator.comparingInt(ObservationTable::getTableSize));
    private ObservationTable curTable = null;
    private final NormalTeacher teacher;
    private final Set<String> sigma;
    public NormalLearner(NormalTeacher teacher, Set<String> sigma) {
        this.teacher = teacher;
        this.sigma = sigma;
    }

    private ObservationTable getNextTable() {
        if (tables.isEmpty()) {
            throw new RuntimeException("empty tables!");
        }
        curTable = tables.poll();
        return curTable;
    }

    @Override
    public void init() {
        List<ObservationTable> table = ObservationTable.init(teacher, sigma);
        tables.addAll(table);
    }

    @Override
    public void learn() {
        while (true) {
            ObservationTable table = getNextTable();
            ResetLogicTimeWord closeRow = table.isClosed();
            if (closeRow != null) {
                List<ObservationTable> newTables = table.makeClosed(closeRow);
                tables.addAll(newTables);
                continue;
            }

            ObservationTable.ConsistentResult consistentResult = table.isConsistent();
            if (consistentResult != null) {
                List<ObservationTable> newTables = table.makeConsistent(consistentResult);
                tables.addAll(newTables);
                continue;
            }
            break;
        }
    }

    @Override
    public void refine(DelayTimeWord counterExample) {
        List<ObservationTable> newTables = curTable.refineCtx(counterExample);
        tables.addAll(newTables);
        learn();
    }

    @Override
    public void show() {

    }

    @Override
    public boolean check(DelayTimeWord counterExample) {
        return false;
    }

    @Override
    public DOTA buildHypothesis() {
        return curTable.buildHypothesis();
    }

    @Override
    public DOTA getFinalHypothesis() {
        return null;
    }
}
