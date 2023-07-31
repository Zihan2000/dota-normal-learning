package modelLearning.Experiment;

import modelLearning.Experiment.result.LearnResult;
import modelLearning.frame.ExperimentFrame;
import modelLearning.smart.SmartExperimentFrame;
import modelLearning.smart.classificationTree.ClassificationTree;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import modelLearning.smart.defaultTeacher.SmartTeacher;
import modelLearning.smart.observationTable.MinimalObservationTable;
import modelLearning.smart.observationTable.ObservationTable;
import ta.ota.DOTA;
import ta.ota.DOTAUtil;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class ObservationTableCompareExperiment {
    public static void main(String[] args) throws IOException {
        String base = ".\\src\\main\\resources\\dota\\minimal\\";
        String experiment1Name = "互模拟包含关系校验观察表_v2";
        String experiment2Name = "普通观察表";
        String experiment3Name = "分类树";
//        String[] paths = {"4_4_20", "7_2_10", "7_4_10", "7_4_20", "7_6_10", "10_4_20", "12_4_20", "14_4_20", "18_4_3", "20_4_3"};
        String[] paths = {"7_4_10"};

        for (String path : paths) {
            LearnResult learnResult = new LearnResult();
            String dirPath = base + path;
            for (int i = 1; i <= 10; i++) {
                String fileName = path + "-" + i + ".json";
                System.out.println(fileName);
                String totalPath = dirPath + "\\" + fileName;
                DOTA ota = DOTAUtil.getDOTAFromJsonFile(totalPath);
                DOTAUtil.completeDOTA(ota);


                SmartTeacher teacher1 = new SmartTeacher(ota);
                SmartTeacher teacher2 = new SmartTeacher(ota);
                SmartTeacher teacher3 = new SmartTeacher(ota);

                System.out.println(experiment1Name);
                MinimalObservationTable observationTable1 = new MinimalObservationTable(fileName, ota.getSigma(), teacher1);
                SmartExperimentFrame frame1 = new SmartExperimentFrame(teacher1, observationTable1);
                frame1.start();

                System.out.println(experiment3Name);
                ClassificationTree classificationTree = new ClassificationTree(fileName, ota.getSigma(), teacher3);
                SmartExperimentFrame frame3 = new SmartExperimentFrame(teacher3, classificationTree);
                frame3.start();

                System.out.println(experiment2Name);
                ObservationTable observationTable2 = new ObservationTable(fileName, ota.getSigma(), teacher2);
                SmartExperimentFrame frame2 = new SmartExperimentFrame(teacher2, observationTable2);
                frame2.start();


                learnResult.addItemResult(experiment1Name, frame1.getItemResult());
                learnResult.addItemResult(experiment3Name, frame3.getItemResult());
                learnResult.addItemResult(experiment2Name, frame2.getItemResult());
            }

            learnResult.calculateResult();
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("实验结果", learnResult);
            jsonObject.put("latex", learnResult.calculateLatexResult());

            String outPath = dirPath + "\\" + experiment1Name + "_" + experiment2Name +"_result.txt";
            FileOutputStream out = new FileOutputStream(outPath);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out));
            bw.write(jsonObject.toString(SerializerFeature.PrettyFormat));
            bw.flush();
            bw.close();
        }

    }
}
