package compiler.translator.type;

/**
 * @author Arkady Rost
 */
public class Range {
    private final int from;
    private final int to;

    public Range(int from, int to) {
        this.from = from;
        this.to = to;
    }

    public int getTo() {
        return to;
    }

    public int getFrom() {
        return from;
    }

    public int getLength() {
        return from - to;
    }
}
