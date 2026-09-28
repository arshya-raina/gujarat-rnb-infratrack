package gov.gujarat.rnb.infratrack.core;

import gov.gujarat.rnb.infratrack.db.Db;
import gov.gujarat.rnb.infratrack.db.Schema;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Generates the fictional Gujarat R&B demo dataset. Headline figures are fixed so the demo always opens on
 * 18,492 assets / 1,283 active projects / 427 maintenance due / 61 critical.
 */
public final class Seed {
    private Seed() {}

    private static final Random R = new Random(42);

    // ---------------------------------------------------------------- random helpers
    private static double uni(double lo, double hi) { return lo + R.nextDouble() * (hi - lo); }
    private static int randint(int lo, int hi) { return lo + R.nextInt(hi - lo + 1); }
    private static <T> T pick(List<T> l) { return l.get(R.nextInt(l.size())); }
    private static <T> T pick(T[] a) { return a[R.nextInt(a.length)]; }
    private static double r1(double v) { return M.round(v, 1); }
    private static double r2(double v) { return M.round(v, 2); }

    private static <T> T weighted(List<T> items, double[] w) {
        double total = 0;
        for (double x : w) total += x;
        double t = R.nextDouble() * total;
        for (int i = 0; i < w.length; i++) {
            t -= w[i];
            if (t <= 0) return items.get(i);
        }
        return items.get(items.size() - 1);
    }

    // ---------------------------------------------------------------- reference data
    private record Sub(String name, double lat, double lng) {}
    private record Div(String name, List<Sub> subs) {}
    private record Reg(String code, String name, String circle, double lat, double lng, int count, boolean focus, String[] rivers, List<Div> divs) {}

    private static Sub s(String n, double lat, double lng) { return new Sub(n, lat, lng); }
    private static Div d(String n, Sub... subs) { return new Div(n, List.of(subs)); }

    private static final List<Reg> GEO = List.of(
        new Reg("AMD", "Ahmedabad", "Ahmedabad Circle", 23.0225, 72.5714, 4281, true, new String[]{"Sabarmati", "Khari", "Bhogavo"}, List.of(
            d("Ahmedabad City Division", s("Ahmedabad East", 23.03, 72.62), s("Ahmedabad West", 23.03, 72.53), s("Maninagar", 22.99, 72.60)),
            d("Ahmedabad Rural Division", s("Sanand", 22.99, 72.38), s("Dholka", 22.73, 72.44), s("Viramgam", 23.12, 72.03)),
            d("Dholera-Bavla Division", s("Dholera", 22.25, 72.19), s("Bavla", 22.83, 72.36), s("Dhandhuka", 22.38, 71.98)))),
        new Reg("GNR", "Gandhinagar", "Gandhinagar Capital Circle", 23.2156, 72.6369, 2104, true, new String[]{"Sabarmati", "Meshwo", "Khari"}, List.of(
            d("Capital Project Division", s("Gandhinagar North", 23.23, 72.65), s("Gandhinagar South", 23.19, 72.63), s("GIFT City", 23.16, 72.68)),
            d("Gandhinagar Rural Division", s("Kalol", 23.24, 72.50), s("Dehgam", 23.17, 72.82), s("Mansa", 23.43, 72.66)))),
        new Reg("SRT", "Surat", "Surat Circle", 21.1702, 72.8311, 3762, true, new String[]{"Tapi", "Purna", "Mindhola", "Ambika"}, List.of(
            d("Surat City Division", s("Athwa", 21.18, 72.81), s("Varachha", 21.21, 72.87), s("Udhna", 21.17, 72.84)),
            d("Surat Rural Division", s("Bardoli", 21.12, 73.11), s("Kamrej", 21.27, 72.96), s("Olpad", 21.33, 72.75)),
            d("Navsari-Tapi Division", s("Navsari", 20.95, 72.92), s("Vyara", 21.11, 73.39), s("Songadh", 21.17, 73.56)))),
        new Reg("VDR", "Vadodara", "Vadodara Circle", 22.3072, 73.1812, 3019, true, new String[]{"Mahi", "Vishwamitri", "Dhadhar", "Narmada"}, List.of(
            d("Vadodara City Division", s("Alkapuri", 22.31, 73.17), s("Makarpura", 22.24, 73.19)),
            d("Vadodara Rural Division", s("Padra", 22.24, 73.08), s("Karjan", 22.05, 73.12), s("Dabhoi", 22.13, 73.41), s("Savli", 22.57, 73.22)),
            d("Chhota Udepur Division", s("Bodeli", 22.27, 73.72), s("Chhota Udepur", 22.30, 74.01)))),
        new Reg("RJT", "Rajkot", "Rajkot Circle", 22.3039, 70.8022, 2871, true, new String[]{"Aji", "Bhadar", "Machhu", "Nyari"}, List.of(
            d("Rajkot City Division", s("Rajkot East", 22.30, 70.83), s("Rajkot West", 22.29, 70.77)),
            d("Rajkot Rural Division", s("Gondal", 21.96, 70.80), s("Jetpur", 21.75, 70.62), s("Dhoraji", 21.73, 70.45), s("Jasdan", 22.04, 71.20)),
            d("Morbi Division", s("Morbi", 22.82, 70.84), s("Wankaner", 22.61, 70.94)))),
        new Reg("BVN", "Bhavnagar", "Bhavnagar Circle", 21.7645, 72.1519, 912, false, new String[]{"Shetrunji", "Kalubhar"}, List.of(
            d("Bhavnagar Division", s("Bhavnagar", 21.76, 72.15), s("Mahuva", 21.09, 71.76), s("Palitana", 21.52, 71.83)))),
        new Reg("KUT", "Kutch", "Kutch (Bhuj) Circle", 23.2420, 69.6669, 868, false, new String[]{"Rukmavati", "Khari"}, List.of(
            d("Bhuj Division", s("Bhuj", 23.24, 69.67), s("Anjar", 23.11, 70.03), s("Mundra", 22.84, 69.72), s("Gandhidham", 23.08, 70.13)))),
        new Reg("JND", "Junagadh", "Junagadh Circle", 21.5222, 70.4579, 675, false, new String[]{"Ozat", "Hiran", "Uben"}, List.of(
            d("Junagadh Division", s("Junagadh", 21.52, 70.46), s("Keshod", 21.30, 70.25), s("Veraval", 20.91, 70.37))))
    );

