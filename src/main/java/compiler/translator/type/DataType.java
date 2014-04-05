package compiler.translator.type;

import org.objectweb.asm.Type;

/**
 * @author Arkady Rost
 */
public interface DataType {
    Type getType();
    boolean isPrimitive();
}
