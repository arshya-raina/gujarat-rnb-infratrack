package gov.gujarat.rnb.infratrack;

import com.sun.net.httpserver.HttpServer;
import gov.gujarat.rnb.infratrack.api.*;
import gov.gujarat.rnb.infratrack.core.Hierarchy;
import gov.gujarat.rnb.infratrack.core.M;
import gov.gujarat.rnb.infratrack.core.Seed;
import gov.gujarat.rnb.infratrack.db.Db;
import gov.gujarat.rnb.infratrack.db.Schema;
import gov.gujarat.rnb.infratrack.http.Router;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * Gujarat R&B InfraTrack: Statewide Infrastructure Asset & Lifecycle Management Platform.
 * Starts the embedded H2 database, generates the demo dataset on first run, and serves the API and web app.
 *
 * Usage: java -jar infratrack.jar [--reseed]
 */
public final class App {
    public static void main(String[] args) throws Exception {
        Path root = findProjectRoot();
        Path dataDir = root.resolve("data");
        Files.createDirectories(dataDir);
        String dbUrl = System.getenv().getOrDefault("DATABASE_URL",
                "jdbc:h2:" + dataDir.resolve("infratrack").toAbsolutePath() + ";DB_CLOSE_DELAY=-1");
        Db.init(dbUrl);

        boolean reseed = args.length > 0 && args[0].equals("--reseed");
        if (reseed || !Schema.exists()) {
            System.out.println("InfraTrack: generating the Gujarat demo dataset...");
            Seed.run();
        }
        Hierarchy.load();

        Router router = new Router(root.resolve("frontend").resolve("dist").toAbsolutePath().normalize());
        router.get("/api/health", req -> M.of("status", "ok", "service", "Gujarat R&B InfraTrack"));
        AuthApi.register(router);
        DashboardApi.register(router);
        AssetApi.register(router);
        ProjectApi.register(router);
        CitizenApi.register(router);

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", router);
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();
        System.out.println();
        System.out.println("  Gujarat R&B InfraTrack is running.");
        System.out.println("  Open http://localhost:" + port + " in your browser. Press Ctrl+C to stop.");
        System.out.println();
    }

    /** The folder that contains frontend/ (works whether started from the project root or from backend/). */
    private static Path findProjectRoot() {
        Path p = Path.of("").toAbsolutePath();
        for (int i = 0; i < 3 && p != null; i++, p = p.getParent())
            if (Files.isDirectory(p.resolve("frontend"))) return p;
        return Path.of("").toAbsolutePath();
    }
}