    private static final String[] VILLAGES = {"Kherva", "Pipli", "Vasna", "Bhadaj", "Limbdi", "Rampura", "Moti Bhamti", "Nana Vadala",
        "Kanpur", "Ambli", "Karamsad", "Undera", "Sokhda", "Varsada", "Bhimasar", "Chandisar", "Lakhtar", "Sarkhej", "Timbi", "Kosamba",
        "Ghoghla", "Devgadh", "Jambudi", "Paldi", "Navagam", "Juna Deesa", "Motipura", "Bhalej", "Kanodar", "Samarkha", "Harij", "Vadgam",
        "Umrala", "Kotda", "Sultanpur", "Khambhalia", "Mota Varachha", "Asoda", "Chikhli", "Valod", "Mandvi", "Sayla", "Rajpur", "Hathijan",
        "Bopal", "Adalaj", "Shertha", "Ranip", "Zundal", "Medra"};

    private static final String[] FIRMS = {"Sabarmati Infracon Pvt. Ltd.", "Narmada Buildcon LLP", "Tapi Roadways & Engineers",
        "Saurashtra Highways Pvt. Ltd.", "Kutch Rann Constructions", "Girnar Infrastructure Co.", "Mahi Bridge Engineers",
        "Vishwamitri Civil Works", "Dwarka Projects Pvt. Ltd.", "Aravalli Infratech", "Shetrunji Builders", "Somnath Engineering Co.",
        "Lothal Structures Pvt. Ltd.", "Pavagadh Roads LLP", "Gir Civil Contractors", "Dholavira Infra Pvt. Ltd.", "Champaner Constructions",
        "Rann Utsav Builders", "Modhera Engineers", "Patan Patola Infra LLP", "Saputara Hill Works", "Bhadar Infraprojects",
        "Ambaji Engineering Works", "Velavadar Constructions"};

