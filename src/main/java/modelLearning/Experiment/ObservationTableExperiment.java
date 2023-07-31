package modelLearning.Experiment;

import modelLearning.smart.defaultTeacher.SmartTeacher;
import modelLearning.smart.observationTable.MinimalObservationTable;
//import ta.graph.GraphUtil;
import modelLearning.util.LogUtil;
import ta.graph.GraphUtil;
import ta.ota.*;

import java.io.IOException;

public class ObservationTableExperiment {
    public static void main(String[] args) throws IOException {
        String base = ".\\src\\main\\resources\\dota\\";
//        String path = base + "minimal\\4_4_20\\4_4_20-2.json";
//        String path = base + "minimal\\3_2\\3_2_3.json";
//        String path = base + "minimal\\7_4_10\\7_4_10-6.json";
//        String path = base + "example2.json";
//        String path = base + "minimal\\7_6_10\\7_6_10-7.json";
//        String path = base + "minimal\\TCP.json";
//        String path = base + "minimal\\12_4_20\\12_4_20-6.json";
        String path = base + "example.json";
        DOTA ota = DOTAUtil.getDOTAFromJsonFile(path);
        DOTAUtil.completeDOTA(ota);

        System.out.println(ota);
        SmartTeacher teacher = new SmartTeacher(ota);
//        ObservationTable modelLearning.smart.observationTable = new ObservationTable("h", ota.getSigma(), teacher);
        MinimalObservationTable observationTable = new MinimalObservationTable("h", ota.getSigma(), teacher);

        long start = System.currentTimeMillis();
        //自定义学习流程
        //1、观察表初始化
        observationTable.init();
//        modelLearning.smart.observationTable.setBiSimulationCheck(true);
//        modelLearning.smart.observationTable.setIncludedCheck(true);

        //2、开始学习
        observationTable.learn();
        observationTable.show();

        //3、生成假设
        DOTA hypothesis = observationTable.buildHypothesis();
        System.out.println(hypothesis);
        //4、等价判断
        ResetLogicTimeWord ce = null;
        while (null != (ce = teacher.equivalence(hypothesis))) {
            System.out.println("ctx:"+ce);
            observationTable.refine(ce);
            hypothesis = observationTable.buildHypothesis();
            observationTable.show();
            System.out.println(hypothesis);
        }

        long end = System.currentTimeMillis();

        System.out.println("学习结束");
        System.out.println("membership查询: " + teacher.getMembership().getCount());
        System.out.println("等价查询: " + teacher.getEquivalenceQuery().getCount());
        System.out.println("观察表大小: " + observationTable.getPrefixList().size());
        System.out.println("一致性搜素耗时: " + observationTable.consistentTime);
        System.out.println("location数量: " + hypothesis.getLocations().size());
        System.out.println("耗时：" + (end - start) + " ms\n");
        try {
            GraphUtil.convert(hypothesis, path.replace(".json", "-hypo.jpg"));
        } catch (InterruptedException e) {
            LogUtil.info("draw hypothesis interrupted");
        }

    }

}
