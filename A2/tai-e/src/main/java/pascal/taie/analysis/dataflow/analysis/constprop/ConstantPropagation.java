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

package pascal.taie.analysis.dataflow.analysis.constprop;

import pascal.taie.analysis.dataflow.analysis.AbstractDataflowAnalysis;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.config.AnalysisConfig;

import pascal.taie.ir.exp.*;
import pascal.taie.ir.stmt.DefinitionStmt;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.type.PrimitiveType;
import pascal.taie.language.type.Type;
import pascal.taie.util.AnalysisException;



public class ConstantPropagation extends
        AbstractDataflowAnalysis<Stmt, CPFact> {

    public static final String ID = "constprop";

    public ConstantPropagation(AnalysisConfig config) {
        super(config);
    }

    @Override
    public boolean isForward() {
        return true;
    }

    @Override
    public CPFact newBoundaryFact(CFG<Stmt> cfg) {
        // TODO - finish me
        var fact = new CPFact();
        cfg.getIR().getParams().forEach(p-> {
            if(canHoldInt(p)) fact.update(p, Value.getNAC());});
        return fact;
    }

    @Override
    public CPFact newInitialFact() {
        // TODO - finish me
        return new CPFact();
    }

    @Override
    public void meetInto(CPFact fact, CPFact target) {
        // TODO - finish me
        // from fact to target
        for(var key : fact.keySet()){
            var v1 = fact.get(key);
            var v2 = target.get(key);
            var meet_v = meetValue(v1, v2);
            target.update(key, meet_v);
        }
    }

    /**
     * Meets two Values.
     */
    public Value meetValue(Value v1, Value v2) {
        // TODO - finish me
        if(v1.isNAC() || v2.isNAC()){
            return Value.getNAC();
        }
        // v1 and v2 can be constant or undef
        if(v1.isUndef()) return v2;
        if(v2.isUndef()) return v1;
        // v1 and v2 must be constant now
        if(v1.equals(v2)) return v1;
        // both constant but not equal
        return Value.getNAC();
    }

    @Override
    public boolean transferNode(Stmt stmt, CPFact in, CPFact out) {
        // TODO - finish me

        if (!(stmt instanceof DefinitionStmt<? extends LValue,? extends RValue> dStmt)){
            // not definition at all
            return out.copyFrom(in);
        }
        if(dStmt.getLValue() == null ||
                !(dStmt.getLValue() instanceof Var varLValue)
                ||!canHoldInt(varLValue)){
            // this is an invoke or the left is not int
            return out.copyFrom(in);
        }
        var value = evaluate(dStmt.getRValue(), in);
        // do not short-circuit here
        return out.copyFrom(in) | out.update(varLValue, value);
    }

    /**
     * @return true if the given variable can hold integer value, otherwise false.
     */
    public static boolean canHoldInt(Var var) {
        Type type = var.getType();
        if (type instanceof PrimitiveType) {
            switch ((PrimitiveType) type) {
                case BYTE:
                case SHORT:
                case INT:
                case CHAR:
                case BOOLEAN:
                    return true;
            }
        }
        return false;
    }

    /**
     * Evaluates the {@link Value} of given expression.
     *
     * @param exp the expression to be evaluated
     * @param in  IN fact of the statement
     * @return the resulting {@link Value}
     */
    public static Value evaluate(Exp exp, CPFact in) {
        // TODO - finish me
        if(exp instanceof Var V){ // x = b
            return in.get(V);
        }
        if(exp instanceof IntLiteral I){ // x = 3
            return Value.makeConstant(I.getValue());
        }
        if(exp instanceof BinaryExp binary){
            var op1 = binary.getOperand1();
            var op2 = binary.getOperand2();
            if(in.get(op1).isUndef() || in.get(op2).isUndef()){
                // one of op is undef. can't decide now
                return Value.getUndef();
            }
            if(in.get(op1).isNAC() || in.get(op2).isNAC()){
                // one of op is nac. result must be nac
                // seems that NAC/0 and NAC%0 should be undef //FUCKFUCKFUCK
                if(in.get(op1).isNAC() && in.get(op2).isConstant()
                    && in.get(op2).getConstant() == 0 &&
                    exp instanceof ArithmeticExp A &&
                        (A.getOperator() == ArithmeticExp.Op.DIV || A.getOperator() == ArithmeticExp.Op.REM))
                    return Value.getUndef();
                return Value.getNAC();
            }
            if(exp instanceof ArithmeticExp A){ // x = a + b
                // op1 and op2 must be var that hold int now
                var op1_v = in.get(op1).getConstant();
                var op2_v = in.get(op2).getConstant();
                // op1 and op2 must be constant now
                if (op2_v == 0 && (A.getOperator() == ArithmeticExp.Op.DIV || A.getOperator() == ArithmeticExp.Op.REM))
                    return Value.getUndef();
                int result = switch (A.getOperator().toString()) {
                    case "+" -> op1_v + op2_v;
                    case "-" -> op1_v - op2_v;
                    case "*" -> op1_v * op2_v;
                    case "/" -> op1_v / op2_v;
                    case "%" -> op1_v % op2_v;
                    default -> throw new AnalysisException("unknown arithop" + A.getOperator().toString());
                };

                return Value.makeConstant(result);
            }
            else if(exp instanceof ConditionExp C){ // x = a == b
                var op1_v = in.get(op1).getConstant();
                var op2_v = in.get(op2).getConstant();
                boolean result = switch (C.getOperator().toString()) {
                    case "==" -> op1_v == op2_v;
                    case "!=" -> op1_v != op2_v;
                    case ">"  -> op1_v > op2_v;
                    case "<"  -> op1_v < op2_v;
                    case ">=" -> op1_v >= op2_v;
                    case "<=" -> op1_v <= op2_v;
                    default -> throw new AnalysisException("unknown condition op: " + C.getOperator().toString());
                };
                return Value.makeConstant(result ? 1 : 0);
            }
            else if(exp instanceof ShiftExp S){
                var op1_v = in.get(op1).getConstant();
                var op2_v = in.get(op2).getConstant();
                int result = switch (S.getOperator().toString()) {
                    case "<<"  -> op1_v << op2_v;
                    case ">>"  -> op1_v >> op2_v;
                    case ">>>" -> op1_v >>> op2_v;
                    default -> throw new AnalysisException("unknown shift op: " + S.getOperator().toString());
                };
                return Value.makeConstant(result);
            }
            else if(exp instanceof BitwiseExp B){
                var op1_v = in.get(op1).getConstant();
                var op2_v = in.get(op2).getConstant();
                int result = switch (B.getOperator().toString()) {
                    case "&" -> op1_v & op2_v;
                    case "|" -> op1_v | op2_v;
                    case "^" -> op1_v ^ op2_v;
                    default -> throw new AnalysisException("unknown bitwise op: " + B.getOperator().toString());
                };
                return Value.makeConstant(result);
            }
        }
        return Value.getNAC(); // other things like invoke and so on
    }
}
