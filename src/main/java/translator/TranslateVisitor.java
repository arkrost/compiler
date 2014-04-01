package translator;

import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import parser.PascalParser.*;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author Arkady Rost
 */
public class TranslateVisitor {
    private ClassWriter cw;
    private MethodVisitor mv;

    public byte[] compile(ProgramContext ctx) {
        if (ctx == null)
            throw new IllegalArgumentException("ctx is null");
        refresh();
        visitProgram(ctx);
        return cw.toByteArray();
    }

    private void refresh() {
        cw = null;
        mv = null;
    }

    private void visitProgram(@NotNull ProgramContext ctx) {
        cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_7, ACC_PUBLIC, ctx.ID().getText(), null, "java/lang/Object", null);

        //empty constructor
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        visitBody(ctx.body());
        cw.visitEnd();
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
    }

    private void visitGlobalVarDeclaration(Var_declarationContext ctx) {
        for (TerminalNode id : ctx.ID())
            declareField(id.getText(), Type.INT_TYPE, 0); //todo array declaration

    }

    private void declareField(String name, Type type, Object value) {
        cw.visitField(ACC_PRIVATE | ACC_STATIC, name, type.getDescriptor(), null,  value);
    }

    private void visitFunctionDeclarations(Function_declaration_partContext ctx) {
        //todo implement
    }

    private void visitBlock(BlockContext ctx) {
        //todo implement
    }
}
