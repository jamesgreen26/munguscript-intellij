package g_mungus.munguscript.intellij;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Colours each word and marks each problem, as the script's language reads it. Reading runs off
 * the UI thread, on a copy of the text taken when the file was last changed. Without a language,
 * the file says why.
 *
 * <p>As in the ZeroPointSystems terminal, the line being written is not marked for what it still
 * lacks: a problem that reaches the end of the caret's line is left to the usage hint
 * ({@link MungusParameterInfoHandler}). One before the end is still marked.
 */
public final class MungusAnnotator extends ExternalAnnotator<MungusAnnotator.Script, MungusAnnotator.Annotated> {
    private static final Logger LOG = Logger.getInstance(MungusAnnotator.class);

    /**
     * What is read: the text as it was, where the script is, to find its language by, and the
     * caret's line, from its start to its end, or -1 when no editor is showing it.
     */
    record Script(String text, Project project, @Nullable VirtualFile file, int caretLineStart, int caretLineEnd) {
    }

    /** What the language says of the script, or why there is no language for it. */
    record Annotated(ScriptAnalysis.@Nullable Result result, @Nullable String unavailable) {
    }

    @Override
    public @Nullable Script collectInformation(@NotNull PsiFile file) {
        return new Script(file.getText(), file.getProject(), file.getOriginalFile().getVirtualFile(), -1, -1);
    }

    @Override
    public @Nullable Script collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
        Document document = editor.getDocument();
        int line = document.getLineNumber(Math.min(editor.getCaretModel().getOffset(), document.getTextLength()));
        return new Script(file.getText(), file.getProject(), file.getOriginalFile().getVirtualFile(),
                document.getLineStartOffset(line), document.getLineEndOffset(line));
    }

    @Override
    public @Nullable Annotated doAnnotate(Script script) {
        LanguageCache.Loaded language = MungusLanguages.getInstance(script.project()).languageFor(script.file());
        if (language.analysis() == null) {
            return new Annotated(null, language.problem());
        }
        try {
            ScriptAnalysis.Result result = language.analysis().analyse(script.text());
            List<ScriptAnalysis.Problem> shown = result.problems().stream()
                    .filter(problem -> !(problem.atLineEnd() && problem.start() >= script.caretLineStart()
                            && problem.start() <= script.caretLineEnd()))
                    .toList();
            return new Annotated(new ScriptAnalysis.Result(result.spans(), shown), null);
        } catch (RuntimeException e) {
            LOG.warn("Could not read the script", e);
            return null;
        }
    }

    @Override
    public void apply(@NotNull PsiFile file, Annotated annotated, @NotNull AnnotationHolder holder) {
        if (annotated.unavailable() != null) {
            holder.newAnnotation(HighlightSeverity.WARNING, annotated.unavailable()).fileLevel().create();
            return;
        }
        ScriptAnalysis.Result result = annotated.result();
        int length = file.getTextLength();
        for (ScriptAnalysis.Span span : result.spans()) {
            TextAttributesKey colour = MungusColors.of(span.kind());
            if (span.start() < span.end() && span.end() <= length) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(new TextRange(span.start(), span.end()))
                        .textAttributes(colour)
                        .create();
            }
        }
        for (ScriptAnalysis.Problem problem : result.problems()) {
            int start = Math.min(problem.start(), length);
            int end = Math.min(Math.max(problem.end(), start), length);
            var annotation = holder.newAnnotation(HighlightSeverity.ERROR, problem.message())
                    .range(new TextRange(start, end));
            if (start == end) {
                // Something missing at the end of a line, such as an argument.
                annotation = annotation.afterEndOfLine();
            }
            annotation.create();
        }
    }
}
