package g_mungus.munguscript.intellij;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import g_mungus.munguscript.engine.Highlight;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Colours each word as it is typed, by what the script's language says it is
 * ({@link MungusHighlightingLexer}), comments as comments, and the rest as plain text.
 * {@link MungusAnnotator} colours the whole script again once it has been read in full, which
 * catches lines an edit changed the meaning of, such as those using an alias that was just defined.
 */
public final class MungusSyntaxHighlighter extends SyntaxHighlighterBase {
    private static final TextAttributesKey[] COMMENT = {MungusColors.COMMENT};
    private static final TextAttributesKey[] TEXT = {MungusColors.TEXT};
    private static final TextAttributesKey[] NONE = {};

    private final Supplier<@Nullable ScriptAnalysis> analysis;

    /** Without a language: lines are told apart, but not words. */
    public MungusSyntaxHighlighter() {
        this(() -> null);
    }

    public MungusSyntaxHighlighter(Supplier<@Nullable ScriptAnalysis> analysis) {
        this.analysis = analysis;
    }

    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new MungusHighlightingLexer(analysis);
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        if (tokenType == MungusTokenTypes.COMMENT) {
            return COMMENT;
        }
        Highlight.Kind kind = MungusTokenTypes.kindOf(tokenType);
        if (kind != null) {
            return new TextAttributesKey[]{MungusColors.TEXT, MungusColors.of(kind)};
        }
        return tokenType == MungusTokenTypes.COMMAND || tokenType == MungusTokenTypes.DEFINITION ? TEXT : NONE;
    }
}
