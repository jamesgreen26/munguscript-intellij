package g_mungus.munguscript.intellij;

import g_mungus.munguscript.engine.ScriptView;
import g_mungus.munguscript.engine.codec.ScriptLanguageFile;
import g_mungus.munguscript.runner.SimpleHost;
import g_mungus.munguscript.runner.SimpleSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabularyPageTest {
    private static final VocabularyPage.Colours COLOURS =
            new VocabularyPage.Colours(Color.ORANGE, Color.MAGENTA, Color.BLUE, Color.GREEN, Color.CYAN, Color.GRAY);

    @TempDir
    static Path dir;

    private static VocabularyPage page;

    @BeforeAll
    static void readTheLanguage() throws IOException {
        Path file = TestLanguage.write(dir.resolve("test.mungustree"));
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            ScriptView<SimpleSource> view = new ScriptLanguageFile().read(in, SimpleHost::new);
            page = new VocabularyPage(view.vocabulary(), view::typeName, COLOURS);
        }
    }

    @Test
    void listsEachKindOfWordWithWhatItTakesAndGives() {
        String html = page.html("test.mungustree", "");
        assertTrue(html.contains("<h3>Executors</h3>"), html);
        assertTrue(html.contains(">write_file<"), html);
        assertTrue(html.contains(">project_version<"), html);
        assertTrue(html.contains(">ends_with<"), html);
        // A host's own type links to its section.
        assertTrue(html.contains("<a href='#type-pair'>"), html);
        assertTrue(html.contains("<a name='type-pair'>"), html);
    }

    @Test
    void aFilterShowsOnlyTheWordsThatMatch() {
        String html = page.html("test.mungustree", "WRITE");
        assertTrue(html.contains(">write_file<"), html);
        assertFalse(html.contains(">project_version<"), html);
        assertTrue(page.html("test.mungustree", "zzz").contains("Nothing matches 'zzz'"));
    }

    @Test
    void textIsEscaped() {
        assertFalse(page.html("<script>", "").contains("<script>"));
        // The built-in mapper <, a word like any other.
        assertTrue(page.html("test.mungustree", "").contains("'>&lt;</font>"));
    }
}
