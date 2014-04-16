package compiler.translator;

import compiler.parser.PascalParser.*;
import compiler.translator.scope.Scope;
import compiler.translator.scope.TranslateScope;
import compiler.translator.type.ArrayType;
import compiler.translator.type.DataType;
import compiler.translator.type.PrimitiveType;
import compiler.translator.type.Range;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.io.Console;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author Arkady Rost
 */
public class TranslateVisitor {
    private ClassWriter cw;
    private MethodVisitor mv;
    private TranslateScope scope;

    public Scope visit(ProgramContext ctx) {
        if (ctx == null)
            throw new IllegalArgumentException("ctx is null");
        refresh();
        visitProgram(ctx);
        scope.setByteCode(cw.toByteArray());
        return scope;
    }

    private void refresh() {
        cw = null;
        mv = null;
        scope = new TranslateScope();
    }

    private void visitProgram(ProgramContext ctx) {
        cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String name = capitalize(ctx.ID().getText());
        scope.setClassName(name);
        cw.visit(V1_7, ACC_PUBLIC, name, null, "java/lang/Object", null);

        visitBody(ctx.body());
        cw.visitEnd();
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private void visitBody(BodyContext ctx) {
        visitGlobalVarDeclarations(ctx.varDeclarations());
        visitFunctionDeclarations(ctx.functionDeclarations());

        // entry point
        mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);

        visitBlock(ctx.block());

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void visitGlobalVarDeclarations(VarDeclarationsContext ctx) {
        for (VarDeclarationContext vctx : ctx.varDeclaration())
            visitGlobalVarDeclaration(vctx);
        createConstructors();
    }

    private void visitGlobalVarDeclaration(VarDeclarationContext ctx) {
        DataType type = getType(ctx.type());
        for (TerminalNode id : ctx.ID())
            declareField(id.getText(), type);
    }

    private void createConstructors() {
        createInstanceConstructor();
        createClassConstructor();
    }

    private void createClassConstructor() {
        mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        for (Map.Entry<String, DataType> var : scope.getGlobalVariables().entrySet()) {
            if (var.getValue().isPrimitive())
                continue;
            ArrayType type = (ArrayType)var.getValue();
            initializeArray(type);
            mv.visitFieldInsn(PUTSTATIC, scope.getClassName(), var.getKey(), Type.getDescriptor(int[].class));
        }
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void initializeArray(ArrayType type) {
        mv.visitLdcInsn(type.getSize());
        mv.visitIntInsn(NEWARRAY, T_INT);
    }

    private void createInstanceConstructor() {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static DataType getType(TypeContext ctx) {
        List<RangeContext> rctxList = ctx.range();
        if (rctxList.isEmpty()) {
            return getPrimitiveType(ctx.getText());
        }
        Range[] dimensions = new Range[rctxList.size()];
        int i = 0;
        for (RangeContext rctx : rctxList) {
            int from = Integer.parseInt(rctx.NUMBER(0).getText());
            int to = Integer.parseInt(rctx.NUMBER(1).getText());
            dimensions[i++] = new Range(from, to);
        }
        return new ArrayType(getPrimitiveType(ctx.PRIMITIVE_TYPE().getText()), dimensions);
    }

    private static PrimitiveType getPrimitiveType(String type) {
        switch (type) {
            case "integer": return PrimitiveType.INTEGER;
            case "boolean": return PrimitiveType.BOOLEAN;
            default:
                throw new CompileException("Unknown primitive type: " + type);
        }
    }


    private void declareField(String name, DataType type) {
        scope.addGlobalVariable(name, type);
        String descriptor = type.getType().getDescriptor();
        Object value = type.isPrimitive() ? 0 : null;
        cw.visitField(ACC_PUBLIC | ACC_STATIC, name, descriptor, null, value).visitEnd();
    }

    private void visitFunctionDeclarations(FunctionDeclarationsContext ctx) {
        for (FunctionDeclarationContext fctx : ctx.functionDeclaration())
            visitFunctionDeclaration(fctx);
    }

    private void visitFunctionDeclaration(FunctionDeclarationContext ctx) {
        String name = ctx.ID().getText();
        scope.setMethodName(name);
        DataType retType = getType(ctx.type());
        scope.setMethodType(retType);
        DataType[] argType = new DataType[ctx.varDeclaration().size()];
        int i = 0;
        for (VarDeclarationContext pctx : ctx.varDeclaration()) {
            DataType type = getType(pctx.type());
            for (TerminalNode id : pctx.ID()) {
                if (scope.isLocalVariable(id.getText()))
                    throw new CompileException(String.format("Duplicate parameter %s in declaration %s", id.getText(), ctx.getText()));
                if (name.equals(id.getText()))
                    throw new CompileException(String.format("Illegal parameter %s name in declaration %s.", id.getText(), ctx.getText()));
                scope.addLocalVariable(id.getText(), type);
            }
            argType[i++] = type;
        }
        if (scope.isFunctionDeclared(name, argType))
            throw new CompileException(String.format("Function with same signature as %s already declared.", ctx.getText()));
        scope.declareFunction(name, retType, argType);
        mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, name, Utils.getFunctionDescriptor(retType, argType), null, null);

        visitLocalVariableDeclarations(ctx.varDeclarations());
        visitBlock(ctx.block());

        mv.visitMaxs(0, 0);
        mv.visitEnd();
        scope.setMethodName("");
        scope.refreshLocalVariables();
    }

    private void visitLocalVariableDeclarations(VarDeclarationsContext ctx) {
        for (VarDeclarationContext vctx : ctx.varDeclaration())
            visitLocalVariableDeclaration(vctx);
    }

    private void visitLocalVariableDeclaration(VarDeclarationContext ctx) {
        DataType type = getType(ctx.type());
        for (TerminalNode var : ctx.ID()) {
            if (var.getText().equals(scope.getMethodName()))
                throw new CompileException(String.format("Illegal local variable name %s in %s.", var.getText(), ctx.getText()));
            int index = scope.addLocalVariable(var.getText(), type);
            if (type instanceof ArrayType) {
                initializeArray((ArrayType) type);
                mv.visitVarInsn(ASTORE, index);
            }
        }
    }

    private void visitStatement(StatementContext ctx) {
        if (ctx.ifStatement() != null) {
            visitIf(ctx.ifStatement());
        } else if (ctx.forStatement() != null) {
            visitFor(ctx.forStatement());
        } else if (ctx.whileStatement() != null) {
            visitWhile(ctx.whileStatement());
        } else if (ctx.assignmentStatement() != null) {
            visitAssignment(ctx.assignmentStatement());
        } else if (ctx.block() != null) {
            visitBlock(ctx.block());
        } else if (ctx.functionCall() != null) {
            visitFunctionCall(ctx.functionCall());
        } else if (ctx.readStatement() != null) {
            visitRead(ctx.readStatement());
        } else if (ctx.writeStatement() != null) {
            visitWrite(ctx.writeStatement());
        } else if (ctx.breakStatement() != null) {
            visitBreak(ctx.breakStatement());
        } else if (ctx.continueStatement() != null) {
            visitContinue(ctx.continueStatement());
        } else {
            throw new CompileException("Unsupported statement: " + ctx.getText());
        }
    }

    private void visitIf(IfStatementContext ctx) {
        verifyType(visitExpression(ctx.expression()), PrimitiveType.BOOLEAN, ctx);
        Label endLabel = new Label();
        if (ctx.elsePart() == null) {
            mv.visitJumpInsn(IFEQ, endLabel);
            visitStatement(ctx.statement());
        } else {
            Label elseLabel = new Label();
            mv.visitJumpInsn(IFEQ, elseLabel);
            visitStatement(ctx.statement());
            mv.visitJumpInsn(GOTO, endLabel);
            mv.visitLabel(elseLabel);
            visitStatement(ctx.elsePart().statement());
        }
        mv.visitLabel(endLabel);
    }

    private void visitFor(ForStatementContext ctx) {
        verifyType(visitAssignment(ctx.assignmentStatement()), PrimitiveType.INTEGER, ctx);
        Label startLabel = new Label();
        Label breakLabel = new Label();
        Label continueLabel = new Label();
        scope.enterLoop(continueLabel, breakLabel);
        boolean to = "to".equals(ctx.DIRECTION().getText());
        mv.visitLabel(startLabel);
        visitExpression(ctx.expression());
        visitQualifiedName(ctx.assignmentStatement().qualifiedName());
        mv.visitJumpInsn(to ? IF_ICMPLT : IF_ICMPGT, breakLabel);
        visitStatement(ctx.statement());
        mv.visitLabel(continueLabel);
        updateForCounter(ctx.assignmentStatement().qualifiedName(), to);
        mv.visitJumpInsn(GOTO, startLabel);
        mv.visitLabel(breakLabel);
        scope.exitLoop();
    }

    private void updateForCounter(QualifiedNameContext ctx, boolean to) {
        if (ctx.expression().isEmpty()) {
            updateForVariableCounter(ctx, to);
        } else {
            updateForArrayCellCounter(ctx, to);
        }
    }

    private void updateForArrayCellCounter(QualifiedNameContext ctx, boolean to) {
        ArrayType type = loadArray(ctx.ID().getText());
        computeArrayIndex(type, ctx);
        updateCounterValue(ctx, to);
        mv.visitInsn(IASTORE);
    }

    private void updateCounterValue(QualifiedNameContext ctx, boolean to) {
        visitQualifiedName(ctx);
        mv.visitInsn(to ? ICONST_1 : ICONST_M1);
        mv.visitInsn(IADD);
    }

    private ArrayType loadArray(String var) {
        ArrayType type;
        if (scope.isLocalVariable(var)) {
            checkArrayType(scope.getLocalVariableType(var));
            type = (ArrayType)scope.getLocalVariableType(var);
            mv.visitVarInsn(ALOAD, scope.getLocalVariableIndex(var));
        } else if (scope.isGlobalVariable(var)) {
            checkArrayType(scope.getGlobalVariableType(var));
            type = (ArrayType)scope.getGlobalVariableType(var);
            mv.visitFieldInsn(GETSTATIC, scope.getClassName(), var, type.getType().getDescriptor());
        } else {
            throw new CompileException(String.format("Variable %s not found.", var));
        }
        return type;
    }

    private void updateForVariableCounter(QualifiedNameContext ctx, boolean to) {
        String var = ctx.ID().getText();
        if (scope.isLocalVariable(var)) {
            mv.visitIincInsn(scope.getLocalVariableIndex(var), to ? 1 : -1);
        } else if (scope.isGlobalVariable(var)) {
            DataType type = scope.getGlobalVariableType(var);
            updateCounterValue(ctx, to);
            mv.visitFieldInsn(PUTSTATIC, scope.getClassName(), var, type.getType().getDescriptor());
        } else {
            throw new RuntimeException("No variable in contexts " + ctx.getText());
        }
    }

    private void visitWhile(WhileStatementContext ctx) {
        Label continueLabel = new Label();
        Label breakLabel = new Label();
        scope.enterLoop(continueLabel, breakLabel);
        mv.visitLabel(continueLabel);
        visitExpression(ctx.expression());
        mv.visitJumpInsn(IFEQ, breakLabel);
        visitStatement(ctx.statement());
        mv.visitJumpInsn(GOTO, continueLabel);
        mv.visitLabel(breakLabel);
        scope.exitLoop();
    }

    private DataType visitAssignment(AssignmentStatementContext ctx) {
        if (ctx.qualifiedName().expression().isEmpty()) {
            return visitVariableAssignment(ctx);
        } else {
            return visitArrayAssignment(ctx);
        }
    }

    private DataType visitVariableAssignment(AssignmentStatementContext ctx) {
        String var = ctx.qualifiedName().ID().getText();
        DataType etype = visitExpression(ctx.expression());
        if (var.equals(scope.getMethodName())) {
            verifyType(etype, scope.getMethodType(), ctx);
            mv.visitInsn(etype.isPrimitive() ? IRETURN : ARETURN);
        } else if (scope.isLocalVariable(var)) {
            verifyType(etype, scope.getLocalVariableType(var), ctx);
            int opcode = scope.getLocalVariableType(var).isPrimitive() ? ISTORE : ASTORE;
            mv.visitVarInsn(opcode, scope.getLocalVariableIndex(var));
        } else if (scope.isGlobalVariable(var)) {
            DataType vtype = scope.getGlobalVariableType(var);
            verifyType(etype, vtype, ctx);
            mv.visitFieldInsn(PUTSTATIC, scope.getClassName(), var, vtype.getType().getDescriptor());
        } else {
            throw new CompileException(String.format("Variable %s not found in context %s.", var, ctx.getText()));
        }
        return etype;
    }

    private DataType visitArrayAssignment(AssignmentStatementContext ctx) {
        ArrayType type = loadArray(ctx.qualifiedName().ID().getText());
        computeArrayIndex(type, ctx.qualifiedName());
        verifyType(visitExpression(ctx.expression()), PrimitiveType.INTEGER, ctx);
        mv.visitInsn(IASTORE);
        return PrimitiveType.INTEGER;
    }

    private void checkArrayType(DataType type) {
        if (!(type instanceof ArrayType))
            throw new CompileException("Not an array type: " + type);
    }

    private void computeArrayIndex(ArrayType type, QualifiedNameContext ctx) {
        if (ctx.expression().size() != type.getDimensions().length) {
            throw new CompileException(String.format("Arity exception. Got %d. Expected %d.",
                    ctx.expression().size(), type.getDimensions().length));
        }
        String errorMessage = String.format("Index out of bound in access %s", ctx.getText());
        for (int i = 0; i < ctx.expression().size(); i++) {
            Label badLabel = new Label();
            Label okLabel = new Label();
            visitExpression(ctx.expression().get(i));
            mv.visitInsn(DUP);
            mv.visitLdcInsn(type.getDimension(i).getFrom());
            mv.visitJumpInsn(IF_ICMPLT, badLabel);
            mv.visitInsn(DUP);
            mv.visitLdcInsn(type.getDimension(i).getTo());
            mv.visitJumpInsn(IF_ICMPGT, badLabel);
            mv.visitJumpInsn(GOTO, okLabel);
            mv.visitLabel(badLabel);
            mv.visitTypeInsn(NEW, Type.getInternalName(RuntimeException.class));
            mv.visitInsn(DUP);
            mv.visitLdcInsn(errorMessage);
            mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(RuntimeException.class), "<init>",
                    Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)), false);
            mv.visitInsn(ATHROW);
            mv.visitLabel(okLabel);
            mv.visitLdcInsn(type.getDimension(i).getFrom());
            mv.visitInsn(ISUB);
        }
        for (int i = ctx.expression().size() - 2; i >= 0; i--) {
            mv.visitLdcInsn(type.getDimension(i).getLength());
            mv.visitInsn(IMUL);
            mv.visitInsn(IADD);
        }
    }

    private void visitBlock(BlockContext ctx) {
        for (StatementContext sctx : ctx.statement())
            visitStatement(sctx);
    }

    private DataType visitFunctionCall(FunctionCallContext ctx) {
        String name = ctx.ID().getText();
        int i = 0;
        DataType[] argumentType = new DataType[ctx.expression().size()];
        for (ExpressionContext ectx : ctx.expression())
            argumentType[i++] = visitExpression(ectx);
        if (!scope.isFunctionDeclared(name, argumentType))
            throw new CompileException(String.format("No such method %s available in call %s", name, ctx.getText()));
        DataType returnType = scope.getFunctionReturnType(name, argumentType);
        mv.visitMethodInsn(INVOKESTATIC, scope.getClassName(), name, Utils.getFunctionDescriptor(returnType, argumentType), false);
        return returnType;
    }

    private void visitRead(ReadStatementContext ctx) {
        for (QualifiedNameContext nctx : ctx.qualifiedName()) {
            if (nctx.expression().isEmpty()) {
                readVariable(nctx, ctx);
            } else {
                readArrayElement(nctx);
            }
        }
    }

    private void readValue(PrimitiveType type) {
        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(System.class), "console", Type.getMethodDescriptor(Type.getType(Console.class)), false);
        mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(Console.class), "readLine", Type.getMethodDescriptor(Type.getType(String.class)), false);
        switch (type) {
            case BOOLEAN:
                mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Boolean.class), "parseBoolean", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getType(String.class)), false);
                break;
            case INTEGER:
                mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Integer.class), "parseInt", Type.getMethodDescriptor(Type.INT_TYPE, Type.getType(String.class)), false);
                break;
        }
    }

    private void readArrayElement(QualifiedNameContext nctx) {
        String var = nctx.ID().getText();
        ArrayType type = loadArray(var);
        computeArrayIndex(type, nctx);
        readValue(type.getDataType());
        mv.visitInsn(IASTORE);
    }

    private void readVariable(QualifiedNameContext nctx, ReadStatementContext ctx) {
        String var = nctx.ID().getText();
        if (scope.isLocalVariable(var)) {
            verifyPrimitiveType(scope.getLocalVariableType(var), ctx);
            readValue((PrimitiveType)scope.getLocalVariableType(var));
            mv.visitVarInsn(ISTORE, scope.getLocalVariableIndex(var));
        } else if (scope.isGlobalVariable(var)) {
            verifyPrimitiveType(scope.getGlobalVariableType(var), ctx);
            readValue((PrimitiveType)scope.getGlobalVariableType(var));
            mv.visitFieldInsn(PUTSTATIC, scope.getClassName(), var, Type.INT_TYPE.getDescriptor());
        } else {
            throw new CompileException(String.format("Variable %s not found in context %s", var, ctx));
        }
    }

    private void visitWrite(WriteStatementContext ctx) {
       for (ExpressionContext ectx : ctx.expression()) {
           mv.visitFieldInsn(GETSTATIC, Type.getInternalName(System.class), "out", Type.getDescriptor(PrintStream.class));
           visitExpression(ectx);
           mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println", Type.getMethodDescriptor(Type.VOID_TYPE, Type.INT_TYPE), false);
       }
    }

    private void visitBreak(BreakStatementContext ctx) {
        if (!scope.inLoop())
            throw new CompileException("Break is out of loop " + ctx.getText());
        mv.visitJumpInsn(GOTO, scope.getBreakLabel());
    }

    private void visitContinue(ContinueStatementContext ctx) {
        if (!scope.inLoop())
            throw new CompileException("Continue is out of loop " + ctx.getText());
        mv.visitJumpInsn(GOTO, scope.getContinueLabel());
    }

    private DataType visitExpression(ExpressionContext ctx) {
        AppTermContext actx = ctx.appTerm(0);
        DataType type = visitAppTerm(actx);
        if (ctx.APP_OP() != null) {
            String op = ctx.APP_OP().getText();
            verifyType(type, Utils.isBooleanOperator(op) ? PrimitiveType.BOOLEAN : PrimitiveType.INTEGER, actx);
            actx = ctx.appTerm(1);
            verifyType(visitAppTerm(actx), type, actx);
            if (Utils.isBooleanOperator(op)) {
                switch (op) {
                  case "or":
                      mv.visitInsn(IOR);
                      break;
                  case "and":
                      mv.visitInsn(IAND);
                      break;
                  default:
                      throw new CompileException("Unsupported boolean operation: " + ctx.getText());
                }
            } else {
                Label endLabel = new Label();
                Label falseLabel = new Label();
                switch (op) {
                    case ">=":
                        mv.visitJumpInsn(IF_ICMPLT, falseLabel);
                        break;
                    case "<=":
                        mv.visitJumpInsn(IF_ICMPGT, falseLabel);
                        break;
                    case "<>":
                        mv.visitJumpInsn(IF_ICMPEQ, falseLabel);
                        break;
                    case "=":
                        mv.visitJumpInsn(IF_ICMPNE, falseLabel);
                        break;
                    case ">":
                        mv.visitJumpInsn(IF_ICMPLE, falseLabel);
                        break;
                    case "<":
                        mv.visitJumpInsn(IF_ICMPGE, falseLabel);
                        break;
                    default:
                        throw new CompileException("Unsupported compare operation: " + ctx.getText());
                }
                mv.visitInsn(ICONST_1);
                mv.visitJumpInsn(GOTO, endLabel);
                mv.visitLabel(falseLabel);
                mv.visitInsn(ICONST_0);
                mv.visitLabel(endLabel);
            }
            return PrimitiveType.BOOLEAN;
        }
        return type;
    }

    private DataType visitAppTerm(AppTermContext ctx) {
        if (ctx.SIGN().isEmpty())
            return visitMulTerm(ctx.mulTerm(0));
        int i = 0;
        if (ctx.mulTerm().size() == ctx.SIGN().size()) {
            mv.visitInsn(ICONST_0);
        } else {
            MulTermContext mctx = ctx.mulTerm(i++);
            verifyType(visitMulTerm(mctx), PrimitiveType.INTEGER, mctx);
        }
        for (TerminalNode op : ctx.SIGN()) {
            MulTermContext mctx = ctx.mulTerm(i++);
            verifyType(visitMulTerm(mctx), PrimitiveType.INTEGER, mctx);
            switch (op.getText()) {
                case "+": mv.visitInsn(IADD); break;
                case "-": mv.visitInsn(ISUB); break;
            }
        }
        return PrimitiveType.INTEGER;
    }

    private DataType visitMulTerm(MulTermContext ctx) {
        DataType type = visitFactor(ctx.factor(0));
        if (!ctx.MUL_OP().isEmpty()) {
            verifyType(type, PrimitiveType.INTEGER, ctx);
            int i = 1;
            for (TerminalNode op : ctx.MUL_OP()) {
                type = visitFactor(ctx.factor(i++));
                switch (op.getText()) {
                    case "*": mv.visitInsn(IMUL); break;
                    case "/": mv.visitInsn(IDIV); break;
                    case "mod": mv.visitInsn(IREM); break;
                }
            }
        }
        return type;
    }

    private DataType visitFactor(FactorContext ctx) {
        if (ctx.expression() != null) {
            return visitExpression(ctx.expression());
        } else if (ctx.functionCall() != null) {
            return visitFunctionCall(ctx.functionCall());
        } else if (ctx.qualifiedName() != null) {
            return visitQualifiedName(ctx.qualifiedName());
        } else if (ctx.NUMBER() != null) {
            mv.visitLdcInsn(Integer.parseInt(ctx.NUMBER().getText()));
            return PrimitiveType.INTEGER;
        } else if (ctx.BOOL() != null) {
            mv.visitInsn("false".equals(ctx.BOOL().getText()) ? ICONST_0 : ICONST_1);
            return PrimitiveType.BOOLEAN;
        } else if (ctx.notFactor() != null) {
            return visitNotFactor(ctx.notFactor());
        } else {
            throw new CompileException("Unsupported expression " + ctx.getText());
        }
    }

    private DataType visitNotFactor(NotFactorContext ctx) {
        boolean revert = true;
        FactorContext fctx = ctx.factor();
        while (fctx.notFactor() != null) {
            revert = !revert;
            fctx = fctx.notFactor().factor();
        }
        verifyType(visitFactor(fctx), PrimitiveType.BOOLEAN, ctx);
        if (revert) {
            mv.visitInsn(ICONST_1);
            mv.visitInsn(IXOR);
        }
        return PrimitiveType.BOOLEAN;
    }

    private DataType visitQualifiedName(QualifiedNameContext ctx) {
        String var = ctx.ID().getText();
        DataType type;
        if (scope.isLocalVariable(var)) {
            type = scope.getLocalVariableType(var);
            mv.visitVarInsn(type.isPrimitive() ? ILOAD : ALOAD, scope.getLocalVariableIndex(var));
        } else if (scope.isGlobalVariable(var)) {
            type = scope.getGlobalVariableType(var);
            mv.visitFieldInsn(GETSTATIC, scope.getClassName(), var, type.getType().getDescriptor());
        } else {
            throw new CompileException(String.format("Variable %s not found in context %s.", var, ctx.getText()));
        }
        if (!ctx.expression().isEmpty()) {
            checkArrayType(type);
            ArrayType arrType = (ArrayType)type;
            computeArrayIndex(arrType, ctx);
            mv.visitInsn(IALOAD);
            return arrType.getDataType();
        }
        return type;
    }

    private void verifyType(@NotNull DataType gotten, @NotNull DataType expected, ParseTree ctx) {
        if (!expected.equals(gotten))
            throw new CompileException(String.format("Type mismatch in %s. Expected %s. Got %s.", ctx.getText(),
                    expected.toString(), gotten.toString()));
    }

    private void verifyPrimitiveType(@NotNull DataType gotten, ParseTree ctx) {
        if (!gotten.isPrimitive())
            throw new CompileException(String.format("Type mismatch in %s. Expected primitive type. Got %s.",
                    ctx.getText(), gotten.toString()));
    }
}