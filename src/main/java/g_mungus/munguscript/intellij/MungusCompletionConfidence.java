package g_mungus.munguscript.intellij;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

/**
 * Lets completion come up where no word has been started, such as after a space, as
 * {@link MungusTypedHandler} asks: in a script, what can come next is worth showing there too.
 */
public final class MungusCompletionConfidence extends CompletionConfidence {

    @Override
    public @NotNull ThreeState shouldSkipAutopopup(@NotNull Editor editor, @NotNull PsiElement contextElement,
                                                   @NotNull PsiFile psiFile, int offset) {
        return psiFile instanceof MungusFile ? ThreeState.NO : ThreeState.UNSURE;
    }
}
