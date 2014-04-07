package compiler.translator;

import compiler.parser.PascalParser.*;
import compiler.translator.scope.Scope;
import compiler.translator.scope.TranslateScope;
import compiler.translator.type.ArrayType;
import compiler.translator.type.DataType;
import compiler.translator.type.PrimitiveType;
import compiler.translator.type.Range;
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
        cw.visitField(ACC_PRIVATE | ACC_STATIC, name, descriptor, null, value).visitEnd();
    }

    private void visitFunctionDeclarations(FunctionDeclarationsContext ctx) {
        for (FunctionDeclarationContext fctx : ctx.functionDeclaration())
            visitFunctionDeclaration(fctx);
    }

    private void visitFunctionDeclaration(FunctionDeclarationContext ctx) {
        throw new CompileException("Function declarations aren't supported yet. " + ctx.getText());
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
        visitAssignment(ctx.assignmentStatement());
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
        if (scope.isGlobalVariable(var)) {
            checkArrayType(scope.getGlobalVariableType(var));
            type = (ArrayType)scope.getGlobalVariableType(var);
            mv.visitFieldInsn(GETSTATIC, scope.getClassName(), var, type.getType().getDescriptor());
        } else {
            throw new CompileException("Local assignments are not supported yet.");
        }
        return type;
    }

    private void updateForVariableCounter(QualifiedNameContext ctx, boolean to) {
        String var = ctx.ID().getText();
        if (scope.isGlobalVariable(var)) {
            DataType type = scope.getGlobalVariableType(var);
            updateCounterValue(ctx, to);
            mv.visitFieldInsn(PUTSTATIC, scope.getClassName(), var, type.getType().getDescriptor());
        } else {
            throw new CompileException("Local assignments are not supported yet.");
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

    private void visitAssignment(AssignmentStatementContext ctx) {
        QualifiedNameContext nctx = ctx.qualifiedName();
        if (nctx.expression().isEmpty()) {
            visitVariableAssignment(nctx.ID().getText(), ctx.expression());
        } else {
            visitArrayAssignment(nctx, ctx.expression());
        }
    }

    private void visitVariableAssignment(String var, ExpressionContext ctx) {
        visitExpression(ctx);
        if (scope.isGlobalVariable(var)) {
            DataType type = scope.getGlobalVariableType(var);
            mv.visitFieldInsn(PUTSTATIC, scope.getClassName(), var, type.getType().getDescriptor());
        } else {
            throw new CompileException("Local assignments are not supported yet.");
        }
    }

    private void visitArrayAssignment(QualifiedNameContext nctx, ExpressionContext ctx) {
        ArrayType type = loadArray(nctx.ID().getText());
        computeArrayIndex(type, nctx);
        visitExpression(ctx);
        mv.visitInsn(IASTORE);
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

    private void visitFunctionCall(FunctionCallContext ctx) {
        throw new CompileException("Unsupported statement: " + ctx.getText());
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

    private void visitExpression(ExpressionContext ctx) {
        visitAppTerm(ctx.appTerm(0));
        if (ctx.CMP_OP() != null) {
            visitAppTerm(ctx.appTerm(1));
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
    }

    private void visitAppTerm(AppTermContext ctx) {
        if (ctx.SIGN().isEmpty()) {
            visitMulTerm(ctx.mulTerm(0));
            return;
        }
        int i = 0;
        if (ctx.mulTerm().size() == ctx.SIGN().size()) {
            mv.visitInsn(ICONST_0);
        } else {
            visitMulTerm(ctx.mulTerm(i++));
        }
        for (TerminalNode op : ctx.SIGN()) {
            visitMulTerm(ctx.mulTerm(i++));
            switch (op.getText()) {
                case "+": mv.visitInsn(IADD); break;
                case "-": mv.visitInsn(ISUB); break;
            }
        }
    }

    private void visitMulTerm(MulTermContext ctx) {
        visitFactor(ctx.factor(0));
        if (!ctx.MUL_OP().isEmpty()) {
            int i = 1;
            for (TerminalNode op : ctx.MUL_OP()) {
                visitFactor(ctx.factor(i++));
                switch (op.getText()) {
                    case "*": mv.visitInsn(IMUL); break;
                    case "/": mv.visitInsn(IDIV); break;
                }
            }
        }
    }

    private void visitFactor(FactorContext ctx) {
        if (ctx.expression() != null) {
            visitExpression(ctx.expression());
        } else if (ctx.functionCall() != null) {
            visitFunctionCall(ctx.functionCall());
        } else if (ctx.qualifiedName() != null) {
            visitQualifiedName(ctx.qualifiedName());
        } else if (ctx.NUMBER() != null) {
            mv.visitLdcInsn(Integer.parseUnsignedInt(ctx.NUMBER().getText()));
        } else {
            throw new CompileException("Unsupported expression " + ctx.getText());
        }
    }

    private void visitQualifiedName(QualifiedNameContext ctx) {
        String var = ctx.ID().getText();
        DataType type;
        if (scope.isGlobalVariable(var)) {
             type = scope.getGlobalVariableType(var);
            mv.visitFieldInsn(GETSTATIC, scope.getClassName(), var, type.getType().getDescriptor());
        } else {
            throw new CompileException("Local variables are not supported yet.");
        }
        if (!ctx.expression().isEmpty()) {
            checkArrayType(type);
            computeArrayIndex((ArrayType)type, ctx);
            mv.visitInsn(IALOAD);
        }
    }
}
