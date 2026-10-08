package g_mungus.munguscript.intellij;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MungusSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

    /** Colours by the language of the script being edited, found again each time it is lexed. */
    @Override
    public @NotNull SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile) {
        if (project == null || virtualFile == null) {
            return new MungusSyntaxHighlighter();
        }
        return new MungusSyntaxHighlighter(() -> project.isDisposed() ? null
                : MungusLanguages.getInstance(project).languageFor(virtualFile).analysis());
    }
}
