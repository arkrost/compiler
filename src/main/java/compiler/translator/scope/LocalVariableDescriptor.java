package compiler.translator.scope;

import compiler.translator.type.DataType;

/**
 * @author Arkady Rost
 */
public class LocalVariableDescriptor {
    private final int index;
    private final DataType type;

    public LocalVariableDescriptor(int index, DataType type) {
        this.index = index;
        this.type = type;
    }

    public int getIndex() {
        return index;
    }

    public DataType getType() {
        return type;
    }
}
