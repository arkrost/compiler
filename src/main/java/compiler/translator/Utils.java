package compiler.translator;

import org.objectweb.asm.Type;

/**
 * @author Arkady Rost
 */
public class Utils {
    public static String getPascalFunctionDescriptor(int paramCount) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (int i = 0; i <= paramCount; i++)
            sb.append(Type.INT_TYPE.getDescriptor());
        sb.insert(sb.length() - 1, ')');
        return sb.toString();
    }

    public static boolean isBooleanOperator(String op) {
        return "or".equals(op) || "and".equals(op);
    }
}
