package io.github.jgjoe.byh.perf;

import java.util.Arrays;

/** Summary statistics used by {@link PerfMeasure}. The input arrays are never mutated. */
public final class Stats {

    private Stats() {
    }

    /** Middle value of the ascending values; the mean of the two middle values for an even count. */
    public static double median(double[] values) {
        double[] sorted = sorted(values);
        int middle = sorted.length / 2;
        if (sorted.length % 2 == 1) {
            return sorted[middle];
        }
        return (sorted[middle - 1] + sorted[middle]) / 2.0;
    }

    /**
     * Nearest-rank p95: the element at index {@code ceil(0.95 * n) - 1} of the ascending values.
     * Computed with integer arithmetic so the rank is exact for every length.
     */
    public static double p95(double[] values) {
        double[] sorted = sorted(values);
        int rank = (95 * sorted.length + 99) / 100;
        return sorted[rank - 1];
    }

    private static double[] sorted(double[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        double[] copy = values.clone();
        Arrays.sort(copy);
        return copy;
    }
}
