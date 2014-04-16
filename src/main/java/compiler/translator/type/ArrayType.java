package compiler.translator.type;

import org.objectweb.asm.Type;

import java.util.Arrays;

/**
 * @author Arkady Rost
 */
public class ArrayType implements DataType {
    private final DataType type;
    private final Range[] dimensions;

    public ArrayType(DataType type, Range... dimensions) {
        this.type = type;
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
        return Type.getType(int[].class);
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
        sb.deleteCharAt(sb.length() - 1).append("] of ").append(type);
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayType arrayType = (ArrayType) o;
        return Arrays.equals(dimensions, arrayType.dimensions) && !(type != null ? !type.equals(arrayType.type) : arrayType.type != null);

    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (dimensions != null ? Arrays.hashCode(dimensions) : 0);
        return result;
    }
}
