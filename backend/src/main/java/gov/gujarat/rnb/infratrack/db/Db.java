package gov.gujarat.rnb.infratrack.db;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Thin JDBC helper around the embedded H2 database. Rows come back as maps keyed by lower-case column name. */
public final class Db {
    private static String url;
    @SuppressWarnings("unused")
    private static Connection keepAlive; // keeps the embedded database open for the life of the server

    private Db() {}

    public static void init(String jdbcUrl) throws SQLException {
        url = jdbcUrl;
        keepAlive = DriverManager.getConnection(url, "sa", "");
    }

    public static Connection conn() throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    public static List<Map<String, Object>> query(String sql, Object... args) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= n; i++) row.put(md.getColumnLabel(i).toLowerCase(Locale.ROOT), convert(rs.getObject(i)));
                    out.add(row);
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL failed: " + sql, e);
        }
    }

    public static Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = query(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static Object scalar(String sql, Object... args) {
        Map<String, Object> row = one(sql, args);
        return row == null ? null : row.values().iterator().next();
    }

    public static long count(String sql, Object... args) {
        Object v = scalar(sql, args);
        return v == null ? 0 : ((Number) v).longValue();
    }

    public static int update(String sql, Object... args) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("SQL failed: " + sql, e);
        }
    }

    /** Insert one row and return its generated id. */
    public static long insert(String sql, Object... args) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, args);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL failed: " + sql, e);
        }
    }

    public static void batch(Connection c, String sql, List<Object[]> rows) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int k = 0;
            for (Object[] r : rows) {
                bind(ps, r);
                ps.addBatch();
                if (++k % 2000 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
    }

    static void bind(PreparedStatement ps, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a instanceof LocalDate d) ps.setDate(i + 1, java.sql.Date.valueOf(d));
            else if (a instanceof LocalDateTime t) ps.setTimestamp(i + 1, Timestamp.valueOf(t));
            else ps.setObject(i + 1, a);
        }
    }

    private static Object convert(Object o) {
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        if (o instanceof Timestamp t) return t.toLocalDateTime().withNano(0);
        if (o instanceof LocalDateTime t) return t.withNano(0);
        if (o instanceof BigDecimal b) return b.doubleValue();
        return o;
    }

    // --------------------------------------------------------------- row accessors
    public static int i(Map<String, Object> r, String k) { return ((Number) r.get(k)).intValue(); }
    public static Integer iN(Map<String, Object> r, String k) { Object v = r.get(k); return v == null ? null : ((Number) v).intValue(); }
    public static long l(Map<String, Object> r, String k) { Object v = r.get(k); return v == null ? 0 : ((Number) v).longValue(); }
    public static double d(Map<String, Object> r, String k) { Object v = r.get(k); return v == null ? 0 : ((Number) v).doubleValue(); }
    public static String s(Map<String, Object> r, String k) { Object v = r.get(k); return v == null ? null : v.toString(); }
    public static boolean b(Map<String, Object> r, String k) { return Boolean.TRUE.equals(r.get(k)); }
    public static LocalDate date(Map<String, Object> r, String k) {
        Object v = r.get(k);
        return v instanceof LocalDate d ? d : v == null ? null : LocalDate.parse(v.toString());
    }
}
