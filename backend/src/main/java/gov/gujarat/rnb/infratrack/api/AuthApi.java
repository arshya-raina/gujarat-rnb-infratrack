package gov.gujarat.rnb.infratrack.api;

import gov.gujarat.rnb.infratrack.core.*;
import gov.gujarat.rnb.infratrack.db.Db;
import gov.gujarat.rnb.infratrack.http.ApiException;
import gov.gujarat.rnb.infratrack.http.Router;

import java.util.*;

/** /api/auth: sign in, current user, demo accounts. */
public final class AuthApi {
    private AuthApi() {}

    public static void register(Router r) {
        r.post("/api/auth/login", req -> {
            String email = req.str("email", "").toLowerCase(Locale.ROOT);
            String password = req.str("password", "");
            Map<String, Object> row = Db.one("SELECT * FROM users WHERE email = ?", email);
            if (row == null || !Auth.verifyPassword(password, Db.s(row, "password_hash")))
                throw new ApiException(401, "Email or password is incorrect");
            User u = User.from(row);
            return M.of("token", Auth.createToken(u), "user", userOut(u));
        });

        r.get("/api/auth/me", req -> userOut(Auth.user(req)));

        r.get("/api/auth/demo-accounts", req -> {
            List<Map<String, Object>> out = new ArrayList<>();
            for (var row : Db.query("SELECT * FROM users ORDER BY id")) {
                User u = User.from(row);
                out.add(M.of("email", u.email(), "name", u.name(), "role", u.role(),
                        "role_label", User.ROLE_LABELS.get(u.role()), "designation", u.designation()));
            }
            return out;
        });
    }

    static Map<String, Object> userOut(User u) {
        String contractorName = u.contractorId() == null ? null
                : (String) Db.scalar("SELECT name FROM contractors WHERE id = ?", u.contractorId());
        return M.of(
                "id", u.id(), "email", u.email(), "name", u.name(), "designation", u.designation(),
                "role", u.role(), "role_label", User.ROLE_LABELS.get(u.role()),
                "region_id", u.regionId(), "division_id", u.divisionId(), "subdivision_id", u.subdivisionId(),
                "contractor_id", u.contractorId(), "contractor_name", contractorName,
                "root", M.of("level", Scope.rootLevel(u), "id", Scope.rootId(u)),
                "jurisdiction", u.isOfficer() ? Scope.label(u) : null);
    }
}
