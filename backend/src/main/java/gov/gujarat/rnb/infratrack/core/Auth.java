package gov.gujarat.rnb.infratrack.core;

import gov.gujarat.rnb.infratrack.db.Db;
import gov.gujarat.rnb.infratrack.http.ApiException;
import gov.gujarat.rnb.infratrack.http.Json;
import gov.gujarat.rnb.infratrack.http.Req;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/** Password hashing (PBKDF2) and signed session tokens (JWT, HS256). */
public final class Auth {
    private Auth() {}

    private static final String SECRET = System.getenv().getOrDefault("JWT_SECRET", "gujarat-rnb-infratrack-dev-secret");
    private static final long TOKEN_SECONDS = 12 * 3600;
    private static final int ITERATIONS = 60_000;
    private static final SecureRandom RNG = new SecureRandom();

    // ---------------------------------------------------------------- passwords
    public static String hashPassword(String pw) {
        byte[] salt = new byte[8];
        RNG.nextBytes(salt);
        String saltHex = HexFormat.of().formatHex(salt);
        return saltHex + "$" + pbkdf2(pw, saltHex);
    }

    public static boolean verifyPassword(String pw, String stored) {
        int i = stored.indexOf('$');
        if (i < 0) return false;
        String expected = stored.substring(i + 1);
        String actual = pbkdf2(pw, stored.substring(0, i));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String pbkdf2(String pw, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pw.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), ITERATIONS, 256);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- tokens
    public static String createToken(User u) {
        String header = b64(Json.write(M.of("alg", "HS256", "typ", "JWT")));
        long exp = System.currentTimeMillis() / 1000 + TOKEN_SECONDS;
        String payload = b64(Json.write(M.of("sub", String.valueOf(u.id()), "role", u.role(), "exp", exp)));
        return header + "." + payload + "." + sign(header + "." + payload);
    }

    /** Returns the user id if the token is valid and unexpired, else null. */
    @SuppressWarnings("unchecked")
    public static Integer verifyToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;
        if (!MessageDigest.isEqual(sign(parts[0] + "." + parts[1]).getBytes(), parts[2].getBytes())) return null;
        try {
            Map<String, Object> p = (Map<String, Object>) Json.parse(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            if (((Number) p.get("exp")).longValue() < System.currentTimeMillis() / 1000) return null;
            return Integer.parseInt(p.get("sub").toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- request guards
    public static User optionalUser(Req r) {
        String h = r.headers.getFirst("Authorization");
        if (h == null || !h.startsWith("Bearer ")) return null;
        Integer id = verifyToken(h.substring(7).trim());
        if (id == null) return null;
        Map<String, Object> row = Db.one("SELECT * FROM users WHERE id = ?", id);
        return row == null ? null : User.from(row);
    }

    public static User user(Req r) {
        User u = optionalUser(r);
        if (u == null) throw new ApiException(401, "Sign in to continue");
        return u;
    }

    public static User require(Req r, String... roles) {
        User u = user(r);
        if (!u.is(roles)) throw ApiException.forbidden("Your role does not have access to this section");
        return u;
    }

    public static User officer(Req r) {
        return require(r, "state_admin", "regional_officer", "division_engineer", "subdivision_engineer");
    }
}
