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

package pascal.taie.analysis.dataflow.solver;

import pascal.taie.analysis.dataflow.analysis.DataflowAnalysis;
import pascal.taie.analysis.dataflow.fact.DataflowResult;
import pascal.taie.analysis.graph.cfg.CFG;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.function.Predicate;

class IterativeSolver<Node, Fact> extends Solver<Node, Fact> {

    public IterativeSolver(DataflowAnalysis<Node, Fact> analysis) {
        super(analysis);
    }

    @Override
    protected void doSolveForward(CFG<Node> cfg, DataflowResult<Node, Fact> result) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doSolveBackward(CFG<Node> cfg, DataflowResult<Node, Fact> result) {
        // 对于LVA，Fact是SetFact<Var>
        // TODO - finish me //DONE
        boolean changed;
        var exit = cfg.getExit();

        do{ // 进行多轮处理以handle环
            changed = false;
            var visited = new HashSet<Node>(); //防止陷入环
            visited.add(exit);
            var WL = new ArrayDeque<>(cfg.getPredsOf(exit));

            while(!WL.isEmpty()){
                var node = WL.removeFirst(); // 本次处理的NODE // 居然同时有pop和removeFirst。。
                visited.add(node);

                var OUT = result.getOutFact(node);
                // 1. 根据本次处理的节点的后继，合并生成该节点本次的OUT
                cfg.getSuccsOf(node).forEach(succ -> analysis.meetInto(result.getInFact(succ), OUT));

                // 2. 根据对OUT施以transfer，生成IN
                changed |= analysis.transferNode(node, result.getInFact(node), result.getOutFact(node));

                // 3. 将本节点的，本轮没有处理过的直接前驱加入WL。
                cfg.getPredsOf(node).stream().filter(Predicate.not(visited::contains)).forEach(WL::add);
            } //(!WL.isEmpty())
        }while(changed);
    }
}
