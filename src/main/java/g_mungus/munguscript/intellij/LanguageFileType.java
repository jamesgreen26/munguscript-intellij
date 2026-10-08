package g_mungus.munguscript.intellij;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/** {@code .mungustree} files: a language its host wrote, shown by {@link LanguageFileEditor}. */
public final class LanguageFileType implements FileType {
    public static final LanguageFileType INSTANCE = new LanguageFileType();

    private LanguageFileType() {
    }

    @Override
    public @NotNull String getName() {
        return "MungusScript Language";
    }

    @Override
    public @NotNull String getDescription() {
        return "MungusScript language file";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "mungustree";
    }

    @Override
    public Icon getIcon() {
        return MungusIcons.FILE;
    }

    @Override
    public boolean isBinary() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public @Nullable String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) {
        return null;
    }
}
