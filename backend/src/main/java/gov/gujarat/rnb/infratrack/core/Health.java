package gov.gujarat.rnb.infratrack.core;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Predictive maintenance engine.
 * Risk index (0-100) = 50% condition deficit + 18% age + 12% traffic + 10% overdue inspection + 10% asset-type criticality.
 */
public final class Health {
    private Health() {}

    private static final Map<String, Double> TYPE_CRITICALITY = Map.of(
            "bridge", 1.0, "road", 0.8, "culvert", 0.7, "other", 0.6, "building", 0.6, "facility", 0.5);
    private static final Map<String, Double> BASE_DETERIORATION = Map.of(
            "road", 4.2, "bridge", 2.4, "culvert", 3.1, "building", 1.9, "facility", 1.8, "other", 2.8);

    public static String band(double score) {
        if (score < 25) return "critical";
        if (score < 50) return "poor";
        if (score < 75) return "fair";
        return "good";
    }

    public static double risk(double score, int yearBuilt, int aadt, LocalDate lastInspection, String type, LocalDate today) {
        double age = Math.min(Math.max(today.getYear() - yearBuilt, 0) / 60.0, 1);
        double traffic = Math.min(aadt / 40000.0, 1);
        double overdue = Math.min(Math.max(ChronoUnit.DAYS.between(lastInspection, today) - 365, 0) / 365.0, 1);
        double r = 0.5 * (100 - score) + 18 * age + 12 * traffic + 10 * overdue + 10 * TYPE_CRITICALITY.getOrDefault(type, 0.6);
        return M.round(Math.min(r, 100), 1);
    }

    /** Condition points lost per year. */
    public static double deteriorationRate(String type, int yearBuilt, int aadt) {
        LocalDate today = LocalDate.now();
        double age = Math.min(Math.max(today.getYear() - yearBuilt, 0) / 60.0, 1);
        double traffic = Math.min(aadt / 40000.0, 1);
        return M.round(BASE_DETERIORATION.getOrDefault(type, 3.0) * (1 + 0.8 * traffic) * (1 + 0.5 * age), 2);
    }

    public static Map<String, Object> forecast(String type, double score, int yearBuilt, int aadt, double replacementValueCr) {
        double rate = deteriorationRate(type, yearBuilt, aadt);
        long monthsToCritical = score <= 25 ? 0 : Math.round((score - 25) / rate * 12);
        int year = LocalDate.now().getYear();
        List<Map<String, Object>> curve = new ArrayList<>();
        for (int i = 0; i < 8; i++) curve.add(M.of("year", year + i, "predicted", M.round(Math.max(score - rate * i, 0), 1)));
        String action;
        double factor;
        if (score < 25) { action = "Emergency repair and load/traffic restriction"; factor = 0.35; }
        else if (score < 50) { action = "Major rehabilitation within this financial year"; factor = 0.18; }
        else if (score < 75) { action = "Periodic maintenance (resurfacing / repairs)"; factor = 0.06; }
        else { action = "Routine inspection and upkeep"; factor = 0.01; }
        return M.of(
                "deterioration_per_year", rate,
                "months_to_critical", monthsToCritical,
                "recommended_action", action,
                "estimated_cost_lakh", M.round(replacementValueCr * factor * 100, 1),
                "curve", curve);
    }

    /** A work is behind schedule when actual progress trails planned progress by more than 10 points. */
    public static boolean isDelayed(String status, double progress, LocalDate start, LocalDate target, LocalDate today) {
        if (!"in_progress".equals(status)) return false;
        return progress < expectedProgress(start, target, today) - 10;
    }

    public static double expectedProgress(LocalDate start, LocalDate target, LocalDate today) {
        long span = Math.max(ChronoUnit.DAYS.between(start, target), 1);
        double f = Math.min(Math.max(ChronoUnit.DAYS.between(start, today) / (double) span, 0), 1);
        return f * 100;
    }
}
