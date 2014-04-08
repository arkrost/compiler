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
        MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        for (Map.Entry<String, DataType> var : scope.getGlobalVariables().entrySet()) {
            if (var.getValue().isPrimitive())
                continue;
            ArrayType type = (ArrayType)var.getValue();
            mv.visitLdcInsn(type.getSize());
            mv.visitIntInsn(NEWARRAY, T_INT);
            mv.visitFieldInsn(PUTSTATIC, scope.getClassName(), var.getKey(), var.getValue().getType().getDescriptor());
        }
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
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
        if (rctxList.isEmpty())
            return PrimitiveType.INTEGER;
        Range[] dimensions = new Range[rctxList.size()];
        int i = 0;
        for (RangeContext rctx : rctxList) {
            int from = Integer.parseUnsignedInt(rctx.NUMBER(0).getText());
            int to = Integer.parseUnsignedInt(rctx.NUMBER(1).getText());
            dimensions[i++] = new Range(from, to);
        }
        return new ArrayType(dimensions);
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
        for (ParamDeclarationContext pctx : ctx.paramDeclaration()) {
            for (TerminalNode id : pctx.ID()) {
                if (scope.isLocalVariable(id.getText()))
                    throw new CompileException(String.format("Duplicate parameter %s in declaration %s", id.getText(), ctx.getText()));
                if (name.equals(id.getText()))
                    throw new CompileException(String.format("Illegal parameter %s name in declaration %s.", id.getText(), ctx.getText()));
                scope.addLocalVariable(id.getText(), PrimitiveType.INTEGER);
            }
        }
        int paramCount = scope.getLocalVariableCount();
        if (scope.isFunctionDeclared(name, paramCount))
            throw new CompileException(String.format("Function with same signature as %s already declared.", ctx.getText()));
        scope.declareFunction(name, paramCount);
        mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, name, Utils.getPascalFunctionDescriptor(paramCount), null, null);

        visitLocalVariableDeclarations(ctx.varDeclarations());
        visitBlock(ctx.block());

        mv.visitMaxs(0, 0);
        mv.visitEnd();
        scope.setMethodName(null);
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
            scope.addLocalVariable(var.getText(), type);
        }
    }

    private void visitStatement(StatementContext sctx) {
        if (sctx.ifStatement() != null) {
            visitIf(sctx.ifStatement());
        } else if (sctx.forStatement() != null) {
            visitFor(sctx.forStatement());
        } else if (sctx.whileStatement() != null) {
            visitWhile(sctx.whileStatement());
        } else if (sctx.assignmentStatement() != null) {
            visitAssignment(sctx.assignmentStatement());
        } else if (sctx.block() != null) {
            visitBlock(sctx.block());
        }else if (sctx.functionCall() != null) {
            visitFunctionCall(sctx.functionCall());
        } else if (sctx.readStatement() != null) {
            visitRead(sctx.readStatement());
        } else if (sctx.writeStatement() != null) {
            visitWrite(sctx.writeStatement());
        } else if (sctx.breakStatement() != null) {
            visitBreak();
        } else if (sctx.continueStatement() != null) {
            visitContinue();
        } else {
            throw new CompileException("Unsupported statement: " + sctx.getText());
        }
    }

    private void visitIf(IfStatementContext ctx) {
        visitExpression(ctx.expression());
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
        Label endLabel = new Label();
        Label continueLabel = new Label();
        boolean to = "to".equals(ctx.DIRECTION().getText());
        mv.visitLabel(startLabel);
        visitExpression(ctx.expression());
        visitQualifiedName(ctx.assignmentStatement().qualifiedName());
        mv.visitJumpInsn(to ? IF_ICMPLT : IF_ICMPGT, endLabel);
        visitStatement(ctx.statement());
        mv.visitLabel(continueLabel);
        updateForCounter(ctx.assignmentStatement().qualifiedName(), to);
        mv.visitJumpInsn(GOTO, startLabel);
        mv.visitLabel(endLabel);
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
            mv.visitIincInsn(scope.getLocalVariableIndex(var), to ? ICONST_1 : ICONST_M1);
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
        Label endLabel = new Label();
        mv.visitLabel(continueLabel);
        visitExpression(ctx.expression());
        mv.visitJumpInsn(IFEQ, endLabel);
        visitStatement(ctx.statement());
        mv.visitJumpInsn(GOTO, continueLabel);
        mv.visitLabel(endLabel);
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
            verifyType(etype, PrimitiveType.INTEGER, ctx);
            mv.visitInsn(IRETURN);
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
        verifyType(visitExpression(ctx.expression()), type, ctx);
        mv.visitInsn(IASTORE);
        return type;
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
        int paramCount = ctx.expression().size();
        String name = ctx.ID().getText();
        String desc = Utils.getPascalFunctionDescriptor(paramCount);
        if (!scope.isFunctionDeclared(name, paramCount))
            throw new CompileException(String.format("No such method %s%s available in call %s", name, desc, ctx.getText()));
        for (ExpressionContext ectx : ctx.expression())
            verifyType(visitExpression(ectx), PrimitiveType.INTEGER, ctx);
        mv.visitMethodInsn(INVOKESTATIC, scope.getClassName(), name, desc, false);
        return PrimitiveType.INTEGER;
    }

    private void visitRead(ReadStatementContext ctx) {
        throw new CompileException("Unsupported statement: " + ctx.getText());
    }

    private void visitWrite(WriteStatementContext ctx) {
       for (ExpressionContext ectx : ctx.expression()) {
           mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
           visitExpression(ectx);
           mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
       }
    }

    private void visitBreak() {
        throw new CompileException("Unsupported break statement");
    }

    private void visitContinue() {
        throw new CompileException("Unsupported continue statement");
    }

    private DataType visitExpression(ExpressionContext ctx) {
        AppTermContext actx = ctx.appTerm(0);
        DataType type = visitAppTerm(actx);
        if (ctx.CMP_OP() != null) {
            verifyType(type, PrimitiveType.INTEGER, actx);
            actx = ctx.appTerm(1);
            verifyType(visitAppTerm(actx), PrimitiveType.INTEGER, actx);
            Label endLabel = new Label();
            Label falseLabel = new Label();
            switch (ctx.CMP_OP().getText()) {
                case ">=": mv.visitJumpInsn(IF_ICMPLT, falseLabel); break;
                case "<=": mv.visitJumpInsn(IF_ICMPGT, falseLabel); break;
                case "<>": mv.visitJumpInsn(IF_ICMPEQ, falseLabel); break;
                case "=": mv.visitJumpInsn(IF_ICMPNE, falseLabel); break;
                case ">": mv.visitJumpInsn(IF_ICMPLE, falseLabel); break;
                case "<": mv.visitJumpInsn(IF_ICMPGE, falseLabel); break;
                default:
                    throw new CompileException("Unsupported compare operation: " + ctx.getText());
            }
            mv.visitInsn(ICONST_1);
            mv.visitJumpInsn(GOTO, endLabel);
            mv.visitLabel(falseLabel);
            mv.visitInsn(ICONST_0);
            mv.visitLabel(endLabel);
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
            visitMulTerm(ctx.mulTerm(i++));
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
                visitFactor(ctx.factor(i++));
                switch (op.getText()) {
                    case "*": mv.visitInsn(IMUL); break;
                    case "/": mv.visitInsn(IDIV); break;
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
            mv.visitLdcInsn(Integer.parseUnsignedInt(ctx.NUMBER().getText()));
            return PrimitiveType.INTEGER;
        } else {
            throw new CompileException("Unsupported expression " + ctx.getText());
        }
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
            computeArrayIndex((ArrayType)type, ctx);
            mv.visitInsn(IALOAD);
        }
        return type;
    }

    private void verifyType(@NotNull DataType gotten, @NotNull DataType expected, ParseTree ctx) {
        if (!expected.equals(gotten))
            throw new CompileException(String.format("Type mismatch in %s. Expected %s. Got %s.", ctx.getText(),
                    expected.toString(), gotten.toString()));
    }
}
