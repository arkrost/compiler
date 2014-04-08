package compiler;

import compiler.parser.PascalLexer;
import compiler.parser.PascalParser;
import compiler.translator.CompileException;
import compiler.translator.TranslateVisitor;
import compiler.translator.scope.Scope;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.TokenStream;

import java.io.*;

/**
 * @author Arkady Rost
 */
public class Compiler {
    private static final CompilerErrorListener LISTENER = new CompilerErrorListener();

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Expected filename to compile.");
            System.exit(1);
        }

        try (InputStream fis = new FileInputStream(args[0])) {
            ANTLRInputStream input = new ANTLRInputStream(fis);

            Lexer lexer = new PascalLexer(input);
            lexer.addErrorListener(LISTENER);

            TokenStream stream = new CommonTokenStream(lexer);
            PascalParser parser = new PascalParser(stream);
            parser.addErrorListener(LISTENER);

            PascalParser.ProgramContext ctx = parser.program();
            if (LISTENER.isErrorOccurred()) {
                System.err.println("Syntax error occurred!");
                System.exit(1);
            }

            TranslateVisitor visitor = new TranslateVisitor();
            Scope scope = visitor.visit(ctx);

            try (FileOutputStream fos = new FileOutputStream(scope.getClassName() + ".class")) {
                fos.write(scope.getByteCode());
            } catch (IOException e) {
                System.err.println("Unexpected io exception: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
            System.out.println("Successfully compiled " + scope.getClassName() + ".class");
        } catch (FileNotFoundException e) {
            System.err.println("Can't find file: " + args[0]);
            System.exit(1);
        } catch (CompileException e) {
            System.err.println("Compilation failed!");
            e.printStackTrace();
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Unexpected io exception!");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