    private static final String[] SCHEMES = {"Mukhyamantri Gram Sadak Yojana", "PMGSY", "Kisan Path Yojana", "State Plan (R&B)", "NABARD RIDF",
        "Central Road & Infrastructure Fund", "Pragati Path", "Vibrant Gujarat Infra Grant"};

    private static final String[] INSPECTORS = {"Dy. EE K. R. Patel", "Dy. EE M. J. Desai", "AE H. N. Shah", "AE P. B. Chaudhary",
        "Dy. EE S. V. Joshi", "AE R. K. Parmar", "Dy. EE A. D. Trivedi", "AE N. G. Solanki", "AE V. M. Rathod", "Dy. EE J. P. Mehta"};

    private static final Map<String, String[]> DEFECTS = Map.of(
        "road", new String[]{"potholes", "edge breaking", "rutting", "alligator cracking", "shoulder erosion", "faded markings"},
        "bridge", new String[]{"spalling of concrete", "exposed reinforcement", "bearing damage", "expansion joint failure", "scour at pier", "railing damage"},
        "culvert", new String[]{"silt blockage", "wing wall cracks", "apron damage", "vegetation growth"},
        "building", new String[]{"roof leakage", "plaster cracks", "dampness", "electrical wear", "plumbing leaks"},
        "facility", new String[]{"roof leakage", "dampness", "fixture damage", "paint deterioration"},
        "other", new String[]{"cracks", "corrosion", "drainage failure", "joint failure"});

    private static final List<String> TYPES = List.of("road", "culvert", "building", "bridge", "facility", "other");
    private static final double[] TYPE_W = {35, 25, 18, 9, 8, 5};

    /** Mutable asset row used during generation. */
    private static final class A {
        int id, regionId, divisionId, subdivisionId, yearBuilt, aadt;
        Integer floors;
        String code, name, type, category, condition, status = "operational";
        double lat, lng, score, value, risk;
        Double lengthKm, spanM;
        LocalDate lastInspection, nextMaintenance;
    }

    private static void profile(A a, String taluka, String[] rivers) {
        String v = pick(VILLAGES);
        switch (a.type) {
            case "road" -> {
                String kind = weighted(List.of("SH", "MDR", "ODR", "VR"), new double[]{20, 35, 30, 15});
                switch (kind) {
                    case "SH" -> { a.name = "SH-" + randint(1, 180) + " " + taluka + "–" + v + " Road"; a.aadt = randint(9000, 42000); a.lengthKm = r1(uni(8, 42)); a.category = "State Highway"; }
                    case "MDR" -> { a.name = taluka + "–" + v + " Major District Road"; a.aadt = randint(3000, 15000); a.lengthKm = r1(uni(4, 22)); a.category = "Major District Road"; }
                    case "ODR" -> { a.name = v + "–" + pick(VILLAGES) + " Other District Road"; a.aadt = randint(800, 5000); a.lengthKm = r1(uni(2, 12)); a.category = "Other District Road"; }
                    default -> { a.name = v + " Village Approach Road"; a.aadt = randint(200, 1500); a.lengthKm = r1(uni(1, 5)); a.category = "Village Road"; }
                }
                a.value = r2(a.lengthKm * uni(0.6, 3.2));
            }
            case "bridge" -> {
                boolean major = R.nextDouble() < 0.35;
                a.spanM = (double) Math.round(major ? uni(60, 820) : uni(12, 60));
                a.name = (major ? "Major" : "Minor") + " Bridge over " + pick(rivers) + " near " + v;
                a.category = major ? "Major Bridge" : "Minor Bridge";
                a.aadt = randint(2000, 38000);
                a.value = r2(a.spanM * uni(0.08, 0.2));
            }
            case "culvert" -> {
                String kind = pick(new String[]{"Box Culvert", "Slab Culvert", "Pipe Culvert"});
                a.name = kind + " at Ch. " + randint(1, 60) + "/" + randint(0, 9) + "00, " + taluka + "–" + v + " Road";
                a.category = kind;
                a.aadt = randint(500, 12000);
                a.value = r2(uni(0.15, 1.8));
            }
            case "building" -> {
                String kind = pick(new String[]{"Primary Health Centre", "Community Health Centre", "Govt. Secondary School", "Taluka Seva Sadan",
                        "Mamlatdar Office", "Police Staff Quarters", "Govt. ITI Workshop", "Anganwadi Centre", "Court Building"});
                String place = Set.of("Taluka Seva Sadan", "Mamlatdar Office", "Court Building").contains(kind) ? taluka : v;
                a.name = kind + ", " + place;
                a.category = kind;
                a.floors = randint(1, 5);
                a.value = r2(uni(0.8, 22));
            }
            case "facility" -> {
                String kind = pick(new String[]{"Circuit House", "R&B Rest House", "Govt. Staff Colony", "Sub-Division Office", "Govt. Guest House"});
                a.name = kind + ", " + taluka;
                a.category = kind;
                a.floors = randint(1, 3);
                a.value = r2(uni(1.2, 14));
            }
            default -> {
                String kind = pick(new String[]{"Road Over Bridge", "Flyover", "Retaining Wall", "Causeway", "Toll Plaza", "Foot Over Bridge"});
                a.name = kind + " at " + v + ", " + taluka;
                a.category = kind;
                a.aadt = randint(2000, 45000);
                a.value = r2(uni(1, 90));
            }
        }
    }

