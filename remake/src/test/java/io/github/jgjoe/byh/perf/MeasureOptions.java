package io.github.jgjoe.byh.perf;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Fixed parameters of one measurement run. */
public record MeasureOptions(GeneratorConfig config, int warmup, int runs, int fetchSize,
                             List<Scenario> scenarios, Path outDir, String runId) {

    public MeasureOptions {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(outDir, "outDir");
        Objects.requireNonNull(runId, "runId");
        scenarios = List.copyOf(scenarios);
        if (warmup < 0) {
            throw new IllegalArgumentException("warmup must not be negative: " + warmup);
        }
        if (runs < 1) {
            throw new IllegalArgumentException("runs must be positive: " + runs);
        }
        if (fetchSize < 1) {
            throw new IllegalArgumentException("fetchSize must be positive: " + fetchSize);
        }
    }

    /** warmup 5, runs 30, fetchSize 100, all scenarios in declaration order. */
    public static MeasureOptions defaults(GeneratorConfig config, Path outDir, String runId) {
        return new MeasureOptions(config, 5, 30, 100, List.of(Scenario.values()), outDir, runId);
    }
}
