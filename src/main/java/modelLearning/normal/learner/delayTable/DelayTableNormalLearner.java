package modelLearning.normal.learner.delayTable;

import modelLearning.Config;
import modelLearning.frame.Learner;
import modelLearning.normal.learner.delayTable.base.BaseTable;
import modelLearning.normal.learner.delayTable.guess.GuessTable;
import modelLearning.normal.learner.delayTable.plugins.Debugger;
import modelLearning.normal.teacher.NormalTeacher;
import modelLearning.util.LogUtil;
import modelLearning.util.time.TimeManager;
import modelLearning.util.time.Timer;
import ta.ota.DOTA;
import ta.ota.ResetLogicTimeWord;
import timedWord.DelayTimeWord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class DelayTableNormalLearner implements Learner<DelayTimeWord, DOTA> {

    private ExecutorService executor;
    private final BaseTable baseTable;

    private DOTA hypothesis;

    private final String name;

    public DelayTableNormalLearner(String name, NormalTeacher teacher, Set<String> sigma) {
        this.name = name;
        baseTable = new BaseTable(sigma, teacher);
    }

    @Override
    public void init() {
        baseTable.init();
        executor = new ThreadPoolExecutor(Config.corePoolSize, Config.maxPoolSize, 5L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());
    }

    @Override
    public void learn() {
//        hypothesis = searchAndSolve();
        hypothesis = concurrentSearchAndSolve();
    }

    private DOTA concurrentSearchAndSolve() {
        int minSLimit = baseTable.getS().size();
        int maxSLimit = minSLimit + 1;
        if (hypothesis != null) {
            minSLimit = Math.min(minSLimit, hypothesis.getLocations().size());
            maxSLimit = Math.max(maxSLimit, hypothesis.getLocations().size() + 1);
        }
        if (Config.searchAllInOneBatch) {
            maxSLimit = Integer.MAX_VALUE-10;
            minSLimit = Integer.MAX_VALUE-10;
        }
        return baseTable.getHypothesis(minSLimit, maxSLimit, name);
    }

    /**
     * 1. 按照S区的limit从小到大尝试找到符合条件的GuessTable，在任意轮次发现解就返回符合条件的GuessTable
     * 2. GuessTable前缀location求解
     * 3. 根据1，2的解生成hypothesis
     */
    private DOTA searchAndSolve() {
        GuessTable targetTable = null;
        Map<ResetLogicTimeWord, Integer> locationSolver = null;
        Timer guessTimer = TimeManager.getDefaultManager().getOrRegisterTimer("生成猜测观察表操作");
        Timer solveLocationTimer = TimeManager.getDefaultManager().getOrRegisterTimer("location求解操作");
        Timer buildTimer = TimeManager.getDefaultManager().getOrRegisterTimer("构建假设自动机操作");

        int minSLimit = baseTable.getS().size();
        int maxSLimit = minSLimit + 1;
        if (hypothesis != null) {
            minSLimit = Math.min(minSLimit, hypothesis.getLocations().size());
            maxSLimit = Math.max(maxSLimit, hypothesis.getLocations().size() + 1);
        }
        if (Config.searchAllInOneBatch) {
            maxSLimit = Integer.MAX_VALUE-10;
            minSLimit = Integer.MAX_VALUE-10;
        }
        List<List<GuessTable>> guessTables;
        for (int limit = minSLimit; limit <= maxSLimit; limit++) {
            guessTimer.start();
            guessTables = baseTable.guessAllPossibleTables(Config.useSearchTree, limit, !Config.searchAllInOneBatch && limit != maxSLimit);
            guessTimer.pause();
            if (guessTables == null || guessTables.size() == 0) continue;

            solveLocationTimer.start();
            Search:
            for (List<GuessTable> tablesInSameSize : guessTables) {
                List<Future<Map<ResetLogicTimeWord, Integer>>> futureTasks = new ArrayList<>(tablesInSameSize.size());
                // 创建求解任务放到任务列表里
                tablesInSameSize.forEach(guessTable -> {
                    Future<Map<ResetLogicTimeWord, Integer>> f = executor.submit(new SolverCallable(guessTable));
                    futureTasks.add(f);
                });

                // 等待任意求解任务返回可行解，
                while (true) {
                    int finishedTaskCount = 0;
                    for (int i = 0; i < futureTasks.size(); i++) {
                        Future<Map<ResetLogicTimeWord, Integer>> f = futureTasks.get(i);
                        if (f.isDone()) {
                            finishedTaskCount++;
                            try {
                                locationSolver = f.get();
                            } catch (ExecutionException | InterruptedException ignored) {
                            }
                            if (locationSolver != null) {
                                targetTable = tablesInSameSize.get(i);
                                for (Future<Map<ResetLogicTimeWord, Integer>> f1 : futureTasks) {
                                    f1.cancel(true);
                                }
                                break Search;
                            }
                        }
                    }
                    if (finishedTaskCount == futureTasks.size()) {
                        LogUtil.warn("location数量为%d时未找到任何解，将尝试下一组", tablesInSameSize.get(0).getS().size());
                        break;
                    }
                }
            }
            solveLocationTimer.pause();
            if (targetTable != null) {
                break;
            }
        }

        if (targetTable == null) {
            Debugger debugger = baseTable.getDebugger();
            GuessTable standardGuessTable = debugger.getTargetGuessTable();
            if (standardGuessTable == null) {
                throw new RuntimeException("未找到任何可行解？不可能！！！！！！！");
            }
            Map<ResetLogicTimeWord, Integer> answer = null;
            try {
                answer = standardGuessTable.solveLocation(true);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            DOTA targetHypothesis = standardGuessTable.buildHypothesis(name, answer);
            throw new RuntimeException("未成功求解，检查代码");
        }
        buildTimer.start();
        hypothesis = targetTable.buildHypothesis(name, locationSolver);
        buildTimer.pause();
        return hypothesis;
    }

    @Override
    public void refine(DelayTimeWord counterExample) {
        int size = counterExample.size();
        List<DelayTimeWord> timeWords = new ArrayList<>(size);
        for (int i = 1; i <= size; i++) {
            timeWords.add(counterExample.subWord(0, i));
        }
        baseTable.addNewPrefixes(timeWords, false);
        learn();
    }

    @Override
    public void show() {

    }

    @Override
    public boolean check(DelayTimeWord counterExample) {
        return false;
    }

    @Override
    public DOTA buildHypothesis() {
        return hypothesis;
    }

    @Override
    public DOTA getFinalHypothesis() {
        return hypothesis;
    }



    private static class SolverCallable implements Callable<Map<ResetLogicTimeWord, Integer>> {
        private final GuessTable table;

        public SolverCallable(GuessTable table) {
            this.table = table;
        }

        @Override
        public Map<ResetLogicTimeWord, Integer> call() throws Exception {
            return table.solveLocation(Config.useZ3Solver);
        }
    }
}
