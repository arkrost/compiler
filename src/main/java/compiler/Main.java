package compiler;

import compiler.parser.PascalLexer;
import compiler.parser.PascalParser;
import compiler.translator.TranslateVisitor;
import compiler.translator.TranslatedClassLoader;
import compiler.translator.scope.Scope;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.TokenStream;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * @author Arkady Rost
 */
public class Main {
    public static void main(String[] args) throws NoSuchMethodException, IllegalAccessException, NoSuchFieldException {
        try (InputStream fis = new FileInputStream("test.pas")) {

            ANTLRInputStream input = new ANTLRInputStream(fis);

            Lexer lexer = new PascalLexer(input);
            TokenStream stream = new CommonTokenStream(lexer);
            PascalParser parser = new PascalParser(stream);

            PascalParser.ProgramContext ctx = parser.program();

            TranslateVisitor visitor = new TranslateVisitor();
            Scope scope = visitor.visit(ctx);

            try (FileOutputStream fos = new FileOutputStream(scope.getClassName() + ".class")) {
                fos.write(scope.getByteCode());
            }

            TranslatedClassLoader cl = new TranslatedClassLoader();
            Class<?> foo = cl.defineClass(scope.getClassName(), scope.getByteCode());

            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType mt = MethodType.methodType(void.class, String[].class);
            MethodHandle mh = lookup.findStatic(foo, "main", mt);
            try {
                mh.invokeExact(new String[]{});
            } catch (Throwable t) {
                System.err.println("Error occurred during invoking compiled sources!");
                t.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
