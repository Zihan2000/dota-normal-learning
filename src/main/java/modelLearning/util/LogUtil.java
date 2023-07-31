package modelLearning.util;

import modelLearning.Config;

import java.util.logging.Level;

public class LogUtil {

    public static void info(String template, Object... args) {
        if (Config.loglevel.intValue() >= Level.INFO.intValue()) {
            System.out.printf((template) + "%n", args);
        }
    }

    public static void warn(String template, Object... args) {
        if (Config.loglevel.intValue() >= Level.WARNING.intValue()) {
            System.out.printf((template) + "%n", args);
        }
    }

    public static void error(String template, Object... args) {
        if (Config.loglevel.intValue() >= Level.SEVERE.intValue()) {
            System.out.printf((template) + "%n", args);
        }
    }
}
