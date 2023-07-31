package modelLearning.normal;

import modelLearning.normal.learner.logicTable.NormalLearner;
import modelLearning.normal.teacher.NormalTeacher;
import ta.ota.DOTA;
import ta.ota.DOTAUtil;
import timedWord.DelayTimeWord;

import java.io.IOException;

public class experiment {
    public static void main(String[] args) throws IOException {
        String base = ".\\src\\main\\resources\\dota\\";
        String path = base + "example.json";
        DOTA ota = DOTAUtil.getDOTAFromJsonFile(path);
//        try {
//            GraphUtil.convert(ota, path.replace(".json", "-hypo.jpg"));
//        } catch (InterruptedException e) {
//            LogUtil.info("draw hypothesis interrupted");
//        }
        DOTAUtil.completeDOTA(ota);
        NormalTeacher teacher = new NormalTeacher(ota);
        NormalLearner learner = new NormalLearner(teacher, ota.getSigma());
        learner.init();
        learner.learn();
        DOTA hypothesis = learner.buildHypothesis();
        DelayTimeWord ce = null;
        while (null != (ce = teacher.equivalence(hypothesis))) {
//            System.out.println("反例是："+ce);
            learner.refine(ce);
//            learner.show();
            hypothesis = learner.buildHypothesis();
//            System.out.println(hypothesis);
        }
        System.out.println(ota);
    }
}
