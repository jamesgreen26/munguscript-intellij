package g_mungus.munguscript.intellij;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Whitespace, then the rest of the line as one token: a comment, a definition or a command. */
public final class MungusLexer extends LexerBase {
    private CharSequence buffer = "";
    private int end;
    private int tokenStart;
    private int tokenEnd;
    private @Nullable IElementType type;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.end = endOffset;
        locate(startOffset);
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return type;
    }

    @Override
    public int getTokenStart() {
        return tokenStart;
    }

    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }

    @Override
    public void advance() {
        locate(tokenEnd);
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return end;
    }

    private void locate(int from) {
        tokenStart = from;
        tokenEnd = from;
        if (from >= end) {
            type = null;
            return;
        }
        if (Character.isWhitespace(buffer.charAt(from))) {
            while (tokenEnd < end && Character.isWhitespace(buffer.charAt(tokenEnd))) {
                tokenEnd++;
            }
            type = TokenType.WHITE_SPACE;
            return;
        }
        while (tokenEnd < end && buffer.charAt(tokenEnd) != '\n' && buffer.charAt(tokenEnd) != '\r') {
            tokenEnd++;
        }
        type = isDefinition(from) ? MungusTokenTypes.DEFINITION
                : buffer.charAt(from) == '#' ? MungusTokenTypes.COMMENT
                : MungusTokenTypes.COMMAND;
    }

    private boolean isDefinition(int from) {
        int after = from + "#def".length();
        return after <= tokenEnd && "#def".contentEquals(buffer.subSequence(from, after))
                && (after == tokenEnd || Character.isWhitespace(buffer.charAt(after)));
    }
}
