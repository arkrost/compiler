package translator;

import org.antlr.v4.runtime.misc.NotNull;
import parser.PascalBaseListener;
import parser.PascalParser;

/**
 * @author Arkady Rost
 */
public class TranslateListener extends PascalBaseListener {
    @Override
    public void enterProgram(@NotNull PascalParser.ProgramContext ctx) {
        System.out.println("public class " + ctx.ID().getText() + " {");
    }

    @Override
    public void exitProgram(@NotNull PascalParser.ProgramContext ctx) {
        System.out.println("}");
    }

    @Override
    public void enterMain(@NotNull PascalParser.MainContext ctx) {
        System.out.println("public static void main(String... args) {");
    }

    @Override
    public void exitMain(@NotNull PascalParser.MainContext ctx) {
        System.out.println("}");
    }
}
