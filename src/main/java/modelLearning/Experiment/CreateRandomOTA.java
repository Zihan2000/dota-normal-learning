package modelLearning.Experiment;

import ta.ota.DOTA;
import ta.ota.DOTAUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class CreateRandomOTA {
    public static void main(String[] args) throws IOException {
        String base = ".\\src\\main\\resources\\dota\\20_4_3\\";
        for (int i = 1; i <= 10; i++){
            DOTA dota = DOTAUtil.createRandomDOTA(20,4,3,i);
            String name = dota.getName()+".json";
            DOTAUtil.writeDOTA2Json(dota, base+name);
        }

    }

    public static List<String> otaPath(){
        return Arrays.asList(
//                "4_4_3-a",
//                "6_4_5",
//                "8_4_5-a",
//                "10_4_5",
//                "12_4_5",
//                "14_4_5",
//                "8-2-5",
//                "8-3-5",
//                "8-5-5",
//                "8-6-5",
//                "8-7-5",
//                "8-4-2",
//                "8-4-3",
//                "8-4-4",
//                "8-4-6",
//                "8-4-7"
        );
    }

}
