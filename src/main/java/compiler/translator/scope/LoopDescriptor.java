package compiler.translator.scope;

import org.objectweb.asm.Label;

/**
 * @author Irene Petrova
 */
class LoopDescriptor {
    private final Label continueLabel;
    private final Label breakLabel;

    public LoopDescriptor(Label continueLabel, Label breakLabel) {
        this.continueLabel = continueLabel;
        this.breakLabel = breakLabel;
    }

    public Label getBreakLabel() {
        return breakLabel;
    }

    public Label getContinueLabel() {
        return continueLabel;
    }
}
