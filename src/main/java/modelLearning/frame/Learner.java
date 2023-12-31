package modelLearning.frame;

import ta.*;
import ta.ota.DOTA;
import ta.ota.OTATranComparator;
import timedWord.TimedWord;

import java.util.ArrayList;
import java.util.List;

public interface Learner<T extends TimedWord, TAType extends TA> {
    //生命周期方法

    //初始化
    void init();

    //学习
    void learn();

    //对反例进行处理
    void refine(T counterExample);

    void show();

    boolean check(T counterExample);

    //构造假设自动机
    TAType buildHypothesis();

    //获取最终结果自动机
    TAType getFinalHypothesis();

    /**
     * 整理并合并DOTA的trans
     */
    static void evidenceToDOTA(DOTA dota) {
        Clock clock = dota.getClock();
        OTATranComparator comparator = new OTATranComparator(clock);
        List<TaTransition> formatTrans = new ArrayList<>();
        for (TaLocation l : dota.getLocations()) {
            for (String action : dota.getSigma()) {
                List<TaTransition> transitions = dota.getTransitions(l, action, null);
                if (transitions.size()==0) continue;
                transitions.sort(comparator);
                // 划分时间段
                for (int i = 0; i < transitions.size() - 1; i++) {
                    TaTransition curTrans = transitions.get(i);
                    TimeGuard curTimeGuard = curTrans.getTimeGuard(clock);
                    TimeGuard nextTimeGuard = transitions.get(i + 1).getTimeGuard(clock);
                    curTimeGuard.setUpperBound(nextTimeGuard.getLowerBound());
                    curTimeGuard.setUpperBoundOpen(!nextTimeGuard.isLowerBoundOpen());
                }
                TaTransition lastTrans = transitions.get(transitions.size() - 1);
                lastTrans.getTimeGuard(clock).setUpperBound(TimeGuard.MAX_TIME);
                lastTrans.getTimeGuard(clock).setUpperBoundOpen(true);

                // 合并时间段
                List<TaTransition> toSelected = new ArrayList<>(transitions);
                while (toSelected.size() != 0) {
                    TaTransition sample = toSelected.get(0);
                    List<TaTransition> sameTrans = new ArrayList<>();
                    List<TaTransition> left = new ArrayList<>();
                    for (TaTransition cur : toSelected) {
                        if (cur.getTargetLocation().equals(sample.getTargetLocation())) {
                            sameTrans.add(cur);
                        } else {
                            left.add(cur);
                        }
                    }
                    toSelected = left;
                    int i = 0, j = 0;
                    while (j < sameTrans.size()) {
                        TaTransition curTrans = sameTrans.get(j);
                        TimeGuard curTimeGuard = curTrans.getTimeGuard(clock);
                        if (j == sameTrans.size() - 1
                                || curTimeGuard.getUpperBound() != sameTrans.get(j + 1).getTimeGuard(clock).getLowerBound()
                                || (!curTimeGuard.isUpperBoundClose() && !sameTrans.get(j + 1).getTimeGuard(clock).isLowerBoundClose())
                                || curTrans.getResetClockSet().contains(clock) != sameTrans.get(j + 1).getResetClockSet().contains(clock)) {
                            TimeGuard lowerTimeGuard = sameTrans.get(i).getTimeGuard(clock);
                            curTimeGuard.setLowerBound(lowerTimeGuard.getLowerBound());
                            curTimeGuard.setLowerBoundOpen(lowerTimeGuard.isLowerBoundOpen());
                            formatTrans.add(curTrans);
                            i = j+1;
                        }
                        j++;
                    }
                }

            }
        }
        dota.setTransitions(formatTrans);
    }

}
