package g_mungus.munguscript.intellij;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import g_mungus.munguscript.engine.Highlight;
import org.jetbrains.annotations.NotNull;

/**
 * Offers what the script's language suggests at the caret: words, aliases and example arguments,
 * each coloured as it is in a script and with what it takes or gives. {@link MungusTypedHandler}
 * brings it up after a space, as well as on letters.
 */
public final class MungusCompletionContributor extends CompletionContributor {

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        // The editor's own text: the copy completion works on has a placeholder at the caret.
        String text = parameters.getEditor().getDocument().getText();
        int cursor = parameters.getOffset();
        ScriptAnalysis analysis = MungusLanguages.getInstance(parameters.getOriginalFile().getProject())
                .languageFor(parameters.getOriginalFile().getVirtualFile()).analysis();
        if (analysis == null) {
            return;
        }
        for (ScriptAnalysis.Completion completion : analysis.complete(text, cursor)) {
            LookupElementBuilder element = LookupElementBuilder.create(completion.text());
            if (completion.kind() != null) {
                TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme()
                        .getAttributes(MungusColors.of(completion.kind()));
                if (attributes != null && attributes.getForegroundColor() != null) {
                    element = element.withItemTextForeground(attributes.getForegroundColor());
                }
                element = element.withBoldness(completion.kind() == Highlight.Kind.EXECUTOR);
            }
            if (completion.detail() != null && !completion.detail().isEmpty()) {
                element = element.withTypeText(completion.detail(), true);
            }
            if (completion.tooltip() != null) {
                element = element.withTailText("  " + completion.tooltip(), true);
            }
            result.withPrefixMatcher(text.substring(completion.start(), cursor)).addElement(element);
        }
    }
}
