package g_mungus.munguscript.intellij;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Offers what can come next without a word being started, as the ZeroPointSystems terminal does:
 * completion after a space, an opening bracket, {@code #} or {@code =}, along with the usage of
 * what is wanted there. Letters already bring completion up.
 */
public final class MungusTypedHandler extends TypedHandlerDelegate {

    @Override
    public @NotNull Result checkAutoPopup(char typed, @NotNull Project project, @NotNull Editor editor,
                                          @NotNull PsiFile file) {
        if (!(file instanceof MungusFile)) {
            return Result.CONTINUE;
        }
        AutoPopupController popups = AutoPopupController.getInstance(project);
        if (typed == ' ' || typed == '(' || typed == '#' || typed == '=') {
            popups.scheduleAutoPopup(editor);
            popups.autoPopupParameterInfo(editor, null);
            return Result.STOP;
        }
        return Result.CONTINUE;
    }
}
