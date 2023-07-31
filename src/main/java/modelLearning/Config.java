package modelLearning;

import java.util.logging.Level;

public interface Config {
    /**
     * 日志打印级别
     * <p>
     * The levels in descending order are:
     * <ul>
     * <li>SEVERE (highest value)
     * <li>WARNING
     * <li>INFO
     * <li>CONFIG
     * <li>FINE
     * <li>FINER
     * <li>FINEST  (lowest value)
     * </ul>
     */
    Level loglevel = Level.INFO;

    /* ------------- 多线程配置 ----------------*/
    boolean parallelSearch = true;
    int corePoolSize = Runtime.getRuntime().availableProcessors();
    int maxPoolSize = Runtime.getRuntime().availableProcessors() + 1;

//    int corePoolSize = 1;
//    int maxPoolSize = 1;

    /**
     * 使用Z3求解 location，目前仅实现了Z3求解location
     */
    boolean useZ3Solver = true;

    /**
     * 使用搜索树边猜边构建猜测观察表，每次构建都会检查猜测观察表的合理性，因此可以在发现错误时及时剪枝，避免后续的无效搜索
     *
     * 缓存超过搜索树maxSLimit的结果，下一轮搜索将直接从上一轮搜索的cache开始
     */
    boolean useSearchTree = true;

    /**
     * 使用搜索树搜索猜测观察表时是否限制S区大小，为true则会搜索所有的猜测观察表
     */
    boolean searchAllInOneBatch = false;

    /**
     * 等价查询时sink的location也作为反例，(a是sink而b不是也会被视为反例)
     */
    boolean sinkCheckForCounterexample = false;

    /**
     * 开启debug模式，会记录并生成正确的guessTable
     */
    boolean debug = false;

    /**
     * 主动确定部分前缀重置信息
     */
    boolean determineReset = true;

    /**
     * 激进搜索模式
     * 开启后将仅猜测关键重置，在一些情况下可能会导致无法产生假设自动机
     */
    boolean aggressiveSearch = true;


}
