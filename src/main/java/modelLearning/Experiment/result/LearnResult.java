package modelLearning.Experiment.result;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.util.*;

@Data
public class LearnResult {
    private String name;
    private Map<String, ItemResult> resultReporter = new HashMap<>();

    @JSONField(serialize = false)
    private Map<String, List<ItemResult>> resultRecorder = new HashMap<>();

    public void addItemResult(String name, ItemResult itemResult) {
        List<ItemResult> itemResults = resultRecorder.getOrDefault(name, new ArrayList<>());
        itemResults.add(itemResult);
        resultRecorder.put(name, itemResults);
    }

    public LearnResult() {

    }

    public void calculateResult() {
        for (Map.Entry<String, List<ItemResult>> entry : resultRecorder.entrySet()) {
            resultReporter.put(entry.getKey(), ItemResult.plusAndAvg(entry.getValue()));
        }
    }

    public Map<String, String> calculateLatexResult() {
        Map<String, String> latexFormRecorder = new HashMap<>();
        for (Map.Entry<String, ItemResult> entry : resultReporter.entrySet()) {
            ItemResult val = entry.getValue();
            String sb = "&" +
                    "&" + val.getMembershipMin() +
                    "&" + (int) val.getMembershipCount() +
                    "&" + val.getMembershipMax() +
                    "&&" + val.getEquivalenceMin() +
                    "&" + (int) val.getEquivalenceCount() +
                    "&" + val.getEquivalenceMax() +
                    "&&" + val.getHLocationCount() +
                    "&&" + val.getHTransitionCount() +
                    "&" + (val.getObservationTableSize() == 0 ? "-" : val.getObservationTableSize()) +
                    String.format("&%.2f\\", val.getCostTime() / 1000.0);
            latexFormRecorder.put(entry.getKey(), sb);
        }
        return latexFormRecorder;
    }
}
