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
import org.objectweb.asm.MethodVisitor;

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
        mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        mv.visitLdcInsn("hello");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);

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
        for (Function_declarationContext fctx: ctx.function_declaration())
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
        throw new CompileException("Unsupported statement: " + ctx.getText());
    }

    private void visitFor(For_statementContext ctx) {
        throw new CompileException("Unsupported statement: " + ctx.getText());
    }

    private void visitWhile(While_statementContext ctx) {
        throw new CompileException("Unsupported statement: " + ctx.getText());
    }

    private void visitAssignment(Assignment_statementContext ctx) {
        throw new CompileException("Unsupported statement: " + ctx.getText());
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
           mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
       }
    }

    private void visitBreak() {
        throw new CompileException("Unsupported break statement");
    }

    private void visitContinue() {
        throw new CompileException("Unsupported continue statement");
    }

    private void visitExpression(ExpressionContext ectx) {

    }
}
