package gov.gujarat.rnb.infratrack.core;

import gov.gujarat.rnb.infratrack.db.Db;
import java.util.Map;
import java.util.Set;

/** A signed-in user with a role and a jurisdiction. */
public record User(int id, String email, String name, String designation, String role,
                   Integer regionId, Integer divisionId, Integer subdivisionId, Integer contractorId) {

    public static final Set<String> OFFICER_ROLES = Set.of("state_admin", "regional_officer", "division_engineer", "subdivision_engineer");

    public static final Map<String, String> ROLE_LABELS = Map.of(
            "state_admin", "State Administrator",
            "regional_officer", "Chief / Regional Officer",
            "division_engineer", "Division Engineer",
            "subdivision_engineer", "Sub-Division Engineer",
            "contractor", "Contractor",
            "citizen", "Citizen");

    public static User from(Map<String, Object> r) {
        return new User(Db.i(r, "id"), Db.s(r, "email"), Db.s(r, "name"), Db.s(r, "designation"), Db.s(r, "role"),
                Db.iN(r, "region_id"), Db.iN(r, "division_id"), Db.iN(r, "subdivision_id"), Db.iN(r, "contractor_id"));
    }

    public boolean isOfficer() { return OFFICER_ROLES.contains(role); }
    public boolean is(String... roles) { for (String r : roles) if (r.equals(role)) return true; return false; }
}
