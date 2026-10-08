package g_mungus.munguscript.intellij;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import org.jetbrains.annotations.NotNull;

/** Offers what the script's language suggests at the caret: words, aliases and example arguments. */
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
            if (completion.tooltip() != null) {
                element = element.withTypeText(completion.tooltip(), true);
            }
            result.withPrefixMatcher(text.substring(completion.start(), cursor)).addElement(element);
        }
    }
}
