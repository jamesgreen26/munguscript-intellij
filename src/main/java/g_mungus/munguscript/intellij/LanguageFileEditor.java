package g_mungus.munguscript.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.codec.ScriptLanguageFile;
import g_mungus.munguscript.runner.SimpleHost;
import g_mungus.munguscript.runner.SimpleSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;

/**
 * A language file shown as the words it holds, to read and search. It cannot be edited: its host
 * writes it. When the file is written again, it is shown again.
 */
public final class LanguageFileEditor extends UserDataHolderBase implements FileEditor {
    private final VirtualFile file;
    private final JPanel component = new JPanel(new BorderLayout());
    private final SearchTextField search = new SearchTextField(false);
    private final JEditorPane page = new JEditorPane();
    private @Nullable VocabularyPage vocabulary;
    private @Nullable String problem;

    public LanguageFileEditor(Project project, VirtualFile file) {
        this.file = file;
        page.setEditable(false);
        page.setEditorKit(HTMLEditorKitBuilder.simple());
        page.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription() != null
                    && event.getDescription().startsWith("#")) {
                page.scrollToReference(event.getDescription().substring(1));
            }
        });
        search.getTextEditor().getEmptyText().setText("Find a word or type");
        search.addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent event) {
                show();
            }
        });
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(JBUI.Borders.empty(6, 8));
        top.add(search, BorderLayout.CENTER);
        component.add(top, BorderLayout.NORTH);
        component.add(new JBScrollPane(page), BorderLayout.CENTER);

        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
                if (events.stream().anyMatch(event -> file.equals(event.getFile()))) {
                    ApplicationManager.getApplication().invokeLater(LanguageFileEditor.this::load);
                }
            }
        });
        load();
    }

    /** Reads the file again, and shows it. */
    private void load() {
        if (!file.isValid()) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(file.contentsToByteArray()))) {
            ScriptView<SimpleSource> view = new ScriptLanguageFile().read(in, SimpleHost::new);
            vocabulary = new VocabularyPage(view.vocabulary(), view::typeName, colours());
            problem = null;
        } catch (IOException | RuntimeException e) {
            vocabulary = null;
            problem = e.getMessage();
        }
        show();
    }

    private void show() {
        String html = vocabulary != null ? vocabulary.html(file.getName(), search.getText())
                : "<html><body style='margin: 8px 12px'><h2>" + VocabularyPage.escape(file.getName())
                + "</h2><p>This is not a language file that can be read: "
                + VocabularyPage.escape(String.valueOf(problem)) + "</p></body></html>";
        page.setText(html);
        page.setCaretPosition(0);
    }

    /** The editor's colours for each kind of word, so the page matches scripts. */
    private static VocabularyPage.Colours colours() {
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        return new VocabularyPage.Colours(colour(scheme, MungusColors.EXECUTOR), colour(scheme, MungusColors.GETTER),
                colour(scheme, MungusColors.MAPPER), colour(scheme, MungusColors.ARGUMENT),
                JBUI.CurrentTheme.Link.Foreground.ENABLED, UIUtil.getContextHelpForeground());
    }

    private static Color colour(EditorColorsScheme scheme, TextAttributesKey key) {
        TextAttributes attributes = scheme.getAttributes(key);
        Color colour = attributes == null ? null : attributes.getForegroundColor();
        return colour != null ? colour : UIUtil.getLabelForeground();
    }

    @Override
    public @NotNull JComponent getComponent() {
        return component;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return search;
    }

    @Override
    public @NotNull String getName() {
        return "Language";
    }

    @Override
    public @NotNull VirtualFile getFile() {
        return file;
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return file.isValid();
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public void dispose() {
    }
}
