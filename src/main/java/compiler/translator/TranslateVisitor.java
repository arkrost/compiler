package compiler.translator;

import compiler.parser.PascalParser.*;
import compiler.translator.scope.Scope;
import compiler.translator.scope.TranslateScope;
import compiler.translator.type.ArrayType;
import compiler.translator.type.DataType;
import compiler.translator.type.PrimitiveType;
import compiler.translator.type.Range;
import org.antlr.v4.runtime.misc.NotNull;
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

    private void visitProgram(@NotNull ProgramContext ctx) {
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

    private void visitBody(@NotNull BodyContext ctx) {
        visitGlobalVarDeclarations(ctx.var_declaration_part());
        visitFunctionDeclarations(ctx.function_declaration_part());

        // entry point
        mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);

        visitBlock(ctx.block());

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void visitGlobalVarDeclarations(Var_declaration_partContext ctx) {
        for (Var_declarationContext vctx : ctx.var_declaration())
            visitGlobalVarDeclaration(vctx);
        createConstructors();
    }

    private void visitGlobalVarDeclaration(Var_declarationContext ctx) {
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

    private void visitFunctionDeclarations(Function_declaration_partContext ctx) {
        for (Function_declarationContext fctx : ctx.function_declaration())
            visitFunctionDeclaration(fctx);
    }

    private void visitFunctionDeclaration(Function_declarationContext ctx) {
        throw new CompileException("Function declarations aren't supported yet. " + ctx.getText());
    }

    private void visitStatement(StatementContext sctx) {
        if (sctx.if_statement() != null) {
            visitIf(sctx.if_statement());
        } else if (sctx.for_statement() != null) {
            visitFor(sctx.for_statement());
        } else if (sctx.while_statement() != null) {
            visitWhile(sctx.while_statement());
        } else if (sctx.assignment_statement() != null) {
            visitAssignment(sctx.assignment_statement());
        } else if (sctx.block() != null) {
            visitBlock(sctx.block());
        }else if (sctx.function_call() != null) {
            visitFunctionCall(sctx.function_call());
        } else if (sctx.read_statement() != null) {
            visitRead(sctx.read_statement());
        } else if (sctx.write_statement() != null) {
            visitWrite(sctx.write_statement());
        } else if (sctx.break_statement() != null) {
            visitBreak();
        } else if (sctx.continue_statement() != null) {
            visitContinue();
        } else {
            throw new CompileException("Unsupported statement: " + sctx.getText());
        }
    }

    private void visitIf(If_statementContext ctx) {
        visitExpression(ctx.expression());
        Label endLabel = new Label();
        if (ctx.else_part() == null) {
            mv.visitJumpInsn(IFEQ, endLabel);
            visitStatement(ctx.statement());
        } else {
            Label elseLabel = new Label();
            mv.visitJumpInsn(IFEQ, elseLabel);
            visitStatement(ctx.statement());
            mv.visitJumpInsn(GOTO, endLabel);
            mv.visitLabel(elseLabel);
            visitStatement(ctx.else_part().statement());
        }
        mv.visitLabel(endLabel);
    }

    private void visitFor(For_statementContext ctx) {
        throw new CompileException("Unsupported statement: " + ctx.getText());
    }

    private void visitWhile(While_statementContext ctx) {
        throw new CompileException("Unsupported statement: " + ctx.getText());
    }

    private void visitAssignment(Assignment_statementContext ctx) {
        Qualified_nameContext nctx = ctx.qualified_name();
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

    private void visitArrayAssignment(Qualified_nameContext nctx, ExpressionContext ctx) {
        String var = nctx.ID().getText();
        ArrayType type;
        if (scope.isGlobalVariable(var)) {
            checkArrayType(scope.getGlobalVariableType(var));
            type = (ArrayType)scope.getGlobalVariableType(var);
            mv.visitFieldInsn(GETSTATIC, scope.getClassName(), var, type.getType().getDescriptor());
        } else {
            throw new CompileException("Local assignments are not supported yet.");
        }
        computeArrayIndex(type, nctx);
        visitExpression(ctx);
        mv.visitInsn(IASTORE);
    }

    private void checkArrayType(DataType type) {
        if (!(type instanceof ArrayType))
            throw new CompileException("Not an array type: " + type);
    }

    private void computeArrayIndex(ArrayType type, Qualified_nameContext ctx) {
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

    private void visitFunctionCall(Function_callContext ctx) {
        throw new CompileException("Unsupported statement: " + ctx.getText());
    }

    private void visitRead(Read_statementContext ctx) {
        throw new CompileException("Unsupported statement: " + ctx.getText());
    }

    private void visitWrite(Write_statementContext ctx) {
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
        visitAppTerm(ctx.app_term(0));
        if (ctx.CMP_OP() != null) {
            visitAppTerm(ctx.app_term(1));
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

    private void visitAppTerm(App_termContext ctx) {
        if (ctx.SIGN().isEmpty()) {
            visitMulTerm(ctx.mul_term(0));
            return;
        }
        int i = 0;
        if (ctx.mul_term().size() == ctx.SIGN().size()) {
            mv.visitInsn(ICONST_0);
        } else {
            visitMulTerm(ctx.mul_term(i++));
        }
        for (TerminalNode op : ctx.SIGN()) {
            visitMulTerm(ctx.mul_term(i++));
            switch (op.getText()) {
                case "+": mv.visitInsn(IADD); break;
                case "-": mv.visitInsn(ISUB); break;
            }
        }
    }

    private void visitMulTerm(Mul_termContext ctx) {
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
        } else if (ctx.function_call() != null) {
            visitFunctionCall(ctx.function_call());
        } else if (ctx.qualified_name() != null) {
            visitQualifiedName(ctx.qualified_name());
        } else if (ctx.NUMBER() != null) {
            mv.visitLdcInsn(Integer.parseUnsignedInt(ctx.NUMBER().getText()));
        } else {
            throw new CompileException("Unsupported expression " + ctx.getText());
        }
    }

    private void visitQualifiedName(Qualified_nameContext ctx) {
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
