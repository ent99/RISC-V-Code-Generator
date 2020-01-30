
import java.util.ArrayList;
import java.util.List;

class MyCodegen implements Codegen {
    List<String> riscv;
    int ifLabel = 0;
    int whileLabel = 0;
    int repeatLabel = 0;
    
    @Override
    public String codegen(Program p) throws CodegenException {
        riscv = new ArrayList<String>();
        
        for(Declaration d : p.decls) {
            genDecl(d);
        }
        
        return listToString(riscv);
    }

    private String listToString(List list) {
        String listString = "";
        for (Object s : list) {
            listString += s + "\n";
        }
        return listString;
    }
    
    private void genDecl(Declaration d) throws CodegenException {
        int sizeAR = (2 + d.numOfArgs) * 4;
        riscv.add(d.id + "_entry:");
        riscv.add("mv s0 sp");
        riscv.add("sw ra 0(sp)");
        riscv.add("addi sp sp -4");
        
        genExp(d.body);
        
        riscv.add("lw ra 4(sp)");
        riscv.add("addi sp sp " + sizeAR);
        riscv.add("lw s0 0(sp)");
        riscv.add("li a7 10");
        riscv.add("ecall");
        riscv.add("jr ra");
    }

    

    private void genExp(Exp e) throws CodegenException {
        if (e instanceof IntLiteral) {
            riscv.add("li a0 " + ((IntLiteral) e).n);
        }
        
        else if (e instanceof Variable) {
            int offset = 4 * ((Variable) e).x;
            riscv.add("lw a0 " + offset + "(s0)");
        }
        
        else if (e instanceof If) {
            ifLabel++;
            String elseBranch = "else_" + ifLabel;
            String thenBranch = "then_" + ifLabel;
            String exitLabel = "exit_" + ifLabel;
            
            genExp(((If) e).l);
            
            riscv.add("sw a0 0(sp)");
            riscv.add("addi sp sp -4");
            
            genExp(((If) e).r);
            
            riscv.add("lw t1 4(sp)");
            riscv.add("addi sp sp 4");
            
            genComp(((If) e).comp, thenBranch);
            
            riscv.add(elseBranch + ":");
            
            genExp(((If) e).elseBody);
            
            riscv.add("b " + exitLabel);
            riscv.add(thenBranch + ":");
            
            genExp(((If) e).thenBody);
            
            riscv.add(exitLabel + ":");
        }
        
        else if (e instanceof Binexp) {
            genExp(((Binexp) e).l);
            
            riscv.add("sw a0 0(sp)");
            riscv.add("addi sp sp -4");
            
            genExp(((Binexp) e).r);
            
            riscv.add("lw t1 4(sp)");
            riscv.add("addi sp sp 4");
            
            genBinop(((Binexp) e).binop);
        }
        
        else if (e instanceof Invoke) {
            String f = ((Invoke) e).name;
            List args = ((Invoke) e).args;
            
            riscv.add("sw s0 0(sp)");
            riscv.add("addi sp sp -4");
            
            for (int i = args.size() - 1; i >= 0; i--) {
                genExp((Exp) args.get(i));
                riscv.add("sw a0 0(sp)");
                riscv.add("addi sp sp -4");
            }
            
            riscv.add("jal " + f + "_entry");
        }
        
        else if (e instanceof Assign) {
            int offset = 4 * ((Assign) e).x;
            genExp(((Assign) e).e);
            riscv.add("sw a0 " + offset + "(s0)");
        }
        
        else if (e instanceof Seq) {
            genExp(((Seq) e).l);
            genExp(((Seq) e).r);
        }
        
        else if (e instanceof Skip) {
            riscv.add("nop");
        }
        
        else if (e instanceof Break) {
            riscv.add("jr t2");
        }
        
        else if (e instanceof Continue) {
            riscv.add("jr t3");
        }
        
        else if (e instanceof While) {
            whileLabel++;
            String loop = "loop_" + whileLabel;
            String loopBody = "loop_body_" + whileLabel;
            String loopExit = "loop_exit_" + whileLabel;
            
            riscv.add(loop + ":");
            riscv.add("la t2, " + loopExit);
            riscv.add("sw t2, 0(sp)");
            riscv.add("addi sp sp -4");
            riscv.add("la t3, " + loop);
            riscv.add("sw t3, 0(sp)");
            riscv.add("addi sp sp -4");
            
            genExp(((While) e).l);
            
            riscv.add("sw a0 0(sp)");
            riscv.add("addi sp sp -4");
            
            genExp(((While) e).r);
            
            riscv.add("lw t1 4(sp)");
            riscv.add("addi sp sp 4");
            
            genComp(((While) e).comp, loopBody);
            
            riscv.add("j " + loopExit);
            riscv.add(loopBody + ":");
            
            genExp(((While) e).body);
            
            riscv.add("j " + loop);
            riscv.add(loopExit + ":");
            riscv.add("lw t2 4(sp)");
            riscv.add("addi sp sp 4");
            riscv.add("lw t3 4(sp)");
            riscv.add("addi sp sp 4");
        }
        
        else if (e instanceof RepeatUntil) {
            repeatLabel++;
            String repeat = "repeat_" + repeatLabel;
            String repeatExit = "repeat_exit_" + repeatLabel;
            
            riscv.add(repeat + ":");
            riscv.add("la t2, " + repeatExit);
            riscv.add("sw t2, 0(sp)");
            riscv.add("addi sp sp -4");
            riscv.add("la t3, " + repeat);
            riscv.add("sw t3, 0(sp)");
            riscv.add("addi sp sp -4");
            
            genExp(((While) e).body);
            genExp(((While) e).l);
            
            riscv.add("sw a0 0(sp)");
            riscv.add("addi sp sp -4");
            
            genExp(((While) e).r);
            
            riscv.add("lw t1 4(sp)");
            riscv.add("addi sp sp 4");
            
            genComp(((While) e).comp, repeatExit);
            
            riscv.add("j " + repeat);
            riscv.add(repeatExit + ":");
            riscv.add("lw t2 4(sp)");
            riscv.add("addi sp sp 4");
            riscv.add("lw t3 4(sp)");
            riscv.add("addi sp sp 4");
        }
        
        else {
            throw new CodegenException("");
        }
    }
    
    private void genComp(Comp c, String label) {
        if (c instanceof Equals) {
            riscv.add("beq a0 t1 " + label);
        }
        
        else if (c instanceof Less) {
            riscv.add("blt t1 a0 " + label);
        }
        
        else if (c instanceof Greater) {
            riscv.add("bgt t1 a0 " + label);
        }
        
        else if (c instanceof LessEq) {
            riscv.add("ble t1 a0 " + label);
        }
        
        else if (c instanceof GreaterEq) {
            riscv.add("bge t1 a0 " + label);
        }
    }

    private void genBinop(Binop b) {
        if (b instanceof Plus) {
            riscv.add("add a0 t1 a0");
        }
        
        else if (b instanceof Minus) {
            riscv.add("sub a0 t1 a0");
        }
        
        else if (b instanceof Times) {
            riscv.add("mul a0 t1 a0");
        }
        
        else if (b instanceof Div) {
            riscv.add("div a0 t1 a0");
        }
    }
    
}