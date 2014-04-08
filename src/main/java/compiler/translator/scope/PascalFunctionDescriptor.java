package compiler.translator.scope;

import compiler.translator.Utils;

/**
 * @author Arkady Rost
 */
class PascalFunctionDescriptor {
    private final String name;
    private final int paramCount;

    public PascalFunctionDescriptor(String name, int paramCount) {
        this.name = name;
        this.paramCount = paramCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PascalFunctionDescriptor that = (PascalFunctionDescriptor) o;
        return paramCount == that.paramCount && name.equals(that.name);

    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + paramCount;
        return result;
    }

    @Override
    public String toString() {
        return name + Utils.getPascalFunctionDescriptor(paramCount);
    }
}
