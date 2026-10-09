package g_mungus.munguscript.intellij;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import g_mungus.munguscript.engine.Highlight.Kind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the editor offers while a line is being written, as the ZeroPointSystems terminal does. */
class WritingTest {
    @TempDir
    static Path dir;

    private static ScriptAnalysis analysis;

    @BeforeAll
    static void readTheLanguage() throws IOException {
        analysis = ScriptAnalysis.read(TestLanguage.write(dir.resolve("test.mungustree")));
    }

    private static Optional<ScriptAnalysis.Usage> usage(String script) {
        return analysis.usage(script, script.length());
    }

    private static Optional<ScriptAnalysis.Completion> completion(String script, String text) {
        return analysis.complete(script, script.length()).stream()
                .filter(completion -> completion.text().equals(text)).findFirst();
    }

    @Test
    void wordsAreOfferedBeforeOneIsStarted() {
        ScriptAnalysis.Completion writeFile = completion("", "write_file").orElseThrow();
        assertEquals(Kind.EXECUTOR, writeFile.kind());
        assertEquals("<from to destination>", writeFile.detail());
        assertTrue(completion("log \"a\"\n", "warn").isPresent());
    }

    @Test
    void completionsSayWhatAWordGivesOrTakes() {
        ScriptAnalysis.Completion version = completion("log value_of(project_ve", "project_version").orElseThrow();
        assertEquals(Kind.GETTER, version.kind());
        assertEquals("string", version.detail());
        ScriptAnalysis.Completion endsWith = completion("if project_version ", "ends_with").orElseThrow();
        assertEquals(Kind.MAPPER, endsWith.kind());
        assertEquals("<string> → boolean", endsWith.detail());
    }

    @Test
    void theArgumentBeingWrittenShowsItsUsage() {
        ScriptAnalysis.Usage log = usage("log ").orElseThrow();
        assertEquals(List.of("<string>"), log.lines());
        assertEquals(4, log.start());
        assertFalse(log.error());
        assertEquals(List.of("<from to destination>"), usage("x\nwrite_file ").orElseThrow().lines());
        assertEquals(List.of("<string>"), usage("if project_version ends_with ").orElseThrow().lines());
    }

    @Test
    void aLineThatCannotGoOnSaysWhy() {
        ScriptAnalysis.Usage unknown = usage("frobnicate").orElseThrow();
        assertTrue(unknown.error());
        assertEquals(List.of("Unknown command 'frobnicate'"), unknown.lines());
    }

    @Test
    void definitionsOfferTheirKeywordAndEquals() {
        assertEquals(0, completion("#d", "#def ").orElseThrow().start());
        assertTrue(completion("#def stamp ", "= ").isPresent());
        assertEquals(List.of("<alias>"), usage("#def ").orElseThrow().lines());
        assertEquals(List.of("<alias>"), usage("#def sta").orElseThrow().lines());
        ScriptAnalysis.Usage noEquals = usage("#def stamp oops").orElseThrow();
        assertTrue(noEquals.error());
        assertEquals(List.of("Expected '=' after alias name"), noEquals.lines());
        assertTrue(completion("#def v = project_ve", "project_version").isPresent());
    }

    @Test
    void commentsAndDefinitionsMayStandBetweenCommands() {
        String script = "log \"a\"\n#note\n#def x = 5\nlog value_of(x)\n";
        assertEquals(List.of(), analysis.analyse(script).problems());
        assertTrue(completion("log \"a\"\n#d", "#def ").isPresent());
    }

