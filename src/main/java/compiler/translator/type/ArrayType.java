package compiler.translator.type;

import org.objectweb.asm.Type;

/**
 * @author Arkady Rost
 */
public class ArrayType implements DataType {
    private static final Type type = Type.getType(int[].class);
    private final Range[] dimensions;

    public ArrayType(Range... dimensions) {
        this.dimensions = dimensions;
    }

    public Range[] getDimensions() {
        return dimensions;
    }

    public Range getDimension(int i) {
        return dimensions[i];
    }

    @Override
    public Type getType() {
        return type;
    }

    public int getSize() {
        int size = 1;
        for (Range d : dimensions)
            size *= d.getLength();
        return size;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }
}
