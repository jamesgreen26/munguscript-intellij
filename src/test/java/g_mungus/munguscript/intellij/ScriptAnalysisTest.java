package g_mungus.munguscript.intellij;

import g_mungus.munguscript.engine.Highlight.Kind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptAnalysisTest {
    @TempDir
    static Path dir;

    private static ScriptAnalysis ANALYSIS;

    @BeforeAll
    static void readTheLanguage() throws IOException {
        ANALYSIS = ScriptAnalysis.read(TestLanguage.write(dir.resolve("test.mungustree")));
    }

    private static final String SCRIPT = """
            # Stamps the version.
            #def stamp_file = "target/classes/version.txt"

            write_file value_of(project_version to value_of(stamp_file))
            if project_version ends_with "-SNAPSHOT" warn "Building a snapshot"
            """;

    /** What {@code word}, the {@code occurrence}th time it is in {@code script}, is highlighted as. */
    private static Kind kindOf(ScriptAnalysis.Result result, String script, String word, int occurrence) {
        int at = -1;
        for (int i = 0; i <= occurrence; i++) {
            at = script.indexOf(word, at + 1);
        }
        int start = at;
        return result.spans().stream()
                .filter(span -> span.start() == start && span.end() == start + word.length())
                .findFirst().orElseThrow(() -> new AssertionError("No span for " + word + " in " + result.spans()))
                .kind();
    }

    @Test
    void aGoodScriptHasNoProblems() {
        assertEquals(List.of(), ANALYSIS.analyse(SCRIPT).problems());
    }

    @Test
    void wordsAreWhatTheEngineSays() {
        ScriptAnalysis.Result result = ANALYSIS.analyse(SCRIPT);
        assertEquals(Kind.KEYWORD, kindOf(result, SCRIPT, "#def", 0));
        assertEquals(Kind.ALIAS, kindOf(result, SCRIPT, "stamp_file", 0));
        assertEquals(Kind.ARGUMENT, kindOf(result, SCRIPT, "\"target/classes/version.txt\"", 0));
        assertEquals(Kind.EXECUTOR, kindOf(result, SCRIPT, "write_file", 0));
        assertEquals(Kind.GETTER, kindOf(result, SCRIPT, "project_version", 0));
        assertEquals(Kind.MAPPER, kindOf(result, SCRIPT, "to", 0));
        assertEquals(Kind.ALIAS, kindOf(result, SCRIPT, "stamp_file", 1));
        assertEquals(Kind.KEYWORD, kindOf(result, SCRIPT, "if", 0));
        assertEquals(Kind.MAPPER, kindOf(result, SCRIPT, "ends_with", 0));
        assertEquals(Kind.EXECUTOR, kindOf(result, SCRIPT, "warn", 0));
    }

    @Test
    void anUnknownWordIsAProblemWhereItIs() {
        String script = "log \"a\"\n  frobnicate \"b\"\n";
        ScriptAnalysis.Result result = ANALYSIS.analyse(script);
        int start = script.indexOf("frobnicate");
        assertEquals(List.of(new ScriptAnalysis.Problem(start, start + "frobnicate".length(),
                "Unknown command 'frobnicate'")), result.problems());
    }

    @Test
    void aBadDefinitionIsAProblemOnItsLine() {
        String script = "#def site = target/site\nlog \"x\"\n";
        List<ScriptAnalysis.Problem> problems = ANALYSIS.analyse(script).problems();
        assertEquals(1, problems.size());
        assertEquals(0, problems.get(0).start());
        assertEquals("#def site = target/site".length(), problems.get(0).end());
        assertTrue(problems.get(0).message().startsWith("Alias must be a literal"), problems.get(0).message());
    }

    @Test
    void anIncompleteCommandIsAProblem() {
        assertEquals(1, ANALYSIS.analyse("write_file\n").problems().size());
    }

    @Test
    void completesTheWordAtTheCursor() {
        String script = "log \"a\"\nwri";
        List<ScriptAnalysis.Completion> completions = ANALYSIS.complete(script, script.length());
        assertTrue(completions.stream().anyMatch(completion -> completion.text().equals("write_file")
                && completion.start() == script.indexOf("wri")), completions.toString());
    }

    @Test
    void completesAliasesWhereExpressionsStart() {
        String script = "#def stamp_file = \"v.txt\"\nlog value_of(sta";
        List<ScriptAnalysis.Completion> completions = ANALYSIS.complete(script, script.length());
        assertTrue(completions.stream().anyMatch(completion -> completion.text().equals("stamp_file")),
                completions.toString());
    }

    @Test
    void completesADefinitionsBody() {
        String script = "#def version = project_ve";
        List<ScriptAnalysis.Completion> completions = ANALYSIS.complete(script, script.length());
        assertTrue(completions.stream().anyMatch(completion -> completion.text().equals("project_version")
                && completion.start() == script.indexOf("project_ve")), completions.toString());
    }

    @Test
    void anArgumentOfTheHostsOwnIsReadByItsShape() {
        assertEquals(List.of(), ANALYSIS.analyse("copy \"a\" to \"b\"\n").problems());
        List<ScriptAnalysis.Problem> problems = ANALYSIS.analyse("copy \"a\" from \"b\"\n").problems();
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).message().contains("Expected 'to'"), problems.get(0).message());
        String script = "copy \"a\" ";
        assertTrue(ANALYSIS.complete(script, script.length()).stream()
                .anyMatch(completion -> completion.text().equals("to")));
    }

    @Test
    void nothingIsOfferedInAComment() {
        String script = "# wri";
        assertEquals(List.of(), ANALYSIS.complete(script, script.length()));
    }
}
