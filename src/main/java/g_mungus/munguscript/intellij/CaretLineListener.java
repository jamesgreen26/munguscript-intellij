package g_mungus.munguscript.intellij;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Reads a script again when the caret leaves a line, since {@link MungusAnnotator} leaves the
 * caret's line unmarked for what it still lacks: once it is left, it is no longer being written.
 */
public final class CaretLineListener implements EditorFactoryListener {

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        Project project = editor.getProject();
        VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (project == null || file == null || file.getFileType() != MungusFileType.INSTANCE) {
            return;
        }
        editor.getCaretModel().addCaretListener(new CaretListener() {
            @Override
            public void caretPositionChanged(@NotNull CaretEvent event) {
                if (event.getOldPosition().line == event.getNewPosition().line || project.isDisposed()) {
                    return;
                }
                PsiFile script = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
                if (script != null) {
                    DaemonCodeAnalyzer.getInstance(project).restart(script, "MungusScript caret left a line");
                }
            }
        });
    }
}
