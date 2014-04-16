package compiler.translator;

import compiler.translator.type.DataType;
import org.objectweb.asm.Type;

/**
 * @author Arkady Rost
 */
public class Utils {
    public static String getFunctionDescriptor(DataType returnDataType, DataType... argumentDataType) {
        Type[] argumentType = new Type[argumentDataType.length];
        for (int i = 0; i < argumentDataType.length; i++)
            argumentType[i] = argumentDataType[i].getType();
        return Type.getMethodDescriptor(returnDataType.getType(), argumentType);
    }

    public static boolean isBooleanOperator(String op) {
        return "or".equals(op) || "and".equals(op);
    }
}
