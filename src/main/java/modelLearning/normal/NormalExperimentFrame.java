package modelLearning.normal;

import modelLearning.frame.ExperimentFrame;
import modelLearning.frame.Learner;
import modelLearning.normal.learner.logicTable.NormalLearner;
import modelLearning.normal.teacher.NormalTeacher;
import ta.ota.DOTA;
import ta.ota.LogicTimeWord;
import timedWord.DelayTimeWord;

public class NormalExperimentFrame extends ExperimentFrame<DelayTimeWord, modelLearning.normal.teacher.BooleanAnswer, DOTA, LogicTimeWord> {
    public NormalExperimentFrame(NormalTeacher teacher, Learner<DelayTimeWord, DOTA> learner) {
        super(teacher, learner);
    }

    @Override
    public void start() {
        super.start();
//        System.out.printf("遍历的观察表数量：%d\n", ((NormalLearner)getLearner()).getVisitedTableCount());
    }


}
