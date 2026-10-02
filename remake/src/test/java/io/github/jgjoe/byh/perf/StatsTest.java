package io.github.jgjoe.byh.perf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the measurement summary statistics ({@link Stats#median}, {@link Stats#p95}). */
class StatsTest {

    @Test
    void medianOfOddLengthIsTheMiddleValue() {
        assertThat(Stats.median(new double[] {5, 1, 3})).isEqualTo(3.0);
    }

    @Test
    void medianOfEvenLengthAveragesTheTwoMiddleValues() {
        assertThat(Stats.median(new double[] {4, 1, 3, 2})).isEqualTo(2.5);
    }

    @Test
    void p95OfTwentyValuesIsTheNineteenth() {
        assertThat(Stats.p95(oneTo(20))).isEqualTo(19.0);
    }

    @Test
    void p95OfThirtyValuesIsTheTwentyNinth() {
        assertThat(Stats.p95(oneTo(30))).isEqualTo(29.0);
    }

    @Test
    void p95OfASingleValueIsThatValue() {
        assertThat(Stats.p95(new double[] {7})).isEqualTo(7.0);
    }

    @Test
    void inputsAreNotMutated() {
        double[] odd = {5, 1, 3};
        Stats.median(odd);
        assertThat(odd).containsExactly(5.0, 1.0, 3.0);

        double[] even = {4, 1, 3, 2};
        Stats.median(even);
        assertThat(even).containsExactly(4.0, 1.0, 3.0, 2.0);

        double[] many = {20, 1, 10, 5, 3};
        Stats.p95(many);
        assertThat(many).containsExactly(20.0, 1.0, 10.0, 5.0, 3.0);
    }

    private static double[] oneTo(int n) {
        double[] values = new double[n];
        for (int i = 0; i < n; i++) {
            values[i] = i + 1;
        }
        return values;
    }
}
