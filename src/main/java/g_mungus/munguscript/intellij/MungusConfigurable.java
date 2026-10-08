package g_mungus.munguscript.intellij;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Settings | Languages & Frameworks | MungusScript: which language file each script is read by. */
public final class MungusConfigurable implements Configurable {
    private final Project project;
    private @Nullable ListTableModel<MungusSettings.Mapping> model;

    public MungusConfigurable(Project project) {
        this.project = project;
    }

    @Override
    public @Nls String getDisplayName() {
        return "MungusScript";
    }

    @Override
    public JComponent createComponent() {
        model = new ListTableModel<>(
                column("Scripts", mapping -> mapping.scripts, (mapping, value) -> mapping.scripts = value),
                column("Language file", mapping -> mapping.language, (mapping, value) -> mapping.language = value));
        TableView<MungusSettings.Mapping> table = new TableView<>(model);
        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        panel.add(ToolbarDecorator.createDecorator(table)
                .setAddAction(button -> model.addRow(new MungusSettings.Mapping("**.mungus",
                        LanguageLookup.DIRECTORY + "/build" + LanguageLookup.EXTENSION)))
                .createPanel(), BorderLayout.CENTER);
        JBLabel help = new JBLabel("<html>Scripts are globs over paths in the project, such as <code>**.mungus</code> for"
                + " all of them; the first that matches is used. A language file is written by the language's host,"
                + " e.g. <code>mvn munguscript:language</code>.<br>With none here, a <code>.mungus/*.mungustree</code>"
                + " file is used: the one named after the script, or the only one.</html>");
        help.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        help.setForeground(UIUtil.getContextHelpForeground());
        panel.add(help, BorderLayout.SOUTH);
        reset();
        return panel;
    }

    private static ColumnInfo<MungusSettings.Mapping, String> column(String name,
                                                                     Function<MungusSettings.Mapping, String> get,
                                                                     BiConsumer<MungusSettings.Mapping, String> set) {
        return new ColumnInfo<>(name) {
            @Override
            public String valueOf(MungusSettings.Mapping mapping) {
                return get.apply(mapping);
            }

            @Override
            public boolean isCellEditable(MungusSettings.Mapping mapping) {
                return true;
            }

            @Override
            public void setValue(MungusSettings.Mapping mapping, String value) {
                set.accept(mapping, value);
            }
        };
    }

    @Override
    public boolean isModified() {
        return model != null && !model.getItems().equals(MungusSettings.getInstance(project).mappings());
    }

    @Override
    public void apply() {
        if (model != null) {
            MungusSettings.getInstance(project).setMappings(model.getItems());
            DaemonCodeAnalyzer.getInstance(project).restart("MungusScript languages changed");
        }
    }

    @Override
    public void reset() {
        if (model != null) {
            model.setItems(new ArrayList<>(MungusSettings.getInstance(project).mappings()));
        }
    }

    @Override
    public void disposeUIResources() {
        model = null;
    }
}
