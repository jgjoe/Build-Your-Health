package io.github.jgjoe.byh.perf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test of the measurement harness: one real {@link PerfMeasure#run} over every
 * scenario with the small data set, then structural checks on the returned summaries and the
 * written files. Plans and timings are never asserted - only completeness, internal consistency
 * and the fixed output format.
 */
class PerfMeasureTest {

    private static final long SEED = 20261002L;
    private static final String RUN_ID = "it";
    private static final GeneratorConfig CONFIG = GeneratorConfig.small(SEED);

    private static final String RAW_HEADER = "run_id,scenario,query,tier,run,elapsed_ms";
    private static final String SUMMARY_HEADER = "run_id,scenario,query,tier,sql_id,plan_hash_value,"
            + "allstats_plan_hash_value,child_count,warmup,runs,median_ms,p95_ms,min_ms,max_ms,"
            + "buffer_gets_per_exec,db_elapsed_ms_per_exec,rows_per_exec,indexes";
    private static final Pattern RAW_ROW =
            Pattern.compile("it,(S0|S1|S2A|S2B|S2C),(Q1|Q2|Q3),(heavy|mid|light),[123],\\d+\\.\\d{3}");

    private static PerfTestSchema schema;
    private static List<CaseSummary> summaries;

    @TempDir
    static Path outDir;

    @BeforeAll
    static void measureEveryCaseOnce() throws Exception {
        schema = PerfTestSchema.create();
        PerfDataLoader.load(schema.connection(), CONFIG);
        MeasureOptions options = new MeasureOptions(CONFIG, 1, 3, 100, List.of(Scenario.values()), outDir, RUN_ID);
        summaries = PerfMeasure.run(schema.connection(), options);
    }

    @AfterAll
    static void closeSchema() throws Exception {
        if (schema != null) {
            schema.close();
        }
    }

    @Test
    void p1_coversEveryScenarioQueryAndTierExactlyOnce() {
        assertThat(summaries).hasSize(35);
        assertThat(summaries.stream().map(PerfMeasureTest::label).toList()).doesNotHaveDuplicates();
        assertThat(summaries.stream().map(CaseSummary::runId).toList()).containsOnly(RUN_ID);

        for (Scenario scenario : Scenario.values()) {
            List<CaseSummary> cases = summaries.stream()
                    .filter(summary -> summary.scenario() == scenario)
                    .toList();
            assertThat(cases).as("%s cases", scenario).hasSize(7);
            for (MeasuredQuery query : List.of(MeasuredQuery.Q1, MeasuredQuery.Q2)) {
                assertThat(cases.stream().filter(summary -> summary.query() == query)
                        .map(CaseSummary::tier).toList())
                        .as("%s %s tiers", scenario, query)
                        .containsExactlyInAnyOrder("heavy", "mid", "light");
            }
            assertThat(cases.stream().filter(summary -> summary.query() == MeasuredQuery.Q3)
                    .map(CaseSummary::tier).toList())
                    .as("%s Q3 tiers", scenario)
                    .containsExactly("mid");
        }
    }

    @Test
    void p2_everySummaryIsInternallyConsistent() {
        for (CaseSummary summary : summaries) {
            String label = label(summary);
            assertThat(summary.runId()).as("%s run id", label).isEqualTo(RUN_ID);
            assertThat(summary.sqlId()).as("%s sql id", label).isNotBlank();
            assertThat(summary.planHashValue()).as("%s plan hash", label).isPositive();
            // Whether the ALLSTATS execution reused the measured plan is recorded, not asserted:
            // a separate STATISTICS_LEVEL=ALL cursor may legitimately be optimized differently.
            assertThat(summary.allstatsPlanHashValue()).as("%s allstats plan hash", label).isPositive();
            assertThat(summary.childCount()).as("%s child count", label).isGreaterThanOrEqualTo(1);
            assertThat(summary.warmup()).as("%s warmup", label).isEqualTo(1);
            assertThat(summary.runs()).as("%s runs", label).isEqualTo(3);
            assertThat(summary.minMs()).as("%s min vs median", label).isLessThanOrEqualTo(summary.medianMs());
            assertThat(summary.medianMs()).as("%s median vs p95", label).isLessThanOrEqualTo(summary.p95Ms());
            assertThat(summary.p95Ms()).as("%s p95 vs max", label).isLessThanOrEqualTo(summary.maxMs());
            assertThat(summary.bufferGetsPerExec()).as("%s buffer gets per exec", label).isPositive();
            assertThat(summary.dbElapsedMsPerExec()).as("%s db elapsed per exec", label).isNotNegative();
            if (summary.query() == MeasuredQuery.Q2) {
                assertThat(summary.rowsPerExec()).as("%s rows per exec", label).isEqualTo(1.0);
            }
            assertThat(new HashSet<>(summary.indexes())).as("%s indexes", label)
                    .isEqualTo(new HashSet<>(summary.scenario().indexNames()));
        }
    }

    @Test
    void p3_rawAndSummaryCsvDescribeTheSameCases() throws Exception {
        List<String> rawLines = Files.readAllLines(outDir.resolve("raw.csv"), StandardCharsets.UTF_8);
        assertThat(rawLines.get(0)).isEqualTo(RAW_HEADER);
        List<String> rawRows = rawLines.subList(1, rawLines.size());
        assertThat(rawRows).hasSize(35 * 3);

        Map<CaseKey, Set<Integer>> runsPerCase = new LinkedHashMap<>();
        for (String row : rawRows) {
            assertThat(row).as("raw.csv row").matches(RAW_ROW);
            String[] fields = row.split(",", -1);
            runsPerCase.computeIfAbsent(new CaseKey(fields[1], fields[2], fields[3]), key -> new HashSet<>())
                    .add(Integer.parseInt(fields[4]));
        }
        assertThat(runsPerCase).hasSize(35);
        assertThat(runsPerCase.keySet()).isEqualTo(caseKeys());
        assertThat(runsPerCase).allSatisfy((key, runs) ->
                assertThat(runs).as("runs of %s", key).containsExactlyInAnyOrder(1, 2, 3));

        List<String> summaryLines = Files.readAllLines(outDir.resolve("summary.csv"), StandardCharsets.UTF_8);
        assertThat(summaryLines.get(0)).isEqualTo(SUMMARY_HEADER);
        List<String> summaryRows = summaryLines.subList(1, summaryLines.size());
        assertThat(summaryRows).hasSize(summaries.size());

        Map<CaseKey, CaseSummary> byKey = summaries.stream()
                .collect(Collectors.toMap(PerfMeasureTest::key, Function.identity()));
        Set<CaseKey> seen = new LinkedHashSet<>();
        for (String row : summaryRows) {
            String[] fields = row.split(",", -1);
            assertThat(fields).as("columns of '%s'", row).hasSize(18);
            assertThat(fields[0]).as("run id of '%s'", row).isEqualTo(RUN_ID);
            CaseKey caseKey = new CaseKey(fields[1], fields[2], fields[3]);
            CaseSummary summary = byKey.get(caseKey);
            assertThat(summary).as("summary for csv case %s", caseKey).isNotNull();
            seen.add(caseKey);
            assertThat(fields[4]).as("sql_id of %s", caseKey).isEqualTo(summary.sqlId());
            assertThat(Long.parseLong(fields[5])).as("plan_hash_value of %s", caseKey)
                    .isEqualTo(summary.planHashValue());
            assertThat(Integer.parseInt(fields[7])).as("child_count of %s", caseKey)
                    .isEqualTo(summary.childCount());
        }
        assertThat(seen).isEqualTo(byKey.keySet());
    }

    @Test
    void p4_everyPlanFileHasTypicalAndAllstatsPlans() throws Exception {
        for (CaseSummary summary : summaries) {
            Path planFile = outDir.resolve("plans")
                    .resolve(summary.scenario() + "_" + summary.query() + "_" + summary.tier() + ".txt");
            assertThat(planFile).as("%s plan file", label(summary)).exists();
            String plan = Files.readString(planFile, StandardCharsets.UTF_8);
            assertThat(plan).as("%s: plan hash", label(summary)).contains("Plan hash value");
            assertThat(plan).as("%s: allstats marker", label(summary)).contains("--- ALLSTATS LAST ---");
            assertThat(plan).as("%s: actual rows column", label(summary)).contains("A-Rows");
        }
    }

    @Test
    void p5_environmentFileDescribesTheRunAndItsData() throws Exception {
        Map<String, String> environment = readEnvironment(outDir.resolve("environment.txt"));
        assertThat(environment).containsKeys("run_id", "oracle_version", "optimizer_features_enable",
                "seed", "warmup", "runs", "fetch_size");
        assertThat(environment).containsEntry("run_id", RUN_ID)
                .containsEntry("seed", Long.toString(SEED))
                .containsEntry("warmup", "1")
                .containsEntry("runs", "3")
                .containsEntry("fetch_size", "100");
        assertThat(environment.get("oracle_version")).isNotBlank();
        assertThat(environment.get("optimizer_features_enable")).isNotBlank();

        List<String> values = List.copyOf(environment.values());
        for (String tier : List.of("heavy", "mid", "light")) {
            String representative = CONFIG.representative(tier);
            assertThat(values).as("environment value for the %s representative %s", tier, representative)
                    .anySatisfy(value -> assertThat(value).contains(representative));
        }
    }

    @Test
    void p6_onlyPrimaryKeyIndexesRemainAfterTheRun() throws Exception {
        Set<String> indexNames = new HashSet<>();
        try (Statement statement = schema.connection().createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT INDEX_NAME FROM USER_INDEXES WHERE TABLE_NAME IN"
                                + " ('MEMBER', 'PRODUCT', 'ORDERS', 'ORDER_ITEMS') AND INDEX_TYPE <> 'LOB'")) {
            while (rows.next()) {
                indexNames.add(rows.getString(1));
            }
        }
        assertThat(indexNames).containsExactlyInAnyOrder(
                "PK_MEMBER", "PK_PRODUCT", "PK_ORDERS", "PK_ORDER_ITEMS");
    }

    @Test
    void defaultsMatchTheDocumentedValues() {
        MeasureOptions options = MeasureOptions.defaults(CONFIG, outDir, "defaults");
        assertThat(options.config()).isEqualTo(CONFIG);
        assertThat(options.outDir()).isEqualTo(outDir);
        assertThat(options.runId()).isEqualTo("defaults");
        assertThat(options.warmup()).isEqualTo(5);
        assertThat(options.runs()).isEqualTo(30);
        assertThat(options.fetchSize()).isEqualTo(100);
        assertThat(options.scenarios()).containsExactly(Scenario.values());
    }

    private static String label(CaseSummary summary) {
        return summary.scenario() + "/" + summary.query() + "/" + summary.tier();
    }

    private static CaseKey key(CaseSummary summary) {
        return new CaseKey(summary.scenario().name(), summary.query().name(), summary.tier());
    }

    private static Set<CaseKey> caseKeys() {
        return summaries.stream()
                .map(PerfMeasureTest::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Map<String, String> readEnvironment(Path file) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            int separator = line.indexOf('=');
            assertThat(separator).as("key=value line '%s'", line).isGreaterThan(0);
            values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        return values;
    }

    /** A (scenario, query, tier) case, parsed from the CSV column names. */
    private record CaseKey(String scenario, String query, String tier) {
    }
}
