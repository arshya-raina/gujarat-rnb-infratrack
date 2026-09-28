package gov.gujarat.rnb.infratrack.core;

import gov.gujarat.rnb.infratrack.db.Db;

import java.util.*;

/** In-memory cache of the (static) Circle → Division → Sub-Division tree. */
public final class Hierarchy {
    private Hierarchy() {}

    public record Region(int id, String code, String name, String circleName, double lat, double lng, boolean isFocus) {}
    public record Division(int id, String code, String name, int regionId, double lat, double lng) {}
    public record SubDivision(int id, String code, String name, int divisionId, int regionId, double lat, double lng) {}

    private static final Map<Integer, Region> REGIONS = new LinkedHashMap<>();
    private static final Map<Integer, Division> DIVISIONS = new LinkedHashMap<>();
    private static final Map<Integer, SubDivision> SUBS = new LinkedHashMap<>();

    public static synchronized void load() {
        REGIONS.clear(); DIVISIONS.clear(); SUBS.clear();
        for (var r : Db.query("SELECT * FROM regions ORDER BY id"))
            REGIONS.put(Db.i(r, "id"), new Region(Db.i(r, "id"), Db.s(r, "code"), Db.s(r, "name"), Db.s(r, "circle_name"),
                    Db.d(r, "lat"), Db.d(r, "lng"), Db.b(r, "is_focus")));
        for (var r : Db.query("SELECT * FROM divisions ORDER BY id"))
            DIVISIONS.put(Db.i(r, "id"), new Division(Db.i(r, "id"), Db.s(r, "code"), Db.s(r, "name"), Db.i(r, "region_id"),
                    Db.d(r, "lat"), Db.d(r, "lng")));
        for (var r : Db.query("SELECT * FROM subdivisions ORDER BY id"))
            SUBS.put(Db.i(r, "id"), new SubDivision(Db.i(r, "id"), Db.s(r, "code"), Db.s(r, "name"), Db.i(r, "division_id"),
                    Db.i(r, "region_id"), Db.d(r, "lat"), Db.d(r, "lng")));
    }

    public static Region region(Integer id) { return id == null ? null : REGIONS.get(id); }
    public static Division division(Integer id) { return id == null ? null : DIVISIONS.get(id); }
    public static SubDivision subdivision(Integer id) { return id == null ? null : SUBS.get(id); }

    public static Collection<Region> regions() { return REGIONS.values(); }
    public static Collection<Division> divisions() { return DIVISIONS.values(); }
    public static Collection<SubDivision> subdivisions() { return SUBS.values(); }

    public static List<Division> divisionsOf(int regionId) {
        return DIVISIONS.values().stream().filter(d -> d.regionId() == regionId).toList();
    }

    public static List<SubDivision> subdivisionsOf(int divisionId) {
        return SUBS.values().stream().filter(s -> s.divisionId() == divisionId).toList();
    }
}