    /** Picks a realistic work for an asset: returns {work_type, name, cost_cr}. */
    private static Object[] projectFor(A a) {
        String n = a.name;
        double v = a.value;
        List<Object[]> opts = new ArrayList<>();
        switch (a.type) {
            case "road" -> {
                double L = a.lengthKm == null ? 5 : a.lengthKm;
                if (a.category.equals("State Highway") || a.category.equals("Major District Road")) {
                    opts.add(new Object[]{"widening", "Widening of " + n + " to 4-Lane", L * 1.4, L * 3.2});
                    opts.add(new Object[]{"strengthening", "Strengthening & Resurfacing of " + n, L * 0.35, L * 0.8});
                    opts.add(new Object[]{"resurfacing", "Periodic Renewal Coat on " + n, L * 0.08, L * 0.18});
                } else {
                    opts.add(new Object[]{"upgradation", "Upgradation & Widening of " + n + " to 7 m", L * 0.5, L * 1.1});
                    opts.add(new Object[]{"resurfacing", "Periodic Renewal Coat on " + n, L * 0.06, L * 0.14});
                    opts.add(new Object[]{"strengthening", "Strengthening of " + n, L * 0.25, L * 0.55});
                }
            }
            case "bridge" -> {
                opts.add(new Object[]{"rehabilitation", "Rehabilitation of " + n, v * 0.12, v * 0.3});
                opts.add(new Object[]{"new_construction", "Construction of New Parallel Bridge – " + n, v * 0.9, v * 1.3});
            }
            case "culvert" -> opts.add(new Object[]{"reconstruction", "Reconstruction of " + n, v * 0.8, v * 1.2});
            case "building" -> {
                opts.add(new Object[]{"renovation", "Renovation of " + n, v * 0.1, v * 0.3});
                opts.add(new Object[]{"new_construction", "Construction of Additional Block – " + n, v * 0.4, v * 0.8});
            }
            case "facility" -> opts.add(new Object[]{"renovation", "Upgradation of " + n, v * 0.15, v * 0.4});
            default -> opts.add(new Object[]{"repair", "Structural Repairs to " + n, v * 0.1, v * 0.3});
        }
        Object[] o = pick(opts);
        return new Object[]{o[0], o[1], r2(Math.max(uni((double) o[2], (double) o[3]), 0.15))};
    }

