package modelLearning.normal.learner.logicTable;

import lombok.Getter;
import modelLearning.frame.Learner;
import modelLearning.normal.teacher.NormalEquivalence;
import modelLearning.normal.teacher.NormalTeacher;
import modelLearning.util.LogUtil;
import ta.ota.DOTA;
import ta.ota.ResetLogicTimeWord;
import timedWord.DelayTimeWord;

import java.util.*;

public class NormalLearner implements Learner<DelayTimeWord, DOTA> {
    private final Deque<ObservationTable> tables = new LinkedList<>();
    private ObservationTable curTable = null;
    private final NormalTeacher teacher;
    private final Set<String> sigma;

    @Getter
    private int visitedTableCount = 0;
    public NormalLearner(NormalTeacher teacher, Set<String> sigma) {
        this.teacher = teacher;
        this.sigma = sigma;
    }

    private ObservationTable getNextTable() {
        if (tables.isEmpty()) {
            throw new RuntimeException("empty tables!");
        }
        curTable = tables.poll();
        visitedTableCount++;
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


    /**
     * refine反例
     * 1. 处理反例生成新的子级ObservationTable
     * 2. 为新的子级ObservationTable添加Father的recorder信息
     */
    @Override
    public void refine(DelayTimeWord counterExample) {
        if (counterExample == NormalEquivalence.NotFoundDelayTimedWord) {
            learn();
            return;
        }
        List<ObservationTable> newTables = curTable.refineCtx(counterExample);
        Recorder recorder = new Recorder(curTable.getRecorder(), curTable.toString(), curTable.getHypothesis(), counterExample);
        for(ObservationTable table : newTables) {
            table.setRecorder(recorder);
        }
        tables.addAll(newTables);
        learn();
    }

    @Override
    public void show() {
        String sb = "-------------------------------------\n" + ", 剩余观察表数量: " + tables.size() + "\n" +
                "当前观察表id " + curTable.getId() + ", has parent " + curTable.getParentId() + " by " + curTable.getReason() + "\n" +
                curTable.toString();
        LogUtil.error(sb);
    }

    @Override
    public boolean check(DelayTimeWord counterExample) {
        return false;
    }

    @Override
    public DOTA buildHypothesis() {
        DOTA dota = curTable.buildHypothesis();
        LogUtil.info("获得假设自动机%s", dota);
        return dota;
    }

    @Override
    public DOTA getFinalHypothesis() {
        return null;
    }
}
