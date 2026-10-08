package g_mungus.munguscript.intellij;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Colours comments as soon as they are typed, and other lines as plain text. Each word is then
 * coloured over that by {@link MungusAnnotator}.
 */
public final class MungusSyntaxHighlighter extends SyntaxHighlighterBase {
    private static final TextAttributesKey[] COMMENT = {MungusColors.COMMENT};
    private static final TextAttributesKey[] TEXT = {MungusColors.TEXT};
    private static final TextAttributesKey[] NONE = {};

    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new MungusLexer();
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        if (tokenType == MungusTokenTypes.COMMENT) {
            return COMMENT;
        }
        return tokenType == MungusTokenTypes.COMMAND || tokenType == MungusTokenTypes.DEFINITION ? TEXT : NONE;
    }
}
