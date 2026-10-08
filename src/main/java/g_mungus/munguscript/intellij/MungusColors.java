package g_mungus.munguscript.intellij;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import g_mungus.munguscript.engine.Highlight;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/**
 * Colours for each kind of word. Dark schemes get the ZeroPointSystems script terminal's colours
 * ({@code colorSchemes/MungusDarcula.xml}); others fall back to the scheme's colours for the
 * nearest thing in other languages.
 */
public final class MungusColors {
    /** Whatever is not a word of its own, such as spaces, {@code =} and brackets. */
    public static final TextAttributesKey TEXT =
            createTextAttributesKey("MUNGUS_TEXT", HighlighterColors.TEXT);
    public static final TextAttributesKey COMMENT =
            createTextAttributesKey("MUNGUS_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey KEYWORD =
            createTextAttributesKey("MUNGUS_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey EXECUTOR =
            createTextAttributesKey("MUNGUS_EXECUTOR", DefaultLanguageHighlighterColors.FUNCTION_CALL);
    public static final TextAttributesKey GETTER =
            createTextAttributesKey("MUNGUS_GETTER", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    public static final TextAttributesKey MAPPER =
            createTextAttributesKey("MUNGUS_MAPPER", DefaultLanguageHighlighterColors.INSTANCE_METHOD);
    public static final TextAttributesKey ARGUMENT =
            createTextAttributesKey("MUNGUS_ARGUMENT", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey ALIAS =
            createTextAttributesKey("MUNGUS_ALIAS", DefaultLanguageHighlighterColors.CONSTANT);
    /** Text a line could not be read as, from where reading stopped. It is also marked as an error. */
    public static final TextAttributesKey UNPARSED =
            createTextAttributesKey("MUNGUS_UNPARSED", HighlighterColors.BAD_CHARACTER);

    private MungusColors() {
    }

    /** The colour for a kind of word. */
    public static TextAttributesKey of(Highlight.Kind kind) {
        return switch (kind) {
            case KEYWORD -> KEYWORD;
            case EXECUTOR -> EXECUTOR;
            case GETTER -> GETTER;
            case MAPPER -> MAPPER;
            case ARGUMENT -> ARGUMENT;
            case ALIAS -> ALIAS;
            case UNPARSED -> UNPARSED;
        };
    }
}
