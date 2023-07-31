package modelLearning.util;

import lombok.Getter;

public class IntegerEntity {

    @Getter
    private int val;

    public IntegerEntity(int val) {
        this.val = val;
    }

    public IntegerEntity() {
        val = 0;
    }

    public int increase() {
        return ++val;
    }

    public int decrease() {
        return --val;
    }
}
