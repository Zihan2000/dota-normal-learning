package modelLearning.Experiment;

import modelLearning.Experiment.result.LearnResult;
import modelLearning.frame.ExperimentFrame;
import modelLearning.smart.classificationTree.ClassificationTree;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import modelLearning.smart.defaultTeacher.SmartTeacher;
import modelLearning.smart.observationTable.ObservationTable;
import ta.ota.DOTA;
import ta.ota.DOTAUtil;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class CompareExperiment {
    public static void main(String[] args) throws IOException {
        String base = ".\\src\\main\\resources\\dota\\";
        String path = "8_6_3";
        String dirPath = base + path;

        LearnResult learnResult = new LearnResult();
        for (int i = 1; i <= 15; i++) {
            String fileName = path + "-" + i + ".json";
            System.out.println(fileName);
            String totalPath = dirPath + "\\" + fileName;
            System.out.println("分类树");
            DOTA ota1 = DOTAUtil.getDOTAFromJsonFile(totalPath);
            DOTAUtil.completeDOTA(ota1);
            SmartTeacher teacher1 = new SmartTeacher(ota1);
            ClassificationTree classificationTree = new ClassificationTree(fileName, ota1.getSigma(), teacher1);
            ExperimentFrame frame1 = new ExperimentFrame(teacher1, classificationTree);
            frame1.start();


            System.out.println("观察表");
            DOTA ota2 = DOTAUtil.getDOTAFromJsonFile(totalPath);
            DOTAUtil.completeDOTA(ota2);
            SmartTeacher teacher2 = new SmartTeacher(ota2);
            ObservationTable observationTable = new ObservationTable(fileName, ota2.getSigma(), teacher2);
            ExperimentFrame frame2 = new ExperimentFrame(teacher2, observationTable);
            System.out.println(fileName);
            frame2.start();
            
        }




        JSONObject jsonObject = new JSONObject();
        jsonObject.put("实验结果", learnResult);

        String outPath = dirPath + "\\" + "result.txt";
        FileOutputStream out = new FileOutputStream(outPath);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out));
        bw.write(jsonObject.toString(SerializerFeature.PrettyFormat));
        bw.flush();
        bw.close();
    }
}
