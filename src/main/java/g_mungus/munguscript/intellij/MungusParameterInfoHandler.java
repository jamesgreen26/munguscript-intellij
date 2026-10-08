package g_mungus.munguscript.intellij;

import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContext;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Shows what is wanted where the caret is, as the ZeroPointSystems terminal does under the line:
 * the usage of the argument being written, such as {@code <int>}, or why the line does not read
 * when it is written to its end and nothing can be suggested. Up after a space, and with
 * Parameter Info (Ctrl+P / ⌘P) anywhere.
 */
public final class MungusParameterInfoHandler implements ParameterInfoHandler<PsiElement, MungusParameterInfoHandler.Hint>,
        DumbAware {

    /** What the hint shows; changed in place as the caret moves, so one hint follows the line. */
    static final class Hint {
        private ScriptAnalysis.Usage usage;

        Hint(ScriptAnalysis.Usage usage) {
            this.usage = usage;
        }
    }

    @Override
    public @Nullable PsiElement findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
        Optional<ScriptAnalysis.Usage> usage = usage(context.getFile(), context.getOffset());
        PsiElement line = line(context.getFile(), context.getOffset());
        if (usage.isEmpty() || line == null) {
            return null;
        }
        context.setItemsToShow(new Object[]{new Hint(usage.get())});
        return line;
    }

    @Override
    public void showParameterInfo(@NotNull PsiElement element, @NotNull CreateParameterInfoContext context) {
        Object[] items = context.getItemsToShow();
        int offset = items != null && items.length > 0 && items[0] instanceof Hint hint
                ? hint.usage.start() : element.getTextRange().getStartOffset();
        context.showHint(element, offset, this);
    }

    @Override
    public @Nullable PsiElement findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
        Optional<ScriptAnalysis.Usage> usage = usage(context.getFile(), context.getOffset());
        Object[] shown = context.getObjectsToView();
        if (usage.isEmpty() || shown == null || shown.length == 0 || !(shown[0] instanceof Hint hint)) {
            return null;
        }
        hint.usage = usage.get();
        return line(context.getFile(), context.getOffset());
    }

    @Override
    public void updateParameterInfo(@NotNull PsiElement element, @NotNull UpdateParameterInfoContext context) {
        context.setParameterOwner(element);
    }

    @Override
    public void updateUI(Hint hint, @NotNull ParameterInfoUIContext context) {
        String text = String.join("\n", hint.usage.lines());
        context.setupUIComponentPresentation(text, 0, 0, false, false, false,
                hint.usage.error() ? JBColor.namedColor("Editor.ToolTip.background", context.getDefaultParameterColor())
                        : context.getDefaultParameterColor());
    }

    /** Spaces matter: after one, something else is wanted. */
    @Override
    public boolean isWhitespaceSensitive() {
        return true;
    }

    /** The line the caret is on, or just after: the hint's owner. */
    private static @Nullable PsiElement line(PsiFile file, int offset) {
        PsiElement element = file.findElementAt(offset);
        if (element == null || element.getText().isBlank()) {
            element = offset > 0 ? file.findElementAt(offset - 1) : null;
        }
        return element;
    }

    private static Optional<ScriptAnalysis.Usage> usage(PsiFile file, int offset) {
        if (!(file instanceof MungusFile)) {
            return Optional.empty();
        }
        ScriptAnalysis analysis = MungusLanguages.getInstance(file.getProject())
                .languageFor(file.getOriginalFile().getVirtualFile()).analysis();
        if (analysis == null) {
            return Optional.empty();
        }
        try {
            return analysis.usage(file.getText(), offset);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
