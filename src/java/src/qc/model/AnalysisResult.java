package qc.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnalysisResult {
    private final String status;
    private final Double peakFrequency;
    private final String reason;

    private AnalysisResult(String status, Double peakFrequency, String reason) {
        this.status = status;
        this.peakFrequency = peakFrequency;
        this.reason = reason;
    }

    public String getStatus() { return status; }
    public Double getPeakFrequency() { return peakFrequency; }
    public String getReason() { return reason; }

    public static AnalysisResult evaluate(List<Double> frequencies, List<Double> values,
                                          RecipeLimit limit) {
        if (values.size() < 3 || values.size() != frequencies.size()) {
            throw new IllegalArgumentException("Неверный размер обработанной характеристики");
        }
        double maximum = values.get(0);
        for (double value : values) {
            maximum = Math.max(maximum, value);
        }
        List<Integer> peaks = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            if (ResponseRules.isClose(values.get(i), maximum, 1e-12)) {
                peaks.add(i);
            }
        }
        if (maximum <= 0 || peaks.size() != 1) {
            return new AnalysisResult("REVIEW", null, "Нет одного положительного максимума");
        }
        int peak = peaks.get(0);
        if (peak == 0 || peak == values.size() - 1) {
            return new AnalysisResult("REVIEW", null,
                    "Максимум на краю сглаженной характеристики: расширьте диапазон");
        }
        double frequency = frequencies.get(peak);
        return new AnalysisResult(limit.contains(frequency) ? "PASS" : "FAIL", frequency,
                "Проверен допуск частоты максимального отклика");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("peak_frequency_hz", peakFrequency);
        result.put("reason", reason);
        return result;
    }
}
