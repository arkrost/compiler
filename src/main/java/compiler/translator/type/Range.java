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
        return to - from + 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Range range = (Range) o;
        return !(from != range.from || to != range.to);
    }

    @Override
    public int hashCode() {
        int result = from;
        result = 31 * result + to;
        return result;
    }
}
