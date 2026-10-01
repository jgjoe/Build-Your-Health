package io.github.jgjoe.byh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-211: static checks over the main source tree. These tests do not need a database or a
 * Spring context; they read the files relative to the Maven project directory.
 */
class SourceHygieneTest {

    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");
    private static final Path APPLICATION_YML = MAIN_RESOURCES.resolve("application.yml");
    private static final Path MAPPER_DIR = MAIN_RESOURCES.resolve("mapper");

    private static final List<String> TEXT_EXTENSIONS =
            List.of(".java", ".yml", ".yaml", ".sql", ".xml", ".properties");

    /** A hard-coded JDBC connection URL (RQ-N-001). */
    private static final Pattern JDBC_URL =
            Pattern.compile("(?i)jdbc:(oracle|mysql|mariadb|postgresql|h2|sqlserver)");

    /** Environment-style assignment carrying a literal value, e.g. {@code DB_PASSWORD=s3cret}. */
    private static final Pattern ENV_ASSIGNMENT =
            Pattern.compile("(?im)\\b(DB_URL|DB_USERNAME|DB_PASSWORD)\\s*=\\s*(\\S.*)$");

    /** Datasource property carrying a literal value, e.g. {@code spring.datasource.password=s3cret}. */
    private static final Pattern DATASOURCE_PROPERTY =
            Pattern.compile("(?im)datasource[.\\-:](url|username|password)\\s*[:=]\\s*(\\S.*)$");

    /** MyBatis string substitution must never appear in a mapper (RQ-N-003). */
    private static final Pattern SUBSTITUTION = Pattern.compile("\\$\\{");

    @Test
    void tc211_noConnectionLiteralsInMainSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        sources.addAll(textFiles(MAIN_JAVA));
        sources.addAll(textFiles(MAIN_RESOURCES));
        assertThat(sources).as("main source files under %s and %s", MAIN_JAVA, MAIN_RESOURCES).isNotEmpty();

        for (Path source : sources) {
            String content = Files.readString(source, UTF_8);
            assertThat(JDBC_URL.matcher(content).find())
                    .as("%s must not contain a JDBC connection URL", source)
                    .isFalse();
            assertAssignmentKeepsPlaceholder(source, content, ENV_ASSIGNMENT);
            assertAssignmentKeepsPlaceholder(source, content, DATASOURCE_PROPERTY);
        }

        String yaml = Files.readString(APPLICATION_YML, UTF_8);
        assertThat(datasourceValue(yaml, "url")).isEqualTo("${DB_URL}");
        assertThat(datasourceValue(yaml, "username")).isEqualTo("${DB_USERNAME}");
        assertThat(datasourceValue(yaml, "password")).isEqualTo("${DB_PASSWORD}");
    }

    @Test
    void tc211_mapperXmlUsesOnlyBindVariables() throws IOException {
        List<Path> mappers = files(MAPPER_DIR,
                path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"));
        assertThat(mappers)
                .as("mapper XML files under %s (SQL mappers are added in the implementation phase)", MAPPER_DIR)
                .isNotEmpty();

        for (Path mapper : mappers) {
            assertThat(Files.readString(mapper, UTF_8))
                    .as("%s must use #{} bind variables only", mapper)
                    .doesNotContain("${");
        }
    }

    private static void assertAssignmentKeepsPlaceholder(Path source, String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String value = matcher.group(2).trim();
            assertThat(value)
                    .as("%s must not hard-code %s", source, matcher.group(1))
                    .startsWith("${");
        }
    }

    private static String datasourceValue(String yaml, String property) {
        Matcher matcher = Pattern.compile("(?m)^\\s*" + property + "\\s*:\\s*(.+?)\\s*$").matcher(yaml);
        assertThat(matcher.find()).as("application.yml must declare the datasource %s", property).isTrue();
        return matcher.group(1);
    }

    private static List<Path> textFiles(Path root) throws IOException {
        return files(root, path -> {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            return TEXT_EXTENSIONS.stream().anyMatch(name::endsWith);
        });
    }

    private static List<Path> files(Path root, Predicate<Path> filter) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).filter(filter).sorted().toList();
        }
    }
}
