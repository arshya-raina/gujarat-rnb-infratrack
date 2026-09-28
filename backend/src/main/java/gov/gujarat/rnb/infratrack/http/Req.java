package gov.gujarat.rnb.infratrack.http;

import com.sun.net.httpserver.Headers;
import java.util.Map;

/** An incoming API request: path parameters, query string and JSON body. */
public final class Req {
    public final String method;
    public final String path;
    public final Map<String, String> query;
    public final Map<String, String> params;
    public final Headers headers;
    private final String body;
    private Map<String, Object> json;

    public Req(String method, String path, Map<String, String> query, Map<String, String> params, Headers headers, String body) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.params = params;
        this.headers = headers;
        this.body = body;
    }

    public String param(String k) { return params.get(k); }

    public int paramInt(String k) {
        try { return Integer.parseInt(params.get(k)); }
        catch (NumberFormatException e) { throw ApiException.notFound("Record"); }
    }

    public String q(String k) {
        String v = query.get(k);
        return v == null || v.isBlank() ? null : v.trim();
    }

    public Integer qInt(String k) {
        String v = q(k);
        if (v == null) return null;
        try { return Integer.parseInt(v); }
        catch (NumberFormatException e) { throw ApiException.bad("'" + k + "' must be a number"); }
    }

    public int qInt(String k, int def, int min, int max) {
        Integer v = qInt(k);
        if (v == null) return def;
        return Math.max(min, Math.min(max, v));
    }

    public boolean qBool(String k) {
        String v = q(k);
        return v != null && (v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes"));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> json() {
        if (json == null) {
            try {
                Object o = Json.parse(body);
                if (!(o instanceof Map)) throw ApiException.bad("Request body must be a JSON object");
                json = (Map<String, Object>) o;
            } catch (IllegalArgumentException | StringIndexOutOfBoundsException e) {
                throw ApiException.bad("Request body is not valid JSON");
            }
        }
        return json;
    }

    public String str(String k, String def) {
        Object v = json().get(k);
        return v == null ? def : v.toString().trim();
    }

    public String requiredStr(String k, int minLen, String label) {
        String v = str(k, "");
        if (v.length() < minLen) throw ApiException.bad(label + (minLen > 1 ? " must be at least " + minLen + " characters" : " is required"));
        return v;
    }

    public double num(String k, String label, double min, double max) {
        Object v = json().get(k);
        double d;
        if (v instanceof Number n) d = n.doubleValue();
        else {
            try { d = Double.parseDouble(String.valueOf(v)); }
            catch (Exception e) { throw ApiException.bad(label + " must be a number"); }
        }
        if (d < min || d > max) throw ApiException.bad(label + " must be between " + fmt(min) + " and " + fmt(max));
        return d;
    }

    private static String fmt(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); }
}
