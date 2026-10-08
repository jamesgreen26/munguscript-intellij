package g_mungus.munguscript.intellij;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/** Finds and reads the language each script in a project is read by. */
@Service(Service.Level.PROJECT)
public final class MungusLanguages {
    private final Project project;
    private final LanguageCache cache = new LanguageCache();

    public MungusLanguages(Project project) {
        this.project = project;
    }

    public static MungusLanguages getInstance(Project project) {
        return project.getService(MungusLanguages.class);
    }

    /**
     * The language {@code script} is read by. Reads files, so it is for background threads.
     *
     * @param script null for a script that is not a file on disk
     */
    public LanguageCache.Loaded languageFor(@Nullable VirtualFile script) {
        VirtualFile base = ProjectUtil.guessProjectDir(project);
        if (base == null || script == null || script.getFileSystem().getNioPath(script) == null) {
            return new LanguageCache.Loaded(null, "MungusScript languages are found only for scripts in a project");
        }
        Path scriptPath = script.getFileSystem().getNioPath(script);
        LanguageLookup.Found found = LanguageLookup.find(base.toNioPath(),
                MungusSettings.getInstance(project).lookup(), scriptPath);
        if (found instanceof LanguageLookup.Language language) {
            return cache.get(language.file());
        }
        return new LanguageCache.Loaded(null, ((LanguageLookup.NotFound) found).reason());
    }
}
