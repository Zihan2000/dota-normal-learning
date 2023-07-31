package modelLearning.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class NumberHelper {

    public static double setDoubleScale(double val, int scale) {
        return new BigDecimal(val).setScale(scale, RoundingMode.UP).doubleValue();
    }
}
