package modelLearning.normal.teacher;

import lombok.AllArgsConstructor;
import lombok.Data;
import modelLearning.frame.Answer;

@Data
@AllArgsConstructor
public class BooleanAnswer implements Answer {
    private boolean accept;
    private boolean sink;
}
