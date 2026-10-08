package g_mungus.munguscript.intellij;

import com.intellij.lang.Language;

public final class MungusLanguage extends Language {
    public static final MungusLanguage INSTANCE = new MungusLanguage();

    private MungusLanguage() {
        super("MungusScript");
    }
}
