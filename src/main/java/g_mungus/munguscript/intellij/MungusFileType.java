package g_mungus.munguscript.intellij;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/** {@code .mungus} files, such as a project's {@code build.mungus}. */
public final class MungusFileType extends LanguageFileType {
    public static final MungusFileType INSTANCE = new MungusFileType();

    private MungusFileType() {
        super(MungusLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "MungusScript";
    }

    @Override
    public @NotNull String getDescription() {
        return "MungusScript script";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "mungus";
    }

    @Override
    public Icon getIcon() {
        return MungusIcons.FILE;
    }
}
