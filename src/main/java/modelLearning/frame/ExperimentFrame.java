package modelLearning.frame;

import modelLearning.Experiment.result.ItemResult;
import modelLearning.frame.Answer;
import modelLearning.frame.Teacher;
import modelLearning.smart.defaultTeacher.SmartTeacher;
import modelLearning.frame.Learner;
import lombok.Data;
import modelLearning.smart.observationTable.AbstractObservationTable;
import modelLearning.util.time.TimeManager;
import ta.TA;
import ta.ota.DOTA;
import ta.ota.ResetLogicTimeWord;
import timedWord.TimedWord;

@Data
public class ExperimentFrame<TimeWordType extends TimedWord, AnswerType extends Answer, TAType extends TA, W extends TimedWord> {
    private Teacher<TimeWordType, AnswerType, TAType, W> teacher;
    private Learner<TimeWordType, TAType> learner;
    private ItemResult itemResult = new ItemResult();

    public ExperimentFrame(Teacher<TimeWordType, AnswerType, TAType, W> teacher, Learner<TimeWordType, TAType> learner) {
        this.teacher = teacher;
        this.learner = learner;
    }

    public void start() {
        TimeManager timeManager = TimeManager.refreshDefault();
        System.out.println("开始学习");
        long start = System.currentTimeMillis();
        //自定义学习流程
        //1、观察表初始化
        learner.init();

        //2、开始学习
        learner.learn();
//        learner.show();

        //3、生成假设
        TAType hypothesis = learner.buildHypothesis();
        learner.show();
//        System.out.println(hypothesis);
        //4、等价判断
        TimeWordType ce = null;
        while (null != (ce = teacher.equivalence(hypothesis))) {
//            System.out.println("反例是："+ce);
            learner.refine(ce);
//            learner.show();
            hypothesis = learner.buildHypothesis();
            learner.show();
            System.out.println(hypothesis);
            while (learner.check(ce)) {
                learner.refine(ce);
                hypothesis = learner.buildHypothesis();
//                System.out.println("reuse");
            }
        }

        long end = System.currentTimeMillis();

        itemResult.setCostTime(end - start);
        itemResult.setEquivalenceCount(teacher.getEquivalenceCount());
        itemResult.setMembershipCount(teacher.getMemberShipCount());
        itemResult.setHLocationCount(hypothesis.getLocations().size());
        itemResult.setHTransitionCount(hypothesis.getTransitions().size());
        if (learner instanceof AbstractObservationTable) {
            itemResult.setObservationTableSize(((AbstractObservationTable) learner).getPrefixList().size());
            itemResult.setConsistentCostTime(((AbstractObservationTable) learner).consistentTime);
            System.out.println("观察表大小: " + ((AbstractObservationTable) learner).getPrefixList().size());
            System.out.println("一致性搜素耗时: " + ((AbstractObservationTable) learner).consistentTime);
        }
        System.out.println("学习结束");
        System.out.println("membership查询: " + teacher.getMemberShipCount());
        System.out.println("等价查询: " + teacher.getEquivalenceCount());
        System.out.println("location数量: " + hypothesis.getLocations().size());
        System.out.println("transition数量: " + hypothesis.getTransitions().size());
        System.out.println("耗时：" + (end - start) + " ms\n");
        System.out.println("额外时间记录：" + timeManager +"\n");
    }
}
