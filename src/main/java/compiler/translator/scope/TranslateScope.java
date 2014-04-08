package compiler.translator.scope;

import compiler.translator.type.DataType;
import org.objectweb.asm.Label;

import java.util.*;

/**
 * @author Arkady Rost
 */
public class TranslateScope implements Scope {
    private String className;
    private String methodName;
    private byte[] byteCode;
    private Map<String, DataType> global = new HashMap<>();
    private Set<PascalFunctionDescriptor> functions = new HashSet<>();
    private Map<String, LocalVariableDescriptor> local = new HashMap<>();
    private Stack<LoopDescriptor> loop = new Stack<>();

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

    public void refreshLocalVariables() {
        local.clear();
    }

    public boolean isLocalVariable(String name) {
        return local.containsKey(name);
    }

    public int getLocalVariableIndex(String name) {
        return local.get(name).getIndex();
    }

    public DataType getLocalVariableType(String name) {
        return local.get(name).getType();
    }

    public int getLocalVariableCount() {
        return local.size();
    }

    public int addLocalVariable(String name, DataType type) {
        local.put(name, new LocalVariableDescriptor(local.size(), type));
        return local.size() - 1;
    }

    public boolean isFunctionDeclared(String name, int paramCount) {
        return functions.contains(new PascalFunctionDescriptor(name, paramCount));
    }

    public void declareFunction(String name, int paramCount) {
        functions.add(new PascalFunctionDescriptor(name, paramCount));
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public boolean inLoop() {
        return !loop.isEmpty();
    }

    public Label getContinueLabel() {
        return loop.peek().getContinueLabel();
    }

    public Label getBreakLabel() {
        return loop.peek().getBreakLabel();
    }

    public void enterLoop(Label continueLabel, Label breakLabel) {
        loop.push(new LoopDescriptor(continueLabel, breakLabel));
    }

    public void exitLoop() {
        loop.pop();
    }
}
