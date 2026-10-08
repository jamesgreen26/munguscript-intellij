package g_mungus.munguscript.intellij;

import com.intellij.lang.Commenter;
import org.jetbrains.annotations.Nullable;

/** Comments are {@code #} lines, and only stand in the block at the top of a script, with the definitions. */
public final class MungusCommenter implements Commenter {

    @Override
    public String getLineCommentPrefix() {
        return "# ";
    }

    @Override
    public @Nullable String getBlockCommentPrefix() {
        return null;
    }

    @Override
    public @Nullable String getBlockCommentSuffix() {
        return null;
    }

    @Override
    public @Nullable String getCommentedBlockCommentPrefix() {
        return null;
    }

    @Override
    public @Nullable String getCommentedBlockCommentSuffix() {
        return null;
    }
}
