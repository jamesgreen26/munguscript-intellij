package g_mungus.munguscript.intellij;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;

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

    private MungusTokenTypes() {
    }
}
