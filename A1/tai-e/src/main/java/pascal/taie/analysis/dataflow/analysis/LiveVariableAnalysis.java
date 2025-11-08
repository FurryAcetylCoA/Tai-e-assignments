/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.dataflow.analysis;

import pascal.taie.analysis.dataflow.fact.SetFact;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Stmt;

/**
 * Implementation of classic live variable analysis.
 */
public class LiveVariableAnalysis extends
        AbstractDataflowAnalysis<Stmt, SetFact<Var>> {

    public static final String ID = "livevar";

    public LiveVariableAnalysis(AnalysisConfig config) {
        super(config);
    }

    @Override
    public boolean isForward() {
        return false;
    }

    /// 边界节点（反向分析时为退出点）的初始条件
    @Override
    public SetFact<Var> newBoundaryFact(CFG<Stmt> cfg) {
        // TODO - finish me //DONE
        // LVA的边界节点的所有变量都是KILL的状态
        return new SetFact<>();
    }

    ///  不是边界节点的其他节点的初始状态
    @Override
    public SetFact<Var> newInitialFact() {
        // TODO - finish me //DONE
        // 在初始状态下，所有内节点的OUT/IN的变量都是KILL的状态
        return new SetFact<>();

    }

    ///  meet策略
    ///  将fact集合并入target集合
    @Override
    public void meetInto(SetFact<Var> fact, SetFact<Var> target) {
        // TODO - finish me //DONE
        // 有一个用到了也是用到了
        target.union(fact);
    }

    /// Node策略
    /// 当in修改时，返回true
    @Override
    public boolean transferNode(Stmt stmt, SetFact<Var> in, SetFact<Var> out) {
        // TODO - finish me // DONE
        // 打断点时注意，这里有多个线程！
        // 有一个HIDDEN CASE过不了。不确定是什么
        // SetFact大概算是一种Set，只要一个Var在里面， 就算SET了
        // 一个Var不在里面，就算KILL了
        var inNew = out.copy();

        // 如果重新定义了，就先KILL  // java的这个语法比C++舒服多了
        // 重新定义指的是Def侧有东西，且是Var（虽然不知道还能是啥别的。。）
        stmt.getDef().filter(Var.class::isInstance)
                     .map(Var.class::cast)
                     .ifPresent(inNew::remove);

        // 如果用到了，就SET
        stmt.getUses().stream()
                .filter(Var.class::isInstance) /*RValue除了Var那可多了去了*/
                .map(Var.class::cast)
                .forEach(inNew::add);

        var changed = !inNew.equals(in);
        in.set(inNew);
        return changed;
    }
}
