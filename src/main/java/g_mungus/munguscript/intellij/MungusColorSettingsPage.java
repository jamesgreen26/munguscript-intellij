package g_mungus.munguscript.intellij;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Map;

/** Settings | Editor | Color Scheme | MungusScript. */
public final class MungusColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = {
            new AttributesDescriptor("Text", MungusColors.TEXT),
            new AttributesDescriptor("Comment", MungusColors.COMMENT),
            new AttributesDescriptor("Keyword", MungusColors.KEYWORD),
            new AttributesDescriptor("Executor", MungusColors.EXECUTOR),
            new AttributesDescriptor("Getter", MungusColors.GETTER),
            new AttributesDescriptor("Mapper", MungusColors.MAPPER),
            new AttributesDescriptor("Argument", MungusColors.ARGUMENT),
            new AttributesDescriptor("Alias", MungusColors.ALIAS),
            new AttributesDescriptor("Unparsed", MungusColors.UNPARSED),
    };

    private static final Map<String, TextAttributesKey> TAGS = Map.of(
            "keyword", MungusColors.KEYWORD,
            "executor", MungusColors.EXECUTOR,
            "getter", MungusColors.GETTER,
            "mapper", MungusColors.MAPPER,
            "argument", MungusColors.ARGUMENT,
            "alias", MungusColors.ALIAS,
            "unparsed", MungusColors.UNPARSED);

    @Override
    public @Nullable Icon getIcon() {
        return MungusIcons.FILE;
    }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return new MungusSyntaxHighlighter();
    }

    @Override
    public @NotNull String getDemoText() {
        return """
                # Stamps the version and copies the docs.
                <keyword>#def</keyword> <alias>stamp_file</alias> = <argument>"target/classes/version.txt"</argument>

                <executor>write_file</executor> <argument>value_of(</argument><getter>project_version</getter> <mapper>to</mapper> <argument>value_of(</argument><alias>stamp_file</alias><argument>))</argument>
                <keyword>if</keyword> <getter>project_version</getter> <mapper>ends_with</mapper> <argument>"-SNAPSHOT"</argument> <executor>warn</executor> <argument>"Building a snapshot"</argument>
                <executor>log</executor> <unparsed>frob</unparsed>
                """;
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return TAGS;
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "MungusScript";
    }
}
