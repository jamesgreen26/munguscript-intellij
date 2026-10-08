package g_mungus.munguscript.intellij;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public final class MungusFile extends PsiFileBase {

    public MungusFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, MungusLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        return MungusFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "MungusScript file";
    }
}
