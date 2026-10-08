package g_mungus.munguscript.intellij;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import g_mungus.munguscript.engine.Highlight;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.codec.ScriptLanguageFile;
import g_mungus.munguscript.engine.failure.ScriptFailure;
import g_mungus.munguscript.engine.host.Match;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.PreProcessContext;
import g_mungus.munguscript.engine.preprocess.PreProcessDiagnostic;
import g_mungus.munguscript.runner.SimpleHost;
import g_mungus.munguscript.runner.SimpleSource;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a whole script as the runner would, without running anything: what each word is, what is
 * wrong, and what could be written at a point. Offsets are in the script's text. Nothing here
 * depends on the IDE, so it can be tested on its own.
 *
 * <p>The language comes from a {@link ScriptLanguageFile}, which its host wrote, so the plugin knows
 * no language of its own. Lines are handled as {@link g_mungus.munguscript.runner.ScriptRunner}
 * handles them: aliases are prepared from the whole script, the lines that used up are skipped,
 * and every other non-blank line is one command, stripped.
 */
public final class ScriptAnalysis {
    private static final String DEFINE = "#def";
    private static final Pattern DECLARATION = Pattern.compile("\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=");

    private final ScriptView<SimpleSource> view;
    private final CommandPreProcessor preProcessors;
    /** An editor has no host context and runs nothing, so a script is read for no source in particular. */
    private final SimpleSource source = new SimpleSource(null);
    private final PreProcessContext context;

    public ScriptAnalysis(ScriptView<SimpleSource> view) {
        this.view = view;
        this.preProcessors = view.aliases();
        this.context = new PreProcessContext(view.probe(source), null);
    }

