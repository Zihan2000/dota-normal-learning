package modelLearning.normal.learner.delayTable.guess;

import com.microsoft.z3.*;
import lombok.Getter;
import modelLearning.Config;
import modelLearning.frame.Learner;
import modelLearning.normal.learner.delayTable.Prefix;
import ta.Clock;
import ta.TaLocation;
import ta.TaTransition;
import ta.TimeGuard;
import ta.ota.DOTA;
import ta.ota.DOTAUtil;
import ta.ota.ResetLogicAction;
import ta.ota.ResetLogicTimeWord;

import java.util.*;

public class GuessTable {
    @Getter
    protected final Set<Prefix<ResetLogicTimeWord>> S;
    @Getter
    protected final Set<Prefix<ResetLogicTimeWord>> R;
    protected final GuessTableRecorder recorder;
    protected final Set<String> sigma;

    public GuessTable(Set<Prefix<ResetLogicTimeWord>> s, Set<Prefix<ResetLogicTimeWord>> r, GuessTableRecorder recorderMap, Set<String> sigma) {
        S = s;
        R = r;
        this.recorder = recorderMap;
        this.sigma = sigma;
    }

    public GuessTable deepClone() {
        Set<Prefix<ResetLogicTimeWord>> copyS = new HashSet<>(S);
        Set<Prefix<ResetLogicTimeWord>> copyR = new HashSet<>(R);
        GuessTableRecorder copyRecorder = recorder.deepClone();
        return new GuessTableBuilder(copyS, copyR, copyRecorder, sigma);
    }

    public void makeClosed() {
        List<Prefix<ResetLogicTimeWord>> tobeMovedPrefixes = new ArrayList<>();
        List<Prefix<ResetLogicTimeWord>> tobeDeletePrefixes = new ArrayList<>();
        // check closed
        checkNextPrefix:
        for (Prefix<ResetLogicTimeWord> rPrefix : R) {
            for (Prefix<ResetLogicTimeWord> sPrefix : S) {
                if (!recorder.getDistinguishResult(sPrefix.getTimedWord(), rPrefix.getTimedWord())) {
                    if (sPrefix.equals(rPrefix)) {
                        tobeDeletePrefixes.add(rPrefix);
                    }
                    continue checkNextPrefix;
                }
            }
            for (Prefix<ResetLogicTimeWord> newSPrefix : tobeMovedPrefixes) {
                if (!recorder.getDistinguishResult(newSPrefix.getTimedWord(), rPrefix.getTimedWord())) {
                    continue checkNextPrefix;
                }
            }

            // S区所有前缀都和当前前缀区分，则这个前缀需要加到S区
            tobeMovedPrefixes.add(rPrefix);
        }

        // make closed
        for (Prefix<ResetLogicTimeWord> p : tobeMovedPrefixes) {
            R.remove(p);
            S.add(p);
        }

        for (Prefix<ResetLogicTimeWord> p : tobeDeletePrefixes) {
            R.remove(p);
        }
    }


