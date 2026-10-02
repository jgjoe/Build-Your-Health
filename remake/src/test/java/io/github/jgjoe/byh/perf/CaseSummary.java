package io.github.jgjoe.byh.perf;

import java.util.List;

/** One measured (scenario, query, tier) case: the summary row of {@code summary.csv}. */
public record CaseSummary(String runId, Scenario scenario, MeasuredQuery query, String tier, String sqlId,
                          long planHashValue, long allstatsPlanHashValue, int childCount, int warmup, int runs,
                          double medianMs, double p95Ms, double minMs, double maxMs,
                          double bufferGetsPerExec, double dbElapsedMsPerExec, double rowsPerExec,
                          List<String> indexes) {

    public CaseSummary {
        indexes = List.copyOf(indexes);
    }
}
