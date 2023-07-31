package modelLearning.util.time;

import modelLearning.util.LogUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TimeManager {
    private final Map<String, Timer> timerMap = new ConcurrentHashMap<>();

    private TimeManager() {}

    public static TimeManager getDefaultManager() {
        return TimeManagerFactory.get();
    }

    public static synchronized TimeManager refreshDefault() {
        TimeManager manager = TimeManagerFactory.get();
        manager.timerMap.clear();
        return manager;
    }

    public Timer registerTimer(String name) {
        if (timerMap.containsKey(name)) {
            LogUtil.warn("计时器%s已注册过，即将覆盖上次注册", name);
        }
        Timer timer = new Timer(name);
        timerMap.put(name, timer);
        return timer;
    }

    public Timer getTimer(String name) {
        return timerMap.get(name);
    }
    public Timer getOrRegisterTimer(String name) {
        Timer t = getTimer(name);
        if (t == null) {
            t = registerTimer(name);
        }
        return t;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("-----------------\n当前实验计时统计如下：\n");
        for (Timer timer : timerMap.values()) {
            sb.append(timer.toString()).append("\n");
        }
        sb.append("-----------------\n");
        return sb.toString();
    }

    private static class TimeManagerFactory {
        private static final TimeManager manager = new TimeManager();
        private static TimeManager get() {
            return manager;
        }
    }
}
