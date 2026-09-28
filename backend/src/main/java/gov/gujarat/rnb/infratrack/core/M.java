package gov.gujarat.rnb.infratrack.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Ordered map builder that allows null values: M.of("a", 1, "b", null). */
public final class M {
    private M() {}

    public static Map<String, Object> of(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    public static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }
}
