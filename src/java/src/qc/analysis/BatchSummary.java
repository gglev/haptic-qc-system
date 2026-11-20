package qc.analysis;

import qc.model.TestRun;
import qc.model.ResponseRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BatchSummary {
    private final Map<String, Integer> allCounts;
    private final Map<String, Integer> finalCounts;
    private final int total;
    private final int uniqueProducts;

    public BatchSummary(List<TestRun> runs) {
        List<TestRun> ordered = new ArrayList<>(runs);
        ordered.sort((left, right) -> {
            int comparison = ResponseRules.compareText(left.getProduct().getId(), right.getProduct().getId());
            if (comparison == 0) {
                comparison = left.getStartedInstant().compareTo(right.getStartedInstant());
            }
            if (comparison == 0) {
                comparison = ResponseRules.compareText(left.getId(), right.getId());
            }
            return comparison;
        });
        allCounts = new LinkedHashMap<>(Map.of("PASS", 0, "FAIL", 0, "REVIEW", 0));
        finalCounts = new LinkedHashMap<>(allCounts);
        int unique = 0;
        for (int i = 0; i < ordered.size(); i++) {
            TestRun run = ordered.get(i);
            String status = run.getStatus();
            if (!allCounts.containsKey(status)) {
                throw new IllegalArgumentException("Нужно сначала рассчитать все проверки");
            }
            allCounts.put(status, allCounts.get(status) + 1);
            boolean last = i + 1 == ordered.size()
                    || !run.getProduct().getId().equals(ordered.get(i + 1).getProduct().getId());
            if (last) {
                unique++;
                finalCounts.put(status, finalCounts.get(status) + 1);
            }
        }
        total = ordered.size();
        uniqueProducts = unique;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("passed", allCounts.get("PASS"));
        summary.put("failed", allCounts.get("FAIL"));
        summary.put("review", allCounts.get("REVIEW"));
        summary.put("pass_percent", total == 0 ? null : 100.0 * allCounts.get("PASS") / total);
        summary.put("unique_products", uniqueProducts);
        summary.put("repeated_checks", total - uniqueProducts);
        summary.put("final_passed", finalCounts.get("PASS"));
        summary.put("final_failed", finalCounts.get("FAIL"));
        summary.put("final_review", finalCounts.get("REVIEW"));
        summary.put("final_pass_percent", total == 0 ? null : 100.0 * finalCounts.get("PASS") / uniqueProducts);
        return summary;
    }
}
