package compiler.translator.scope;

import compiler.translator.type.DataType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Arkady Rost
 */
public class TranslateScope implements Scope {
    private String className;
    private byte[] byteCode;
    private Map<String, DataType> global = new HashMap<>();


    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public byte[] getByteCode() {
        return byteCode;
    }

    public void setByteCode(byte[] byteCode) {
        this.byteCode = byteCode;
    }

    public void addGlobalVariable(String name, DataType type) {
        global.put(name, type);
    }

    public Map<String, DataType> getGlobalVariables() {
        return Collections.unmodifiableMap(global);
    }

    public boolean isGlobalVariable(String name) {
        return global.containsKey(name);
    }

    public DataType getGlobalVariableType(String name) {
        return global.get(name);
    }
}
