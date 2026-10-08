package g_mungus.munguscript.intellij;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import g_mungus.munguscript.engine.Highlight;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The lexer only tells lines apart. What each word in a line is depends on the script's language,
 * so that is left to {@link MungusAnnotator}, which reads it by its language file.
 */
public final class MungusTokenTypes {
    public static final IFileElementType FILE = new IFileElementType(MungusLanguage.INSTANCE);

    /** A {@code #} line other than a definition. */
    public static final IElementType COMMENT = new IElementType("COMMENT", MungusLanguage.INSTANCE);
    /** A {@code #def name = body} line. */
    public static final IElementType DEFINITION = new IElementType("DEFINITION", MungusLanguage.INSTANCE);
    /** Any other line. */
    public static final IElementType COMMAND = new IElementType("COMMAND", MungusLanguage.INSTANCE);

    public static final TokenSet COMMENTS = TokenSet.create(COMMENT);

    /**
     * A word of a command or definition, by what the language says it is. Only
     * {@link MungusHighlightingLexer} makes these, for colouring.
     */
    private static final Map<Highlight.Kind, IElementType> WORDS = new EnumMap<>(Highlight.Kind.class);

    static {
        for (Highlight.Kind kind : Highlight.Kind.values()) {
            WORDS.put(kind, new IElementType("WORD_" + kind.name(), MungusLanguage.INSTANCE));
        }
    }

    public static IElementType word(Highlight.Kind kind) {
        return WORDS.get(kind);
    }

    /** The kind of word a token is, or null for any other token. */
    public static Highlight.@Nullable Kind kindOf(IElementType type) {
        for (Map.Entry<Highlight.Kind, IElementType> word : WORDS.entrySet()) {
            if (word.getValue() == type) {
                return word.getKey();
            }
        }
        return null;
    }

    private MungusTokenTypes() {
    }
}
