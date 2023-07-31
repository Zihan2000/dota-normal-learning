package modelLearning.util.time;

import lombok.Getter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Timer {
    @Getter
    private final String msg;
    @Getter
    private volatile long totalTime = 0;
    private volatile long beginTimeStamp = 0;

    private volatile int times = 0;

    private final Lock lock = new ReentrantLock();

    public Timer(String msg) {
        this.msg = msg;
    }

    synchronized public void start() {
        try {
            if (!lock.tryLock(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("不要在高耗时场景多线程计时!!!!!!");
            }
            beginTimeStamp = System.currentTimeMillis();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    synchronized public void pause() {
        totalTime += System.currentTimeMillis() - beginTimeStamp;
        times += 1;
        beginTimeStamp = 0;
        lock.unlock();
    }

    @Override
    public String toString() {
        return msg + " 共计时" + times + "次，总耗时 " + totalTime + "ms";
    }
}
