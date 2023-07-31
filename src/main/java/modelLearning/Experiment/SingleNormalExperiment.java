package modelLearning.Experiment;

import modelLearning.frame.Learner;
import modelLearning.normal.NormalExperimentFrame;
import modelLearning.normal.learner.delayTable.DelayTableNormalLearner;
import modelLearning.normal.learner.logicTable.NormalLearner;
import modelLearning.normal.teacher.NormalTeacher;
import ta.ota.DOTA;
import ta.ota.DOTAUtil;
import timedWord.DelayTimeWord;

import java.io.IOException;

public class SingleNormalExperiment {
    // 6_2_10-5
    // 6_2_10-6
    // 6_2_10-7
    // 4_4_20 无法求解
    public static void main(String[] args) throws IOException {
        String base = ".\\src\\main\\resources\\dota\\normal\\";
        String path = "7_4_20";
        String dirPath = base + path;
        String fileName;
        String totalPath;
        DOTA ota;
        NormalTeacher teacher1;
        Learner<DelayTimeWord, DOTA> observationTable1;
        NormalExperimentFrame frame1;

//        fileName = ".\\src\\main\\resources\\dota\\example.json";
//        System.out.println(fileName);
//        ota = DOTAUtil.getDOTAFromJsonFile(fileName);
//        DOTAUtil.completeDOTA(ota);
//        System.out.println(ota);
//        teacher1 = new NormalTeacher(ota);
//        observationTable1 = new DelayTableNormalLearner(fileName, teacher1, ota.getSigma());
//        frame1 = new NormalExperimentFrame(teacher1, observationTable1);
//        frame1.start();

        for (int i = 9; i <= 10; i++) {
            fileName = path + "-" + i + ".json";
            System.out.println(fileName);
            totalPath = dirPath + "\\" + fileName;
            ota = DOTAUtil.getDOTAFromJsonFile(totalPath);
            DOTAUtil.completeDOTA(ota);
            System.out.println(ota);

            teacher1 = new NormalTeacher(ota);
//            observationTable1 = new NormalLearner(teacher1, ota.getSigma());
            observationTable1 = new DelayTableNormalLearner(fileName, teacher1, ota.getSigma());
            frame1 = new NormalExperimentFrame(teacher1, observationTable1);
            frame1.start();
        }

//        String fileName = path + "_" + 3 + ".json";
//        System.out.println(fileName);
//        String totalPath = dirPath + "\\" + fileName;
//        DOTA ota = DOTAUtil.getDOTAFromJsonFile(totalPath);
//        DOTAUtil.completeDOTA(ota);
//
//        System.out.println(experiment1Name);
//        DefaultTeacher teacher1 = new DefaultTeacher(ota);
//        MinimalObservationTable observationTable1 = new MinimalObservationTable(fileName, ota.getSigma(), teacher1);
//        ExperimentFrame frame1 = new ExperimentFrame(teacher1, observationTable1);
//        frame1.start();
    }
}