    @Test
    void aLineSeesOnlyTheAliasesDefinedAboveIt() {
        String script = "log value_of(stamp)\n#def stamp = \"v.txt\"\nlog value_of(stamp)\n";
        List<ScriptAnalysis.Problem> problems = analysis.analyse(script).problems();
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).start() < script.indexOf("#def"), problems.toString());
        String above = "log value_of(sta\n#def stamp = \"v.txt\"\n";
        assertTrue(analysis.complete(above, above.indexOf("\n")).stream()
                .noneMatch(completion -> completion.text().equals("stamp")));
        String below = "#def stamp = \"v.txt\"\nlog value_of(sta";
        assertTrue(completion(below, "stamp").isPresent());
        // Coloured as an alias only where it is one.
        Kind first = analysis.lineHighlighter(script).apply(0).stream()
                .filter(span -> span.start() == script.indexOf("stamp")).findFirst().orElseThrow().kind();
        Kind last = analysis.lineHighlighter(script).apply(script.lastIndexOf("log")).stream()
                .filter(span -> span.start() == script.lastIndexOf("stamp")).findFirst().orElseThrow().kind();
        assertTrue(first != Kind.ALIAS, String.valueOf(first));
        assertEquals(Kind.ALIAS, last);
    }

    @Test
    void anyHashLineIsAComment() {
        assertEquals(List.of(), analysis.analyse("#note\n# fine\nlog \"a\"\n").problems());
        MungusLexer lexer = new MungusLexer();
        lexer.start("# a\n#note\n#def x = 5\n", 0, 21, 0);
        List<IElementType> types = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            if (lexer.getTokenType() != TokenType.WHITE_SPACE) {
                types.add(lexer.getTokenType());
            }
            lexer.advance();
        }
        assertEquals(List.of(MungusTokenTypes.COMMENT, MungusTokenTypes.COMMENT, MungusTokenTypes.DEFINITION), types);
    }

    @Test
    void whatALineStillLacksIsToldFromWhatIsWrongInIt() {
        assertTrue(analysis.analyse("write_file\n").problems().get(0).atLineEnd());
        assertFalse(analysis.analyse("frobnicate \"x\"\n").problems().get(0).atLineEnd());
    }

    @Test
    void aLineIsColouredAsItIsWritten() {
        String script = "#def stamp = \"v.txt\"\nwrite_file value_of(project_ver";
        List<ScriptAnalysis.Span> spans = analysis.lineHighlighter(script).apply(script.indexOf("write_file"));
        assertEquals(Kind.EXECUTOR, spans.get(0).kind());
        assertTrue(spans.stream().anyMatch(span -> span.kind() == Kind.GETTER || span.kind() == Kind.UNPARSED),
                spans.toString());
    }

    @Test
    void theHighlightingLexerSplitsLinesIntoWords() {
        String script = "# notes\n#def stamp = \"v.txt\"\n\n  write_file value_of(project_version to value_of(stamp))\n";
        MungusHighlightingLexer lexer = new MungusHighlightingLexer(() -> analysis);
        lexer.start(script, 0, script.length(), 0);
        List<String> words = new ArrayList<>();
        int at = 0;
        while (lexer.getTokenType() != null) {
            assertEquals(at, lexer.getTokenStart(), "tokens leave no gaps");
            boolean lineStart = at == 0 || script.charAt(at - 1) == '\n';
            assertEquals(lineStart ? 0 : 1, lexer.getState(), "only tokens that start a line can be started from");
            IElementType type = lexer.getTokenType();
            if (MungusTokenTypes.kindOf(type) != null) {
                words.add(MungusTokenTypes.kindOf(type) + " " + script.substring(lexer.getTokenStart(), lexer.getTokenEnd()));
            } else if (type != TokenType.WHITE_SPACE) {
                assertTrue(type == MungusTokenTypes.COMMENT || type == MungusTokenTypes.COMMAND
                        || type == MungusTokenTypes.DEFINITION, type.toString());
            }
            at = lexer.getTokenEnd();
            lexer.advance();
        }
        assertEquals(script.length(), at);
        assertEquals(List.of("KEYWORD #def", "ALIAS stamp", "ARGUMENT \"v.txt\"", "EXECUTOR write_file",
                "ARGUMENT value_of(", "GETTER project_version", "MAPPER to", "ARGUMENT value_of(", "ALIAS stamp",
                "ARGUMENT )", "ARGUMENT )"), words);
    }

    @Test
    void withoutALanguageTheLexerStillTellsLinesApart() {
        MungusHighlightingLexer lexer = new MungusHighlightingLexer(() -> null);
        lexer.start("log \"x\"\n", 0, 8, 0);
        assertEquals(MungusTokenTypes.COMMAND, lexer.getTokenType());
        assertEquals(7, lexer.getTokenEnd());
    }
}
