package compiler.translator.type;

import org.objectweb.asm.Type;

/**
 * @author Arkady Rost
 */
public enum PrimitiveType implements DataType {
    INTEGER(Type.INT_TYPE);

    private final Type type;

    private PrimitiveType(Type type) {
        this.type = type;
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public Type getType() {
        return type;
    }
}
