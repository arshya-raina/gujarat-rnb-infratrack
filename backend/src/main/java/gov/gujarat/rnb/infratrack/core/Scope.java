package gov.gujarat.rnb.infratrack.core;

import gov.gujarat.rnb.infratrack.db.Where;
import gov.gujarat.rnb.infratrack.http.ApiException;

/** Jurisdiction rules: which rows each role may see. Applied to every query, not just hidden in the UI. */
public final class Scope {
    private Scope() {}

    /** A WHERE clause limiting a table with region_id/division_id/subdivision_id columns to the user's jurisdiction. */
    public static Where of(User u, boolean isProjectTable) {
        Where w = new Where();
        switch (u.role()) {
            case "state_admin" -> { }
            case "regional_officer" -> w.and("region_id = ?", u.regionId());
            case "division_engineer" -> w.and("division_id = ?", u.divisionId());
            case "subdivision_engineer" -> w.and("subdivision_id = ?", u.subdivisionId());
            case "contractor" -> {
                if (isProjectTable) w.and("contractor_id = ?", u.contractorId());
                else w.and("1 = 0");
            }
            default -> w.and("1 = 0");
        }
        return w;
    }

    public static Where assets(User u) { return of(u, false); }
    public static Where projects(User u) { return of(u, true); }
    public static Where complaints(User u) { return of(u, false); }

    public static boolean canAccess(User u, Integer regionId, Integer divisionId, Integer subdivisionId) {
        return switch (u.role()) {
            case "state_admin" -> true;
            case "regional_officer" -> regionId != null && regionId.equals(u.regionId());
            case "division_engineer" -> divisionId != null && divisionId.equals(u.divisionId());
            case "subdivision_engineer" -> subdivisionId != null && subdivisionId.equals(u.subdivisionId());
            default -> false;
        };
    }

    public static void ensure(User u, Integer regionId, Integer divisionId, Integer subdivisionId) {
        if (!canAccess(u, regionId, divisionId, subdivisionId))
            throw ApiException.forbidden("This record is outside your jurisdiction");
    }

    /** The top of the hierarchy this user may see: {level, id}. */
    public static String rootLevel(User u) {
        return switch (u.role()) {
            case "regional_officer" -> "region";
            case "division_engineer" -> "division";
            case "subdivision_engineer" -> "subdivision";
            default -> "state";
        };
    }

    public static Integer rootId(User u) {
        return switch (u.role()) {
            case "regional_officer" -> u.regionId();
            case "division_engineer" -> u.divisionId();
            case "subdivision_engineer" -> u.subdivisionId();
            default -> null;
        };
    }

    public static String label(User u) {
        return switch (rootLevel(u)) {
            case "region" -> Hierarchy.region(u.regionId()).circleName();
            case "division" -> Hierarchy.division(u.divisionId()).name();
            case "subdivision" -> Hierarchy.subdivision(u.subdivisionId()).name();
            default -> "State of Gujarat";
        };
    }
}
