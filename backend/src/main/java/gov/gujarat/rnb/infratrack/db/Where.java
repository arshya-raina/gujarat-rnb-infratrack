package gov.gujarat.rnb.infratrack.db;

import java.util.ArrayList;
import java.util.List;

/** Builds a parameterised SQL WHERE clause. */
public final class Where {
    private final List<String> parts = new ArrayList<>();
    private final List<Object> args = new ArrayList<>();

    public Where and(String sql, Object... a) {
        parts.add(sql);
        args.addAll(List.of(a));
        return this;
    }

    public Where andIf(boolean cond, String sql, Object... a) { return cond ? and(sql, a) : this; }

    public Where copy() {
        Where w = new Where();
        w.parts.addAll(parts);
        w.args.addAll(args);
        return w;
    }

    public String sql() { return parts.isEmpty() ? "1=1" : String.join(" AND ", parts); }

    public Object[] args() { return args.toArray(); }

    /** Arguments of this clause followed by extra ones (for queries with further placeholders after WHERE). */
    public Object[] args(Object... extra) {
        List<Object> all = new ArrayList<>(args);
        all.addAll(List.of(extra));
        return all.toArray();
    }
}
