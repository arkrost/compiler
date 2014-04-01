import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.TokenStream;
import parser.PascalLexer;
import parser.PascalParser;
import translator.TranslateVisitor;
import translator.TranslatedClassLoader;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * @author Arkady Rost
 */
public class Main {
    public static void main(String[] args) throws NoSuchMethodException, IllegalAccessException {
        try (InputStream fis = new FileInputStream("test.pas")) {

            ANTLRInputStream input = new ANTLRInputStream(fis);

            Lexer lexer = new PascalLexer(input);
            TokenStream stream = new CommonTokenStream(lexer);
            PascalParser parser = new PascalParser(stream);

            PascalParser.ProgramContext ctx = parser.program();

            String className = ctx.ID().getText();

            TranslateVisitor visitor = new TranslateVisitor();
            byte[] byteCode = visitor.compile(ctx);

            TranslatedClassLoader cl = new TranslatedClassLoader();
            Class<?> foo = cl.defineClass(className, byteCode);

            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType mt = MethodType.methodType(void.class, String[].class);
            MethodHandle mh = lookup.findStatic(foo, "main", mt);
            try {
                mh.invokeExact(new String[]{});
            } catch (Throwable t) {
                System.err.println("Error occurred during invoking compiled sources!");
                t.printStackTrace();
            }

            try (FileOutputStream fos = new FileOutputStream(className + ".class")) {
                fos.write(byteCode);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
