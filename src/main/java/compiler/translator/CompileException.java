package compiler.translator;

/**
 * @author Arkady Rost
 */
public class CompileException extends IllegalStateException {
    public CompileException(String s) {
        super(s);
    }

    public CompileException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public CompileException(Throwable throwable) {
        super(throwable);
    }
}