    /**
     * 求解并分配location，无解返回 null
     */
    public Map<ResetLogicTimeWord, Integer> solveLocation(boolean useZ3) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (useZ3) {
            return solveLocationWithZ3();
        }
        return solveLocationPlain();
    }

    private Map<ResetLogicTimeWord, Integer> solveLocationPlain() throws InterruptedException {
        throw new RuntimeException("未实现，请打开 useZ3Solver的开关");
    }

    private Map<ResetLogicTimeWord, Integer> solveLocationWithZ3() throws InterruptedException {
        HashMap<String, String> cfg = new HashMap<>(); // cfg 上下文无关文法
        cfg.put("model", "true");
        try (Context ctx = new Context(cfg)) {
            Solver solver = ctx.mkSolver(); // Creates a new (incremental) solver
            Map<ResetLogicTimeWord, IntExpr> valMap = new HashMap<>();
            int idx = 0;
            // S区的前缀直接分配location值
            for (Prefix<ResetLogicTimeWord> s : S) {
//                S区的每个前缀都分配一个int值作为location，并将其时间字字符串作为int变量名，等于location值
                idx++;
//                IntExpr: Int expressions; ctx.mkIntConst: Creates an integer constant.
                IntExpr wExpr = ctx.mkIntConst(s.toString()); // 时间字的字符表达，例如|(a,0.0,n) BooleanAnswer(accept=false, sink=true)|
//                ctx.mkEq: Creates the equality; ctx.mkInt: Create an integer numeral.
                solver.add(ctx.mkEq(wExpr, ctx.mkInt(idx))); // Assert a multiple constraints into the solver.
                valMap.put(s.getTimedWord(), wExpr);
            }

            // R区的前缀与S区的某一个前缀location值相同
//            即满足封闭性
            for (Prefix<ResetLogicTimeWord> r : R) {
                IntExpr wExpr = ctx.mkIntConst(r.toString());
//                ctx.mkGe: >=; ctx.mkLe: <=
                solver.add(ctx.mkGe(wExpr, ctx.mkInt(1)), ctx.mkLe(wExpr, ctx.mkInt(idx)));
                valMap.put(r.getTimedWord(), wExpr);
            }

            // 前缀定义限制：相互区分的前缀不能属于同一个location
            for (Prefix<ResetLogicTimeWord> r : R) {
                ResetLogicTimeWord curRLTW = r.getTimedWord();
                IntExpr wExpr = valMap.get(curRLTW);
                Map<ResetLogicTimeWord, Boolean> distinguishMap = recorder.getDistinguishMap(curRLTW);
                for (Map.Entry<ResetLogicTimeWord, Boolean> entry : distinguishMap.entrySet()) {
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                    if (!entry.getValue()) {
                        continue;
                    }
                    ResetLogicTimeWord distinguishedRLTW = entry.getKey();
                    IntExpr distinguishedExpr = valMap.get(distinguishedRLTW);
                    solver.add(ctx.mkNot(ctx.mkEq(wExpr, distinguishedExpr))); // 约束：和每个可区分前缀都不属于同一个location
                }
            }

            List<Prefix<ResetLogicTimeWord>> prefixes = new ArrayList<>(S.size() + R.size());
            prefixes.addAll(S);
            prefixes.addAll(R);
            for (int i = 0; i < prefixes.size(); i++) {
                for (int j = i + 1; j < prefixes.size(); j++) {
                    Prefix<ResetLogicTimeWord> r = prefixes.get(i);
                    Prefix<ResetLogicTimeWord> prefix = prefixes.get(j);
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                    if (prefix == r) continue;
                    ResetLogicTimeWord rW = r.getTimedWord();
                    ResetLogicTimeWord prefixW = prefix.getTimedWord();
                    if (prefixW.isEmpty() || rW.isEmpty() || !rW.getLastResetAction().regionEq(prefixW.getLastResetAction())) {
                        continue;
                    }
                    ResetLogicTimeWord beforeRW = rW.subWord(0, rW.size() - 1);
                    ResetLogicTimeWord beforePrefixW = prefixW.subWord(0, prefixW.size() - 1);

                    if (recorder.getDistinguishResult(beforeRW, beforePrefixW)) {
                        continue;
                    }
                    IntExpr beforeRWExpr = valMap.get(beforeRW);
                    IntExpr beforePrefixWExpr = valMap.get(beforePrefixW);
                    if (rW.getLastResetAction().isReset() != prefixW.getLastResetAction().isReset()) {
                        // 确定性check
                        solver.add(ctx.mkNot(ctx.mkEq(beforeRWExpr, beforePrefixWExpr)));
                        continue;
                    }

                    // 一致性检查
                    if (recorder.getDistinguishResult(rW, prefixW)) {
                        continue;
                    }
                    IntExpr rWExpr = valMap.get(rW);
                    IntExpr prefixWExpr = valMap.get(prefixW);
                    solver.add(ctx.mkImplies(ctx.mkEq(beforeRWExpr, beforePrefixWExpr), ctx.mkEq(rWExpr, prefixWExpr)));
                }
            }

            //求解
            Status checkStatus = solver.check();
            if (checkStatus == Status.UNSATISFIABLE) {
                return null;
            }

            if (checkStatus == Status.SATISFIABLE) {
                Model model = solver.getModel();
                Map<ResetLogicTimeWord, Integer> result = new HashMap<>();
                for (Map.Entry<ResetLogicTimeWord, IntExpr> entry : valMap.entrySet()) {
                    int locationId = Integer.parseInt(model.eval(entry.getValue(), false).toString());
                    result.put(entry.getKey(), locationId);
                }
                return result;
            }

            throw new RuntimeException("Z3求解异常！！！！！");
        }
    }

    public DOTA buildHypothesis(String name) {
        Map<ResetLogicTimeWord, Integer> locationSolver = null;
        try {
            locationSolver = solveLocation(true);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (locationSolver == null) {
            return null;
        }
        return buildHypothesis(name, locationSolver);
    }

    // 2. 创建hypothesis
    public DOTA buildHypothesis(String name, Map<ResetLogicTimeWord, Integer> locationSolver) {
        Map<Integer, TaLocation> id2Location = new HashMap<>(S.size() + R.size());
        ResetLogicTimeWord emptyWord = ResetLogicTimeWord.emptyWord();
        // 1. 构建location
        for (Prefix<ResetLogicTimeWord> s : S) {
            ResetLogicTimeWord sWord = s.getTimedWord();
            Integer locationId = locationSolver.get(sWord);
            if (locationId == null) {
                throw new RuntimeException("不完整的location分配，缺少前缀" + sWord);
            }
            boolean init = sWord.equals(emptyWord);
            boolean accept = s.getAnswer().isAccept();
            String locationName = s.getAnswer().isSink() ? TaLocation.SINK_NAME : locationId.toString();
            TaLocation location = new TaLocation(locationId.toString(), locationName, init, accept);
            id2Location.put(locationId, location);
        }
        for (Prefix<ResetLogicTimeWord> r : R) {
            ResetLogicTimeWord rWord = r.getTimedWord();
            Integer locationId = locationSolver.get(rWord);
            if (locationId == null) {
                throw new RuntimeException("不完整的location分配，缺少前缀" + rWord);
            }
        }

        // 2. DOTA的唯一clock
        Clock clock = new Clock("c1");

        // 2. 构建transition
        Set<TaTransition> transitions = new HashSet<>();
        for (Map.Entry<ResetLogicTimeWord, Integer> entry : locationSolver.entrySet()) {
            ResetLogicTimeWord prefix = entry.getKey();
            int locationId = entry.getValue();
            if (prefix.isEmpty()) {
                continue;
            }
            ResetLogicTimeWord pre = prefix.subWord(0, prefix.size() - 1);
            if (!locationSolver.containsKey(pre)) {
                throw new RuntimeException("not prefix closure");
            }
            int preLocationId = locationSolver.get(pre);
            TaLocation sourceLocation = id2Location.get(preLocationId);
            TaLocation targetLocation = id2Location.get(locationId);
            ResetLogicAction resetAction = prefix.getLastResetAction();
            String symbol = resetAction.getSymbol();
            TimeGuard timeGuard = TimeGuard.bottomGuard(resetAction.logicTimedAction());
            Map<Clock, TimeGuard> clockTimeGuardMap = new HashMap<>();
            clockTimeGuardMap.put(clock, timeGuard);
            Set<Clock> resetClockSet = new HashSet<>();
            if (resetAction.isReset()) {
                resetClockSet.add(clock);
            }
            TaTransition transition = new TaTransition(sourceLocation, targetLocation, symbol, clockTimeGuardMap, resetClockSet);
            transitions.add(transition);
        }

        DOTA evidenceDOTA = new DOTA(name, sigma, new ArrayList<>(id2Location.values()), new ArrayList<>(transitions), clock);
        Learner.evidenceToDOTA(evidenceDOTA);
        DOTAUtil.completeDOTA(evidenceDOTA);
        // todo 仅用于debug, 检查hypothesis的合法性
        if (Config.debug) {
            for (TaTransition transition : evidenceDOTA.getTransitions()) {
                TimeGuard guard = transition.getTimeGuard(clock);
                if (!guard.check()) {
                    throw new RuntimeException("构造出了非法的guard!!!!");
                }
            }
        }
        return evidenceDOTA;
    }
}
