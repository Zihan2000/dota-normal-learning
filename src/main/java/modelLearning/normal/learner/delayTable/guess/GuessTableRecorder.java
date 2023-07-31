package modelLearning.normal.learner.delayTable.guess;

import lombok.AllArgsConstructor;
import ta.ota.ResetLogicTimeWord;

import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
public class GuessTableRecorder {
    private Map<ResetLogicTimeWord, Map<ResetLogicTimeWord, Boolean>> recorder;

    public boolean getDistinguishResult(ResetLogicTimeWord a, ResetLogicTimeWord b) {
        return getDistinguishMap(a).get(b);
    }

    public void appendNewElement(ResetLogicTimeWord w, Map<ResetLogicTimeWord, Boolean> element) {
        for (Map.Entry<ResetLogicTimeWord, Map<ResetLogicTimeWord, Boolean>> entry : recorder.entrySet()) {
            ResetLogicTimeWord key = entry.getKey();
            Map<ResetLogicTimeWord, Boolean> valMap = entry.getValue();
            Boolean boolVal = element.get(key);
            assert boolVal != null;
            valMap.put(w, boolVal);
        }
        recorder.put(w, element);
    }

    public Map<ResetLogicTimeWord, Boolean> getDistinguishMap(ResetLogicTimeWord a) {
        Map<ResetLogicTimeWord, Boolean> second = recorder.get(a);
        if (second == null) {
            throw new RuntimeException("given prefix " + a + " are not in tableRecorder");
        }
        return second;
    }

    public GuessTableRecorder deepClone() {
        Map<ResetLogicTimeWord, Map<ResetLogicTimeWord, Boolean>> copyRecorder = new HashMap<>(recorder.size());
        for (Map.Entry<ResetLogicTimeWord, Map<ResetLogicTimeWord, Boolean>> entry : recorder.entrySet()) {
            ResetLogicTimeWord key = entry.getKey();
            Map<ResetLogicTimeWord, Boolean> cloneVal = new HashMap<>(entry.getValue());
            copyRecorder.put(key, cloneVal);
        }
        return new GuessTableRecorder(copyRecorder);
    }
}
