package g_mungus.munguscript.intellij;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import g_mungus.munguscript.engine.Highlight;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.Vocabulary;
import g_mungus.munguscript.engine.codec.ScriptLanguageFile;
import g_mungus.munguscript.engine.failure.ScriptFailure;
import g_mungus.munguscript.engine.host.Match;
import g_mungus.munguscript.engine.preprocess.CommandPreProcessor;
import g_mungus.munguscript.engine.preprocess.PreProcessContext;
import g_mungus.munguscript.engine.preprocess.PreProcessDiagnostic;
import g_mungus.munguscript.engine.preprocess.PreProcessed;
import g_mungus.munguscript.engine.preprocess.SourceMap;
import g_mungus.munguscript.language.type.TypeKey;
import g_mungus.munguscript.runner.SimpleHost;
import g_mungus.munguscript.runner.SimpleSource;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads a whole script as the runner would, without running anything: what each word is, what is
 * wrong, what could be written at a point and what is wanted there. Offsets are in the script's
 * text. Nothing here depends on the IDE, so it can be tested on its own.
 *
 * <p>The language comes from a {@link ScriptLanguageFile}, which its host wrote, so the plugin knows
 * no language of its own. Lines are handled as {@link g_mungus.munguscript.runner.ScriptRunner}
 * handles them: aliases are prepared from the whole script, the lines that used up are skipped,
 * and every other non-blank line is one command, stripped.
 *
 * <p>Comments ({@code #} lines) and definitions may stand on any line, and each line sees only the
 * aliases defined above it.
 *
 * <p>What it offers while a line is written follows the ZeroPointSystems script terminal: words
 * from the first character, the usage of the argument being written, and {@code #def} and
 * {@code =} on definition lines.
 */
public final class ScriptAnalysis {
    private static final String DEFINE = "#def";
    private static final Pattern DECLARATION = Pattern.compile("\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=");
    private static final Pattern ALIAS_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    /** An argument's usage names it after its node's path; a person only needs the last part. */
    private static final Pattern NAMED_ARGUMENT_USAGE = Pattern.compile("<[^<>]*:([^<>:]+)>");
    private static final String ALIAS_NAME_USAGE = "<alias>";
    private static final String EXPECTED_EQUALS = "Expected '=' after alias name";

    private final ScriptView<SimpleSource> view;
    private final CommandPreProcessor preProcessors;
    /** An editor has no host context and runs nothing, so a script is read for no source in particular. */
    private final SimpleSource source = new SimpleSource(null);
    private final PreProcessContext context;
    /** What each word is and takes or gives, for completions: by name, where a name means one thing. */
    private final Map<String, Described> words = new HashMap<>();

    public ScriptAnalysis(ScriptView<SimpleSource> view) {
        this.view = view;
        this.preProcessors = view.aliases();
        this.context = new PreProcessContext(view.probe(source), null);
        describe(view.vocabulary());
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

    /**
     * Something wrong, and where. An empty range is a problem at that point, such as the end of a line.
     *
     * @param atLineEnd whether it reaches the end of its line, as a line still being written does
     */
    public record Problem(int start, int end, String message, boolean atLineEnd) {
    }

    public record Result(List<Span> spans, List<Problem> problems) {
    }

    /**
     * Something that could be written at the cursor.
     *
     * @param start   where the text it replaces starts; it replaces up to the cursor
     * @param tooltip what the engine says of it, such as an alias's expression
     * @param detail  what it takes or gives, such as {@code <string>} or {@code string}
     * @param kind    what kind of word it is, if it is one
     */
    public record Completion(int start, String text, @Nullable String tooltip, @Nullable String detail,
                             Highlight.@Nullable Kind kind) {
    }

    /**
     * What is wanted at the cursor, as the ZeroPointSystems terminal shows it under the line: the
     * usage of the argument being written, such as {@code <int>}, or why the line does not read.
     *
     * @param start where what it describes starts
     * @param error whether it says what is wrong, rather than what is wanted
     */
    public record Usage(int start, List<String> lines, boolean error) {
    }

    private record Described(String detail, Highlight.Kind kind) {
    }

    public Result analyse(String script) {
        List<Line> lines = Line.split(script);
        CommandPreProcessor.Prepared prepared = prepare(lines);
        List<Span> spans = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        for (PreProcessDiagnostic diagnostic : prepared.diagnostics()) {
            Line line = diagnostic.line() == null ? lines.get(0) : lines.get(diagnostic.line());
            int base = line.start() + line.indent();
            int end = base + line.command().length();
            problems.add(diagnostic.range() == null
                    ? new Problem(base, end, diagnostic.message(), true)
                    : new Problem(base + diagnostic.range().getStart(), base + diagnostic.range().getEnd(),
                    diagnostic.message(), base + diagnostic.range().getEnd() >= end));
        }
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            spans.addAll(spans(line, i, prepared));
            if (!line.text().isBlank() && !prepared.consumedLines().contains(i)) {
                check(line, prepared.at(i)).ifPresent(problems::add);
            }
        }
        return new Result(spans, problems);
    }

    /**
     * Reads {@code script} once, for colouring it a line at a time as an editor asks: given an
     * offset, what each word on its line is. Quick enough to run as each line is written, since
     * nothing is checked.
     */
    public Function<Integer, List<Span>> lineHighlighter(String script) {
        List<Line> lines = Line.split(script);
        CommandPreProcessor.Prepared prepared = prepare(lines);
        return offset -> {
            int index = Line.at(lines, offset);
            return spans(lines.get(index), index, prepared);
        };
    }

    /** What each word on a line is, seeing only the aliases defined above it. */
    private List<Span> spans(Line line, int index, CommandPreProcessor.Prepared prepared) {
        if (line.text().isBlank()) {
            return List.of();
        }
        int base = line.start() + line.indent();
        String text = line.command();
        List<Span> spans = new ArrayList<>();
        CommandPreProcessor.Prepared above = prepared.at(index);
        if (prepared.consumedLines().contains(index)) {
            if (isDefinition(text)) {
                definition(base, text, above, spans);
            }
            return spans;
        }
        for (Highlight highlight : view.highlight(text, source, above)) {
            spans.add(span(base, highlight));
        }
        return spans;
    }

    /** {@code #def}, the alias's name, then its body as {@link ScriptView#highlightDefinition} says. */
    private void definition(int base, String text, CommandPreProcessor.Prepared prepared, List<Span> spans) {
        spans.add(new Span(base, base + DEFINE.length(), Highlight.Kind.KEYWORD));
        Matcher name = ALIAS_NAME.matcher(text);
        int nameStart = DEFINE.length();
        while (nameStart < text.length() && Character.isWhitespace(text.charAt(nameStart))) {
            nameStart++;
        }
        if (nameStart == DEFINE.length() || !name.region(nameStart, text.length()).lookingAt()) {
            return;
        }
        spans.add(new Span(base + name.start(), base + name.end(), Highlight.Kind.ALIAS));
        Matcher declaration = DECLARATION.matcher(text).region(DEFINE.length(), text.length());
        if (!declaration.lookingAt()) {
            return;
        }
        int bodyStart = declaration.end();
        for (Highlight highlight : view.highlightDefinition(text.substring(bodyStart), source, prepared)) {
            spans.add(span(base + bodyStart, highlight));
        }
    }

    private Optional<Problem> check(Line line, CommandPreProcessor.Prepared prepared) {
        int base = line.start() + line.indent();
        String command = line.command();
        Optional<ScriptFailure> failure = view.check(command, source, prepared);
        return failure.map(found -> {
            StringRange range = found.faultRange();
            int start = range == null ? base : base + range.getStart();
            int end = range == null ? base + command.length() : base + range.getEnd();
            return new Problem(start, end, found.reason(), end >= base + command.length());
        });
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
        String typed = text.substring(0, cursor - base);
        CommandPreProcessor.Prepared above = prepared.at(index);
        if (prepared.consumedLines().contains(index)) {
            return definitionCompletions(text, typed, base, cursor, above);
        }
        return completions(view.suggest(text, cursor - base, source, above).join().getList(), base, cursor);
    }

    /** On a {@code #} line: {@code #def} itself, then {@code =} after the name, then the expression. */
    private List<Completion> definitionCompletions(String text, String typed, int base, int cursor,
                                                   CommandPreProcessor.Prepared prepared) {
        if (!typed.isEmpty() && DEFINE.startsWith(typed.strip()) && !typed.contains(" ")) {
            return List.of(new Completion(base, DEFINE + " ", "define an alias", null, Highlight.Kind.KEYWORD));
        }
        if (!isDefinition(text)) {
            return List.of();
        }
        Matcher declaration = DECLARATION.matcher(text).region(DEFINE.length(), text.length());
        if (!declaration.lookingAt()) {
            int slot = equalsSlot(typed);
            return slot >= 0 && slot == typed.length() && text.indexOf('=') < 0
                    ? List.of(new Completion(cursor, "= ", null, null, null))
                    : List.of();
        }
        if (typed.length() < declaration.end()) {
            return List.of();
        }
        int bodyStart = declaration.end();
        while (bodyStart < typed.length() && typed.charAt(bodyStart) == ' ') {
            bodyStart++;
        }
        return completions(view.suggestExpression(text.substring(bodyStart), typed.length() - bodyStart, source,
                null, prepared).join().getList(), base + bodyStart, cursor);
    }

    private List<Completion> completions(List<Suggestion> suggestions, int base, int cursor) {
        List<Completion> completions = new ArrayList<>();
        for (Suggestion suggestion : suggestions) {
            int start = base + suggestion.getRange().getStart();
            if (start <= cursor) {
                Described described = words.get(suggestion.getText());
                completions.add(new Completion(start, suggestion.getText(),
                        suggestion.getTooltip() == null ? null : suggestion.getTooltip().getString(),
                        described == null ? null : described.detail(),
                        described == null ? null : described.kind()));
            }
        }
        return completions;
    }

    /**
     * What is wanted at the cursor: on a command, the usage of the argument the cursor is in or
     * before; at the end of a line that does not read and has nothing to suggest, why; on a
     * definition, {@code <alias>} where the name goes. Empty if there is nothing to say.
     */
    public Optional<Usage> usage(String script, int cursor) {
        List<Line> lines = Line.split(script);
        int index = Line.at(lines, cursor);
        Line line = lines.get(index);
        int base = line.start() + line.indent();
        if (cursor < base || line.text().isBlank()) {
            return Optional.empty();
        }
        CommandPreProcessor.Prepared prepared = prepare(lines);
        String text = line.text().substring(line.indent());
        int lineCursor = cursor - base;
        if (prepared.consumedLines().contains(index)) {
            return definitionUsage(text, lineCursor, base);
        }
        CommandPreProcessor.Prepared above = prepared.at(index);
        PreProcessed processed = above.process(text, context);
        ParseResults<SimpleSource> parse = view.parse(processed.command(), source);
        Optional<Usage> wanted = nodeUsage(parse, processed.sourceMap(), text, lineCursor, base);
        if (wanted.isPresent()) {
            return wanted;
        }
        boolean atEnd = text.substring(lineCursor).isBlank();
        if (atEnd && complete(script, cursor).isEmpty()) {
            return view.check(text.strip(), source, above)
                    .map(failure -> new Usage(base + (failure.faultRange() == null ? 0 : failure.faultRange().getStart()),
                            List.of(failure.reason()), true));
        }
        return Optional.empty();
    }

    /**
     * The usage of what follows the node before the cursor, as Brigadier's smart usage gives it.
     * The parse is of the line after pre-processing, so it is only placed where the line was not
     * rewritten, or at its end.
     */
    private Optional<Usage> nodeUsage(ParseResults<SimpleSource> parse, SourceMap map, String text, int lineCursor,
                                      int base) {
        String processed = parse.getReader().getString();
        int processedCursor;
        if (map == SourceMap.IDENTITY) {
            processedCursor = lineCursor;
        } else if (text.substring(lineCursor).isBlank()) {
            processedCursor = processed.length();
        } else {
            return Optional.empty();
        }
        if (processedCursor < 0 || processedCursor > processed.length()) {
            return Optional.empty();
        }
        SuggestionContext<SimpleSource> found;
        try {
            found = parse.getContext().findSuggestionContext(processedCursor);
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
        Map<CommandNode<SimpleSource>, String> usages =
                new CommandDispatcher<SimpleSource>().getSmartUsage(found.parent, source);
        List<String> usage = usages.entrySet().stream()
                .filter(entry -> !(entry.getKey() instanceof LiteralCommandNode))
                .map(entry -> simplifyUsage(entry.getValue()))
                .distinct()
                .toList();
        return usage.isEmpty() ? Optional.empty()
                : Optional.of(new Usage(base + map.originalStart(found.startPos), usage, false));
    }

    /** {@code <alias>} while the name is written, and what is wrong when something else stands for the {@code =}. */
    private static Optional<Usage> definitionUsage(String text, int lineCursor, int base) {
        if (!isDefinition(text)) {
            return Optional.empty();
        }
        int nameStart = DEFINE.length();
        while (nameStart < text.length() && Character.isWhitespace(text.charAt(nameStart))) {
            nameStart++;
        }
        int equals = text.indexOf('=');
        int slot = equalsSlot(text);
        if (equals < 0 && slot >= 0 && slot < text.length()) {
            return Optional.of(new Usage(base + slot, List.of(EXPECTED_EQUALS), true));
        }
        Matcher name = ALIAS_NAME.matcher(text).region(nameStart, text.length());
        boolean inName = nameStart > DEFINE.length() && lineCursor >= nameStart
                && (equals < 0 || lineCursor <= equals)
                && (!name.lookingAt() || lineCursor <= name.end());
        return inName ? Optional.of(new Usage(base + nameStart, List.of(ALIAS_NAME_USAGE), false)) : Optional.empty();
    }

    /**
     * Where the {@code =} of a definition belongs: past the name and the spaces after it. -1 while
     * the name is not followed by a space.
     */
    private static int equalsSlot(String text) {
        if (!isDefinition(text)) {
            return -1;
        }
        int nameStart = DEFINE.length();
        while (nameStart < text.length() && Character.isWhitespace(text.charAt(nameStart))) {
            nameStart++;
        }
        Matcher name = ALIAS_NAME.matcher(text).region(nameStart, text.length());
        if (nameStart == DEFINE.length() || !name.lookingAt() || name.end() >= text.length()
                || !Character.isWhitespace(text.charAt(name.end()))) {
            return -1;
        }
        int slot = name.end();
        while (slot < text.length() && Character.isWhitespace(text.charAt(slot))) {
            slot++;
        }
        return slot;
    }

    /** An argument's usage as a person reads it: its hint, without where it leads. */
    private static String simplifyUsage(String usage) {
        int arrow = usage.indexOf(" -> ");
        String simplified = arrow < 0 ? usage : usage.substring(0, arrow);
        return NAMED_ARGUMENT_USAGE.matcher(simplified).replaceAll("<$1>");
    }

    /** What completions say of each word: what an executor takes, and what a getter or mapper gives. */
    private void describe(Vocabulary vocabulary) {
        for (Vocabulary.Word executor : vocabulary.executors()) {
            words.put(executor.name(), new Described(takes(executor), Highlight.Kind.EXECUTOR));
        }
        for (Vocabulary.Word getter : vocabulary.getters()) {
            words.put(getter.name(), new Described(gives(getter), Highlight.Kind.GETTER));
        }
        Map<String, List<Vocabulary.Word>> mappers = vocabulary.mappers().stream()
                .collect(Collectors.groupingBy(Vocabulary.Word::name));
        for (Map.Entry<String, List<Vocabulary.Word>> mapper : mappers.entrySet()) {
            List<String> details = mapper.getValue().stream()
                    .map(word -> (takes(word).isEmpty() ? "" : takes(word) + " ") + "→ " + gives(word))
                    .distinct().toList();
            // A name that means different things after different types says only what it is.
            words.putIfAbsent(mapper.getKey(), new Described(details.size() == 1 ? details.get(0) : "",
                    Highlight.Kind.MAPPER));
        }
    }

    private String takes(Vocabulary.Word word) {
        return word.forms().stream().filter(form -> form.argument() != null)
                .map(form -> "<" + form.argument() + ">").distinct().collect(Collectors.joining(" | "));
    }

    private String gives(Vocabulary.Word word) {
        return word.forms().stream().map(Vocabulary.Form::gives).filter(type -> type != null).distinct()
                .map(this::typeName).collect(Collectors.joining(" | "));
    }

    private String typeName(TypeKey type) {
        return view.typeName(type);
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
            int low = 0;
            int high = lines.size() - 1;
            while (low < high) {
                int middle = (low + high + 1) >>> 1;
                if (lines.get(middle).start() <= offset) {
                    low = middle;
                } else {
                    high = middle - 1;
                }
            }
            return low;
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
