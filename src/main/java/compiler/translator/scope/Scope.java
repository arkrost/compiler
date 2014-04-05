package compiler.translator.scope;

/**
 * @author Arkady Rost
 */
public interface Scope {
    String getClassName();
    byte[] getByteCode();
}
