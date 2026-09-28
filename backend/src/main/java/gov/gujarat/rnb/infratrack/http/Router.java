package gov.gujarat.rnb.infratrack.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Routes /api/* requests to handlers and serves the built React app for everything else. */
public final class Router implements HttpHandler {

    @FunctionalInterface
    public interface Handler { Object handle(Req r) throws Exception; }

    private record Route(String method, Pattern pattern, List<String> names, Handler handler) {}

    private final List<Route> routes = new ArrayList<>();
    private final Path staticRoot;

    public Router(Path staticRoot) { this.staticRoot = staticRoot; }

    public void get(String path, Handler h) { add("GET", path, h); }
    public void post(String path, Handler h) { add("POST", path, h); }
    public void patch(String path, Handler h) { add("PATCH", path, h); }

    private void add(String method, String path, Handler h) {
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("\\{(\\w+)}").matcher(path);
        StringBuilder rx = new StringBuilder("^");
        int last = 0;
        while (m.find()) {
            rx.append(Pattern.quote(path.substring(last, m.start()))).append("([^/]+)");
            names.add(m.group(1));
            last = m.end();
        }
        rx.append(Pattern.quote(path.substring(last))).append("/?$");
        routes.add(new Route(method, Pattern.compile(rx.toString()), names, h));
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PATCH, OPTIONS");
            if (ex.getRequestMethod().equals("OPTIONS")) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (path.startsWith("/api/")) api(ex, path);
            else serveStatic(ex, path);
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, Map.of("detail", "Internal server error"));
        } finally {
            ex.close();
        }
    }

    private void api(HttpExchange ex, String path) throws IOException {
        String method = ex.getRequestMethod();
        boolean pathMatched = false;
        for (Route r : routes) {
            Matcher m = r.pattern.matcher(path);
            if (!m.matches()) continue;
            pathMatched = true;
            if (!r.method.equals(method)) continue;
            Map<String, String> params = new HashMap<>();
            for (int i = 0; i < r.names.size(); i++) params.put(r.names.get(i), decode(m.group(i + 1)));
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Req req = new Req(method, path, parseQuery(ex.getRequestURI().getRawQuery()), params, ex.getRequestHeaders(), body);
            try {
                Object out = r.handler.handle(req);
                sendJson(ex, 200, out);
            } catch (ApiException e) {
                sendJson(ex, e.status, Map.of("detail", e.getMessage()));
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(ex, 500, Map.of("detail", "Internal server error"));
            }
            return;
        }
        sendJson(ex, pathMatched ? 405 : 404, Map.of("detail", pathMatched ? "Method not allowed" : "Not found"));
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> q = new HashMap<>();
        if (raw == null || raw.isEmpty()) return q;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            if (i < 0) q.put(decode(pair), "");
            else q.put(decode(pair.substring(0, i)), decode(pair.substring(i + 1)));
        }
        return q;
    }

    private static String decode(String s) { return URLDecoder.decode(s, StandardCharsets.UTF_8); }

    public static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    // ------------------------------------------------------------------ static files (React build)
    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8", "js", "application/javascript", "css", "text/css",
            "svg", "image/svg+xml", "png", "image/png", "ico", "image/x-icon", "json", "application/json",
            "woff2", "font/woff2", "txt", "text/plain");

    private void serveStatic(HttpExchange ex, String path) throws IOException {
        if (staticRoot == null || !Files.isDirectory(staticRoot)) {
            byte[] msg = ("InfraTrack API is running. The web interface was not found at frontend/dist. "
                    + "Build it with: cd frontend && npm install && npm run build").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(200, msg.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(msg); }
            return;
        }
        Path file = staticRoot.resolve(path.substring(1)).normalize();
        if (!file.startsWith(staticRoot) || !Files.isRegularFile(file)) file = staticRoot.resolve("index.html");
        String name = file.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", TYPES.getOrDefault(ext, "application/octet-stream"));
        if (path.startsWith("/assets/")) ex.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
