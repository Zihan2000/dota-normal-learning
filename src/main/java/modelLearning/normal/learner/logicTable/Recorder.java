package modelLearning.normal.learner.logicTable;

import lombok.AllArgsConstructor;
import lombok.Data;
import ta.ota.DOTA;
import timedWord.DelayTimeWord;

import java.util.Deque;
import java.util.LinkedList;

@Data
@AllArgsConstructor
public class Recorder {
    private Recorder fatherRecorder;
    private String tableStr;
    private DOTA hypothesis;
    private DelayTimeWord counterexample;

    public String toString() {
        Deque<Recorder> recorders = new LinkedList<>();
        Recorder cur = this;
        while (cur!=null) {
            recorders.push(cur);
            cur = cur.getFatherRecorder();
        }
        StringBuilder sb = new StringBuilder();
        while (!recorders.isEmpty()) {
            cur = recorders.pop();
            sb.append("\n------------------\n当前hypothesis:\n")
                    .append(cur.hypothesis)
                    .append("\n反例是：")
                    .append(cur.counterexample);
        }
        return sb.toString();
    }
}
