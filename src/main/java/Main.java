import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import parser.PascalLexer;
import parser.PascalParser;
import translator.TranslateListener;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author Arkady Rost
 */
public class Main {
    public static void main(String[] args) throws IOException {
        ANTLRInputStream input = new ANTLRInputStream(new FileInputStream("test.pas"));

        Lexer lexer = new PascalLexer(input);
        TokenStream stream = new CommonTokenStream(lexer);
        PascalParser parser = new PascalParser(stream);

        PascalParser.ProgramContext ctx = parser.program();

        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(new TranslateListener(), ctx);
    }
}