    /**
     * Reads the language in {@code file}. An editor has no target, so a word meant only for some
     * targets is offered everywhere.
     */
    public static ScriptAnalysis read(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            return new ScriptAnalysis(new ScriptLanguageFile().read(in,
                    namespace -> new SimpleHost(namespace, (applicability, context) -> Match.UNRESTRICTED)));
        }
    }

    /** A word and what it is. */
    public record Span(int start, int end, Highlight.Kind kind) {
    }

    /** Something wrong, and where. An empty range is a problem at that point, such as the end of a line. */
    public record Problem(int start, int end, String message) {
    }

    public record Result(List<Span> spans, List<Problem> problems) {
    }

    /**
     * Something that could be written at the cursor.
     *
     * @param start where the text it replaces starts; it replaces up to the cursor
     */
    public record Completion(int start, String text, @Nullable String tooltip) {
    }

    public Result analyse(String script) {
        List<Line> lines = Line.split(script);
        CommandPreProcessor.Prepared prepared = prepare(lines);
        List<Span> spans = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        for (PreProcessDiagnostic diagnostic : prepared.diagnostics()) {
            Line line = diagnostic.line() == null ? lines.get(0) : lines.get(diagnostic.line());
            int base = line.start() + line.indent();
            problems.add(diagnostic.range() == null
                    ? new Problem(base, line.end(), diagnostic.message())
                    : new Problem(base + diagnostic.range().getStart(), base + diagnostic.range().getEnd(),
                    diagnostic.message()));
        }
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (line.text().isBlank()) {
                continue;
            }
            if (prepared.consumedLines().contains(i)) {
                if (isDefinition(line.command())) {
                    definition(line, prepared, spans);
                }
            } else {
                command(line, prepared, spans, problems);
            }
        }
        return new Result(spans, problems);
    }

    /** {@code #def}, the alias's name, then its body as {@link g_mungus.munguscript.engine.ScriptView#highlightDefinition} says. */
    private void definition(Line line, CommandPreProcessor.Prepared prepared, List<Span> spans) {
        int base = line.start() + line.indent();
        String text = line.command();
        spans.add(new Span(base, base + DEFINE.length(), Highlight.Kind.KEYWORD));
        Matcher declaration = DECLARATION.matcher(text).region(DEFINE.length(), text.length());
        if (!declaration.lookingAt()) {
            return;
        }
        spans.add(new Span(base + declaration.start(1), base + declaration.end(1), Highlight.Kind.ALIAS));
        int bodyStart = declaration.end();
        for (Highlight highlight : view.highlightDefinition(text.substring(bodyStart), source, prepared)) {
            spans.add(span(base + bodyStart, highlight));
        }
    }

    private void command(Line line, CommandPreProcessor.Prepared prepared, List<Span> spans, List<Problem> problems) {
        int base = line.start() + line.indent();
        String command = line.command();
        for (Highlight highlight : view.highlight(command, source, prepared)) {
            spans.add(span(base, highlight));
        }
        Optional<ScriptFailure> failure = view.check(command, source, prepared);
        failure.ifPresent(found -> problems.add(problem(base, command, found.faultRange(), found.reason())));
    }

    public List<Completion> complete(String script, int cursor) {
        List<Line> lines = Line.split(script);
        int index = Line.at(lines, cursor);
        Line line = lines.get(index);
        int base = line.start() + line.indent();
        if (cursor < base) {
            return List.of();
        }
        CommandPreProcessor.Prepared prepared = prepare(lines);
        // Spaces after the cursor are kept: a word may be about to start there.
        String text = line.text().substring(line.indent());
        List<Suggestion> suggestions;
        if (prepared.consumedLines().contains(index)) {
            Matcher declaration = DECLARATION.matcher(text);
            if (!isDefinition(text) || !declaration.region(DEFINE.length(), text.length()).lookingAt()
                    || cursor - base < declaration.end()) {
                return List.of();
            }
            base += declaration.end();
            suggestions = view.suggestExpression(text.substring(declaration.end()), cursor - base, source, null,
                    prepared).join().getList();
        } else {
            suggestions = view.suggest(text, cursor - base, source, prepared).join().getList();
        }
        List<Completion> completions = new ArrayList<>();
        for (Suggestion suggestion : suggestions) {
            int start = base + suggestion.getRange().getStart();
            if (start <= cursor) {
                completions.add(new Completion(start, suggestion.getText(),
                        suggestion.getTooltip() == null ? null : suggestion.getTooltip().getString()));
            }
        }
        return completions;
    }

    private CommandPreProcessor.Prepared prepare(List<Line> lines) {
        return preProcessors.prepare(lines.stream().map(Line::text).toList(), context);
    }

    private static boolean isDefinition(String line) {
        return line.startsWith(DEFINE) && (line.length() == DEFINE.length()
                || Character.isWhitespace(line.charAt(DEFINE.length())));
    }

    private static Span span(int base, Highlight highlight) {
        return new Span(base + highlight.range().getStart(), base + highlight.range().getEnd(), highlight.kind());
    }

    /** A problem in a command, or all of the command if it has no place. */
    private static Problem problem(int base, String command, @Nullable StringRange range, String message) {
        return range == null
                ? new Problem(base, base + command.length(), message)
                : new Problem(base + range.getStart(), base + range.getEnd(), message);
    }

    /**
     * One line of a script.
     *
     * @param start where it starts in the script
     * @param text  the line, without its line break
     */
    record Line(int start, String text) {

        static List<Line> split(String script) {
            List<Line> lines = new ArrayList<>();
            int start = 0;
            while (true) {
                int end = script.indexOf('\n', start);
                if (end < 0) {
                    lines.add(new Line(start, script.substring(start)));
                    return lines;
                }
                int textEnd = end > start && script.charAt(end - 1) == '\r' ? end - 1 : end;
                lines.add(new Line(start, script.substring(start, textEnd)));
                start = end + 1;
            }
        }

        /** The index of the line {@code offset} is on. */
        static int at(List<Line> lines, int offset) {
            for (int i = lines.size() - 1; i > 0; i--) {
                if (lines.get(i).start() <= offset) {
                    return i;
                }
            }
            return 0;
        }

        int end() {
            return start + text.length();
        }

        /** How far in the command starts. */
        int indent() {
            return text.length() - text.stripLeading().length();
        }

        /** The line as the runner reads it, without spaces either side; it starts at {@link #indent()}. */
        String command() {
            return text.strip();
        }
    }
}