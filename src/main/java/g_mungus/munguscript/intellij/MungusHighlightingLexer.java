package g_mungus.munguscript.intellij;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Colours each word as it is written, as the ZeroPointSystems terminal does: the lines
 * {@link MungusLexer} tells apart, with each command and definition split into a token per word,
 * by what the script's language says the word is. Lines are read as they are reached, so an edit
 * only reads the lines the editor asks for again.
 *
 * <p>Tokens that start a line have state 0 and the rest 1, so the editor only starts lexing again
 * at the start of a line, where a line can be read on its own.
 */
public final class MungusHighlightingLexer extends LexerBase {
    private static final int LINE_START = 0;
    private static final int IN_LINE = 1;

    private final Supplier<@Nullable ScriptAnalysis> analysis;
    private final MungusLexer lines = new MungusLexer();
    private final Deque<Token> pending = new ArrayDeque<>();
    private CharSequence buffer = "";
    private int end;
    private @Nullable Function<Integer, List<ScriptAnalysis.Span>> highlighter;
    private @Nullable Token token;

    private record Token(int start, int end, IElementType type, int state) {
    }

    /**
     * @param analysis the script's language, asked for each time lexing starts, so a language
     *                 written later is used; null for none, when lines are only told apart
     */
    public MungusHighlightingLexer(Supplier<@Nullable ScriptAnalysis> analysis) {
        this.analysis = analysis;
    }

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.end = endOffset;
        this.pending.clear();
        this.highlighter = null;
        try {
            ScriptAnalysis language = analysis.get();
            if (language != null) {
                highlighter = language.lineHighlighter(buffer.toString());
            }
        } catch (RuntimeException e) {
            // Without a language, lines are still told apart.
        }
        lines.start(buffer, startOffset, endOffset, 0);
        advance();
    }

    @Override
    public void advance() {
        if (pending.isEmpty()) {
            fill();
        }
        token = pending.pollFirst();
    }

    /** The pieces of the next line token. */
    private void fill() {
        IElementType type = lines.getTokenType();
        if (type == null) {
            return;
        }
        int start = lines.getTokenStart();
        int tokenEnd = lines.getTokenEnd();
        lines.advance();
        if (type == TokenType.WHITE_SPACE) {
            // Each line starts a token of its own, where lexing can start again.
            int piece = start;
            for (int i = start; i < tokenEnd; i++) {
                if (buffer.charAt(i) == '\n') {
                    add(piece, i + 1, type);
                    piece = i + 1;
                }
            }
            add(piece, tokenEnd, type);
        } else if (type == MungusTokenTypes.COMMENT || highlighter == null) {
            add(start, tokenEnd, type);
        } else {
            words(start, tokenEnd, type);
        }
    }

    /** A line, split into its words and the text between them. */
    private void words(int start, int lineEnd, IElementType text) {
        List<ScriptAnalysis.Span> spans;
        try {
            spans = highlighter.apply(start);
        } catch (RuntimeException e) {
            spans = List.of();
        }
        int at = start;
        for (ScriptAnalysis.Span span : spans) {
            int from = Math.max(span.start(), at);
            int to = Math.min(span.end(), lineEnd);
            if (to <= from) {
                continue;
            }
            add(at, from, text);
            add(from, to, MungusTokenTypes.word(span.kind()));
            at = to;
        }
        add(at, lineEnd, text);
    }

    private void add(int start, int end, IElementType type) {
        if (end > start) {
            boolean lineStart = start == 0 || buffer.charAt(start - 1) == '\n';
            pending.addLast(new Token(start, end, type, lineStart ? LINE_START : IN_LINE));
        }
    }

    @Override
    public int getState() {
        return token == null ? LINE_START : token.state();
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return token == null ? null : token.type();
    }

    @Override
    public int getTokenStart() {
        return token == null ? end : token.start();
    }

    @Override
    public int getTokenEnd() {
        return token == null ? end : token.end();
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return end;
    }
}
