package g_mungus.munguscript.intellij;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Reads scripts again when a language file is written, so a host's new words show at once. */
public final class LanguageFileListener implements BulkFileListener {
    private final Project project;

    public LanguageFileListener(Project project) {
        this.project = project;
    }

    @Override
    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        if (events.stream().anyMatch(event -> event.getPath().endsWith(LanguageLookup.EXTENSION))) {
            DaemonCodeAnalyzer.getInstance(project).restart("MungusScript languages changed");
        }
    }
}
