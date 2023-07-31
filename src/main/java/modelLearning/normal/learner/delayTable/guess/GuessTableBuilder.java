package modelLearning.normal.learner.delayTable.guess;

import modelLearning.normal.learner.delayTable.Prefix;
import modelLearning.normal.learner.delayTable.base.BaseTable;
import modelLearning.normal.learner.delayTable.plugins.TWNormalizer;
import modelLearning.util.TimeWordHelper;
import ta.ota.ResetLogicTimeWord;
import timedWord.DelayTimeWord;

import java.util.*;

/**
 * 构造GuessTable的构造器，通过不断调用appendPrefix最终生成close且合法的 guessTable
 */
public class GuessTableBuilder extends GuessTable{
    /**
     * 记录了当前builder存储的所有正则化过的逻辑重置时间字
     */
    private List<Prefix<ResetLogicTimeWord>> rltwPrefixes;

    /**
     * 重置的dtw
     */
    private Set<DelayTimeWord> resetDTW;


    /*----------------在所有builder中唯一的值，只能在init时引入---------------*/
    private List<Prefix<DelayTimeWord>> dtwPrefixes;
    private TWNormalizer normalizer;
    private BaseTable baseTable;

    public GuessTableBuilder(Set<Prefix<ResetLogicTimeWord>> s, Set<Prefix<ResetLogicTimeWord>> r, GuessTableRecorder recorder, Set<String> sigma) {
        super(s, r, recorder, sigma);
    }

    public void initBuilder(List<Prefix<DelayTimeWord>> dtwPrefixes, BaseTable baseTable, TWNormalizer normalizer) {
        this.dtwPrefixes = dtwPrefixes;
        this.baseTable = baseTable;
        this.normalizer = normalizer;
        this.resetDTW = new HashSet<>();
        this.rltwPrefixes = new ArrayList<>();
    }

    public GuessTableBuilder deepClone() {
        // clone GuessTable
        Set<Prefix<ResetLogicTimeWord>> copyS = new HashSet<>(S);
        Set<Prefix<ResetLogicTimeWord>> copyR = new HashSet<>(R);
        GuessTableRecorder copyRecorder = recorder.deepClone();
        GuessTableBuilder builder = new GuessTableBuilder(copyS, copyR, copyRecorder, sigma);

        // clone 不变值
        builder.dtwPrefixes = dtwPrefixes;
        builder.baseTable = baseTable;
        builder.normalizer = normalizer;

        // clone 存储变量
        builder.resetDTW = new HashSet<>(resetDTW);
        builder.rltwPrefixes = new ArrayList<>(rltwPrefixes);
        return builder;
    }

    public boolean appendPrefix(int dtwIdx, boolean reset) {
        Prefix<DelayTimeWord> DTPrefix = dtwPrefixes.get(dtwIdx);
        // update reset
        if (reset) {
            resetDTW.add(DTPrefix.getTimedWord());
        }
        ResetLogicTimeWord RLTW = normalizer.normalizeRLT(TimeWordHelper.DTW2RLTW(DTPrefix.getTimedWord(), resetDTW));
        if (RLTW.isBeyondMaxValWord()) {
            // 超过最大上限的猜测直接抛弃
            return false;
        }
        Prefix<ResetLogicTimeWord> RLTPrefix = new Prefix<>(RLTW, DTPrefix.getAnswer());

        Map<ResetLogicTimeWord, Boolean> recorderElement = new HashMap<>(dtwPrefixes.size());

        for (int i = 0; i< rltwPrefixes.size(); i++) {
            Prefix<ResetLogicTimeWord> baseRLTWPrefix = rltwPrefixes.get(i);
            Prefix<DelayTimeWord> baseDTWPrefix = dtwPrefixes.get(i);
            boolean distinguished = baseTable.getTableRecorder().getDistinguishResult(DTPrefix, baseDTWPrefix, resetDTW);
            // 前置检查
            if (ResetLogicTimeWord.isLogicTimeEq(baseRLTWPrefix.getTimedWord(), RLTPrefix.getTimedWord())) {
                if (!ResetLogicTimeWord.isResetEq(baseRLTWPrefix.getTimedWord(), RLTPrefix.getTimedWord())) {
                    // 1.1 重置检查：如果存在两个tw 正则化后属于同一region，但是重置信息不同，则说明当前猜测是错误的
                    return false;
                }
                if (distinguished) {
                    // 1.2 正则化检查: 如果存在正则化后属于同一region却可以区分的两个timedWord, 则说明当前猜测是错误的
                    return false;
                }
                // 新增前缀和已有前缀转换后的逻辑时间region相同，不需要后续更新
                rltwPrefixes.add(baseRLTWPrefix);
                return true;
            }
            recorderElement.put(baseRLTWPrefix.getTimedWord(), distinguished);
        }

        // close检查
        boolean needMoveToS = true;
        for (Prefix<ResetLogicTimeWord> s : S) {
            if (!recorderElement.get(s.getTimedWord())) {
                needMoveToS = false;
                break;
            }
        }

        if (needMoveToS) {
            S.add(RLTPrefix);
        } else {
            R.add(RLTPrefix);
        }

        // set
        rltwPrefixes.add(RLTPrefix);
        recorder.appendNewElement(RLTPrefix.getTimedWord(), recorderElement);
        return true;
    }
}
