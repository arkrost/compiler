package compiler.translator.scope;

import compiler.translator.type.DataType;

import java.util.Arrays;

/**
 * @author Arkady Rost
 */
class FunctionSignature {
    private final String name;
    private final DataType[] argumentType;

    public FunctionSignature(String name, DataType[] argumentType) {
        this.name = name;
        this.argumentType = argumentType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FunctionSignature that = (FunctionSignature) o;
        return name.equals(that.name) && Arrays.equals(argumentType, that.argumentType);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + Arrays.hashCode(argumentType);
        return result;
    }
}