    // ---------------------------------------------------------------- main
    public static void run() throws Exception {
        long t0 = System.currentTimeMillis();
        LocalDate today = LocalDate.now();
        try (Connection c = Db.conn()) {
            Schema.recreate(c);
            c.setAutoCommit(false);

            // ---- hierarchy
            List<Object[]> regions = new ArrayList<>(), divisions = new ArrayList<>(), subs = new ArrayList<>();
            Map<Integer, List<Object[]>> subsByRegion = new LinkedHashMap<>(); // {subId, divId, name, lat, lng}
            int divId = 0, subId = 0;
            for (int ri = 1; ri <= GEO.size(); ri++) {
                Reg g = GEO.get(ri - 1);
                regions.add(new Object[]{ri, g.code, g.name, g.circle, g.lat, g.lng, g.focus});
                subsByRegion.put(ri, new ArrayList<>());
                int dn = 0;
                for (Div dv : g.divs) {
                    divId++;
                    dn++;
                    double lat = dv.subs.stream().mapToDouble(x -> x.lat).average().orElse(0);
                    double lng = dv.subs.stream().mapToDouble(x -> x.lng).average().orElse(0);
                    divisions.add(new Object[]{divId, g.code + "-D" + dn, dv.name, ri, lat, lng});
                    int sn = 0;
                    for (Sub sb : dv.subs) {
                        subId++;
                        sn++;
                        subs.add(new Object[]{subId, g.code + "-D" + dn + "-S" + sn, sb.name, divId, ri, sb.lat, sb.lng});
                        subsByRegion.get(ri).add(new Object[]{subId, divId, sb.name, sb.lat, sb.lng});
                    }
                }
            }
            Db.batch(c, "INSERT INTO regions (id, code, name, circle_name, lat, lng, is_focus) VALUES (?,?,?,?,?,?,?)", regions);
            Db.batch(c, "INSERT INTO divisions (id, code, name, region_id, lat, lng) VALUES (?,?,?,?,?,?)", divisions);
            Db.batch(c, "INSERT INTO subdivisions (id, code, name, division_id, region_id, lat, lng) VALUES (?,?,?,?,?,?,?)", subs);

            // ---- assets
            List<A> assets = new ArrayList<>();
            int aid = 0;
            for (int ri = 1; ri <= GEO.size(); ri++) {
                Reg g = GEO.get(ri - 1);
                List<Object[]> rs = subsByRegion.get(ri);
                double[] w = new double[rs.size()];
                for (int i = 0; i < w.length; i++) w[i] = uni(0.7, 1.5);
                for (int k = 0; k < g.count; k++) {
                    aid++;
                    Object[] sb = weighted(rs, w);
                    A a = new A();
                    a.id = aid;
                    a.type = weighted(TYPES, TYPE_W);
                    a.regionId = ri;
                    a.subdivisionId = (int) sb[0];
                    a.divisionId = (int) sb[1];
                    profile(a, (String) sb[2], g.rivers);
                    a.code = "GJ-" + g.code + "-" + a.type.substring(0, 3).toUpperCase() + "-" + String.format("%05d", aid);
                    a.lat = M.round((double) sb[3] + uni(-0.09, 0.09), 5);
                    a.lng = M.round((double) sb[4] + uni(-0.09, 0.09), 5);
                    a.yearBuilt = randint(1962, 2023);
                    String band = weighted(List.of("good", "fair", "poor"), new double[]{54, 31, 15});
                    a.score = r1(switch (band) { case "good" -> uni(75, 98); case "fair" -> uni(50, 74.9); default -> uni(26, 49.9); });
                    a.lastInspection = today.minusDays(randint(20, 640));
                    a.nextMaintenance = today.plusDays(randint(12, 900));
                    assets.add(a);
                }
            }
            // exact headline numbers: 61 critical, 427 due for maintenance (critical ones included)
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < assets.size(); i++) idx.add(i);
            Collections.shuffle(idx, R);
            for (int i = 0; i < 61; i++) {
                A a = assets.get(idx.get(i));
                a.score = r1(uni(6, 24.5));
                a.nextMaintenance = today.minusDays(randint(1, 150));
                a.status = pick(new String[]{"operational", "operational", "restricted", "closed"});
                a.yearBuilt = randint(1962, 1995);
            }
            for (int i = 61; i < 427; i++) {
                A a = assets.get(idx.get(i));
                a.score = r1(uni(28, 68));
                a.nextMaintenance = today.minusDays(randint(0, 120));
            }
            List<Object[]> assetRows = new ArrayList<>(), inspRows = new ArrayList<>();
            for (A a : assets) {
                a.condition = Health.band(a.score);
                a.risk = Health.risk(a.score, a.yearBuilt, a.aadt, a.lastInspection, a.type, today);
                assetRows.add(new Object[]{a.id, a.code, a.name, a.type, a.category, a.regionId, a.divisionId, a.subdivisionId, a.lat, a.lng,
                        a.yearBuilt, a.score, a.condition, a.status, a.lastInspection, a.nextMaintenance, a.lengthKm, a.spanM, a.floors,
                        a.aadt, a.value, a.risk});
                String[] defects = DEFECTS.get(a.type);
                double prev = Math.min(a.score + uni(3, 14), 99);
                inspRows.add(new Object[]{a.id, a.lastInspection.minusDays(randint(300, 420)), pick(INSPECTORS), r1(prev),
                        pick(defects), "routine", "Periodic inspection"});
                String d2 = a.score > 60 ? pick(defects) : pick(defects) + ", " + pick(defects);
                inspRows.add(new Object[]{a.id, a.lastInspection, pick(INSPECTORS), a.score, d2,
                        a.score < 25 ? "urgent" : a.score < 50 ? "repair" : "routine", "Condition recorded during annual survey"});
            }
            Db.batch(c, "INSERT INTO assets (id, asset_code, name, asset_type, category, region_id, division_id, subdivision_id, lat, lng, "
                    + "year_built, condition_score, condition, status, last_inspection_date, next_maintenance_date, length_km, span_m, floors, "
                    + "traffic_aadt, replacement_value_cr, risk_score) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", assetRows);
            Db.batch(c, "INSERT INTO inspections (asset_id, date, inspector, condition_score, defects, action, remarks) VALUES (?,?,?,?,?,?,?)", inspRows);

            // ---- contractors
            List<Object[]> firms = new ArrayList<>();
            for (int i = 0; i < FIRMS.length; i++)
                firms.add(new Object[]{i + 1, FIRMS[i], "GJ/R&B/" + pick(new String[]{"AA", "A", "B"}) + "/" + (2009 + i) + "/" + randint(100, 999),
                        pick(new String[]{"AA", "AA", "A", "B"}), r1(uni(3.1, 4.9))});
            Db.batch(c, "INSERT INTO contractors (id, name, registration_no, contractor_class, rating) VALUES (?,?,?,?,?)", firms);

            // ---- projects: exactly 1,283 in progress
            Object[][] statusCounts = {{"in_progress", 1283}, {"completed", 412}, {"sanctioned", 196}, {"tendering", 138}, {"on_hold", 41}};
            Map<Integer, List<A>> byRegion = new LinkedHashMap<>();
            for (A a : assets) byRegion.computeIfAbsent(a.regionId, k -> new ArrayList<>()).add(a);
            List<Integer> regionIds = new ArrayList<>(byRegion.keySet());
            double[] regionW = regionIds.stream().mapToDouble(r -> byRegion.get(r).size()).toArray();
            List<Integer> firmIds = new ArrayList<>();
            double[] firmW = new double[FIRMS.length];
            for (int i = 0; i < FIRMS.length; i++) { firmIds.add(i + 1); firmW[i] = i == 0 ? 1.3 : 1; }

            List<Object[]> projRows = new ArrayList<>(), updRows = new ArrayList<>(), billRows = new ArrayList<>();
            String[] notes = {"Earthwork completed in stretch", "GSB & WMM layers laid", "Bituminous work in progress", "Pier cap casting done",
                    "Slab casting completed", "Brickwork up to lintel level", "Finishing work in progress", "Material testing cleared by QC lab",
                    "Monsoon slowdown; resumed work", "Utility shifting awaited from GEB", "Traffic diversion in place"};
            int pid = 0;
            for (Object[] sc : statusCounts) {
                String status = (String) sc[0];
                for (int k = 0; k < (int) sc[1]; k++) {
                    pid++;
                    int ri = weighted(regionIds, regionW);
                    A a = pick(byRegion.get(ri));
                    Object[] pf = projectFor(a);
                    double cost = (double) pf[2], prog, spent;
                    LocalDate start, target;
                    switch (status) {
                        case "completed" -> {
                            start = today.minusDays(randint(500, 1400)); target = start.plusDays(randint(240, 720));
                            prog = 100; spent = r2(cost * uni(0.92, 1.06));
                        }
                        case "in_progress" -> {
                            start = today.minusDays(randint(40, 700)); target = start.plusDays(randint(300, 900));
                            double expected = Health.expectedProgress(start, target, today);
                            prog = r1(Math.min(Math.max(expected + uni(-16, 18), 3), 97));
                            spent = r2(cost * prog / 100 * uni(0.85, 1.1));
                        }
                        case "on_hold" -> {
                            start = today.minusDays(randint(200, 800)); target = start.plusDays(randint(300, 700));
                            prog = r1(uni(10, 60)); spent = r2(cost * prog / 100);
                        }
                        default -> {
                            start = today.plusDays(randint(20, 200)); target = start.plusDays(randint(300, 800));
                            prog = 0; spent = 0;
                        }
                    }
                    Integer contractor = status.equals("sanctioned") || status.equals("tendering") ? null : weighted(firmIds, firmW);
                    boolean delayed = Health.isDelayed(status, prog, start, target, today);
                    projRows.add(new Object[]{pid, "RB/" + GEO.get(ri - 1).code + "/" + (2022 + pid % 5) + "/" + String.format("%05d", pid),
                            pf[1], pf[0], pick(SCHEMES), a.id, ri, a.divisionId, a.subdivisionId, contractor, cost, spent, prog, start, target,
                            status, delayed});

                    if (status.equals("in_progress") || status.equals("completed") || status.equals("on_hold")) {
                        int n = randint(2, 4);
                        long span = Math.min(ChronoUnit.DAYS.between(start, today), 900);
                        for (int j = 1; j <= n; j++) {
                            double frac = j / (double) n;
                            LocalDate dt = start.plusDays((long) (span * frac));
                            if (dt.isAfter(today)) dt = today;
                            updRows.add(new Object[]{pid, dt, r1(prog * frac), pick(notes), "Contractor site engineer"});
                        }
                        double billed = spent * 100;
                        int nb = randint(1, 3);
                        for (int j = 1; j <= nb; j++) {
                            boolean last = j == nb && status.equals("in_progress");
                            billRows.add(new Object[]{pid, "RA-" + j, r2(billed / nb), today.minusDays((long) (nb - j) * 75 + randint(3, 40)),
                                    last ? pick(new String[]{"submitted", "verified"}) : "paid", ""});
                        }
                    }
                }
            }
            Db.batch(c, "INSERT INTO projects (id, code, name, work_type, scheme, asset_id, region_id, division_id, subdivision_id, contractor_id, "
                    + "sanctioned_cost_cr, expenditure_cr, physical_progress, start_date, target_date, status, is_delayed) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", projRows);
            Db.batch(c, "INSERT INTO progress_updates (project_id, date, physical_progress, remarks, submitted_by) VALUES (?,?,?,?,?)", updRows);
            Db.batch(c, "INSERT INTO bills (project_id, bill_no, amount_lakh, submitted_on, status, remarks) VALUES (?,?,?,?,?,?)", billRows);

            // ---- demo users (password: demo123)
            List<Object[]> users = new ArrayList<>();
            int suratCity = 6, athwa = 16; // stable ids from the hierarchy above
            String pw = Auth.hashPassword("demo123");
            users.add(new Object[]{"admin@rnb.gujarat.gov.in", "Smt. Anjali Mehta", "Secretary, R&B Department", pw, "state_admin", null, null, null, null});
            users.add(new Object[]{"ce.surat@rnb.gujarat.gov.in", "Shri Rajesh Desai", "Superintending Engineer, Surat Circle", pw, "regional_officer", 3, null, null, null});
            users.add(new Object[]{"ee.suratcity@rnb.gujarat.gov.in", "Shri Vikram Chaudhary", "Executive Engineer, Surat City Division", pw, "division_engineer", 3, suratCity, null, null});
            users.add(new Object[]{"dyee.athwa@rnb.gujarat.gov.in", "Ms. Priya Solanki", "Dy. Executive Engineer, Athwa Sub-Division", pw, "subdivision_engineer", 3, suratCity, athwa, null});
            users.add(new Object[]{"contractor@sabarmatiinfra.in", "Shri Kalpesh Shah", "Director, Sabarmati Infracon Pvt. Ltd.", pw, "contractor", null, null, null, 1});
            users.add(new Object[]{"citizen@example.com", "Ravi Patel", "Citizen, Surat", pw, "citizen", null, null, null, null});
            Db.batch(c, "INSERT INTO users (email, name, designation, password_hash, role, region_id, division_id, subdivision_id, contractor_id) "
                    + "VALUES (?,?,?,?,?,?,?,?,?)", users);

            // ---- complaints
            String[][] cats = {{"Pothole on road", "medium"}, {"Damaged bridge railing", "high"}, {"Cracks in bridge", "high"},
                    {"Waterlogging on road", "medium"}, {"Blocked culvert", "medium"}, {"Missing road signage", "low"},
                    {"Damaged government building", "medium"}, {"Road cave-in", "high"}, {"Broken divider", "low"}};
            String[] names = {"Rakesh Patel", "Hetal Shah", "Imran Shaikh", "Kinjal Desai", "Mahesh Parmar", "Nirali Joshi",
                    "Jignesh Chaudhary", "Farhana Pathan", "Bhavesh Solanki", "Pooja Mehta", "Harshad Rathod", "Sonal Trivedi"};
            List<Object[]> allSubs = new ArrayList<>();
            subsByRegion.forEach((ri, l) -> l.forEach(x -> allSubs.add(new Object[]{x[0], x[1], ri, x[2], x[3], x[4]})));
            List<Object[]> compRows = new ArrayList<>();
            for (int i = 1; i <= 320; i++) {
                Object[] sb = pick(allSubs);
                String[] cat = pick(cats);
                LocalDateTime created = today.minusDays(randint(0, 120)).atStartOfDay().plusHours(randint(7, 21));
                String st = weighted(List.of("open", "assigned", "in_progress", "resolved"), new double[]{18, 16, 20, 46});
                String sname = (String) sb[3];
                compRows.add(new Object[]{"GRB-" + created.getYear() + "-" + String.format("%06d", i), cat[0],
                        cat[0] + " reported near " + pick(VILLAGES) + ", " + sname + ". Needs attention.", "Near " + pick(VILLAGES) + ", " + sname,
                        (double) sb[4] + uni(-.05, .05), (double) sb[5] + uni(-.05, .05), sb[2], sb[1], sb[0], pick(names),
                        "98" + randint(10000000, 99999999), cat[1], st, st.equals("resolved") ? "Repair completed by sub-division team" : "",
                        created, st.equals("resolved") ? created.plusDays(randint(1, 12)) : created});
            }
            Db.batch(c, "INSERT INTO complaints (ticket, category, description, location_text, lat, lng, region_id, division_id, subdivision_id, "
                    + "citizen_name, phone, priority, status, resolution_note, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", compRows);

            // ---- recent activity
            String[][] acts = {{"Inspection logged", "asset", "Condition updated after field inspection"}, {"RA bill approved", "bill", "Forwarded to treasury"},
                    {"Progress update", "project", "Physical progress reported by contractor"}, {"Complaint resolved", "complaint", "Pothole patching done"},
                    {"Work order issued", "project", "Agreement signed with contractor"}, {"Critical alert raised", "asset", "Load restriction advised"}};
            List<Object[]> logRows = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                String[] act = pick(acts);
                A a = pick(assets);
                logRows.add(new Object[]{LocalDateTime.now().withNano(0).minusMinutes(randint(5, 60 * 72)), pick(INSPECTORS), act[0], act[1],
                        a.code, act[2] + " — " + a.name, a.regionId});
            }
            Db.batch(c, "INSERT INTO audit_logs (ts, user_name, action, entity, entity_id, detail, region_id) VALUES (?,?,?,?,?,?,?)", logRows);

            // identity columns continue after the seeded rows
            try (Statement st = c.createStatement()) {
                for (String t : new String[]{"inspections", "progress_updates", "bills", "users", "complaints", "audit_logs"}) {
                    long max = 0;
                    var rs = st.executeQuery("SELECT COALESCE(MAX(id), 0) FROM " + t);
                    if (rs.next()) max = rs.getLong(1);
                    st.execute("ALTER TABLE " + t + " ALTER COLUMN id RESTART WITH " + (max + 1));
                }
            }
            c.commit();
            System.out.printf("InfraTrack: seeded %,d assets and %,d projects in %.1f s%n", assets.size(), pid, (System.currentTimeMillis() - t0) / 1000.0);
        }
    }
}
