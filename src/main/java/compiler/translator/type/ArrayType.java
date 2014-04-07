package compiler.translator.type;

import org.objectweb.asm.Type;

import java.util.Arrays;

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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ARRAY[");
        for (Range r : dimensions)
            sb.append(r.getFrom()).append("..").append(r.getTo()).append(',');
        sb.deleteCharAt(sb.length() - 1).append(']');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayType arrayType = (ArrayType) o;
        return Arrays.equals(dimensions, arrayType.dimensions);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(dimensions);
    }
}
