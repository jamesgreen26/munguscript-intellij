package g_mungus.munguscript.intellij;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageLookupTest {

    @TempDir
    Path project;

    private Path file(String path) throws IOException {
        Path file = project.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "");
        return file;
    }

    private LanguageLookup.Found find(List<LanguageLookup.Mapping> mappings, String script) {
        return LanguageLookup.find(project, mappings, project.resolve(script));
    }

    @Test
    void theOnlyLanguageInDotMungusIsUsedForEveryScript() throws IOException {
        Path language = file(".mungus/build.mungustree");
        assertEquals(new LanguageLookup.Language(language), find(List.of(), "scripts/deploy.mungus"));
    }

    @Test
    void aLanguageNamedAfterTheScriptIsChosenFirst() throws IOException {
        file(".mungus/build.mungustree");
        Path deploy = file(".mungus/deploy.mungustree");
        assertEquals(new LanguageLookup.Language(deploy), find(List.of(), "deploy.mungus"));
        LanguageLookup.NotFound other = assertInstanceOf(LanguageLookup.NotFound.class, find(List.of(), "other.mungus"));
        assertTrue(other.reason().startsWith("Several language files"), other.reason());
    }

    @Test
    void withoutALanguageFileTheScriptSaysSo() {
        LanguageLookup.NotFound none = assertInstanceOf(LanguageLookup.NotFound.class, find(List.of(), "build.mungus"));
        assertTrue(none.reason().startsWith("No MungusScript language"), none.reason());
    }

    @Test
    void settingsAreUsedInsteadOfTheConvention() throws IOException {
        file(".mungus/build.mungustree");
        Path tools = file("languages/tools.mungustree");
        List<LanguageLookup.Mapping> mappings = List.of(
                new LanguageLookup.Mapping("tools/**.mungus", "languages/tools.mungustree"),
                new LanguageLookup.Mapping("*.mungus", "languages/missing.mungustree"));
        assertEquals(new LanguageLookup.Language(tools), find(mappings, "tools/a/b.mungus"));
        assertEquals(new LanguageLookup.NotFound("The language file languages/missing.mungustree does not exist"),
                find(mappings, "build.mungus"));
        LanguageLookup.NotFound unmapped = assertInstanceOf(LanguageLookup.NotFound.class, find(mappings, "x/y.mungus"));
        assertTrue(unmapped.reason().startsWith("No MungusScript language is set for x/y.mungus"), unmapped.reason());
    }

    @Test
    void aChangedLanguageFileIsReadAgain() throws IOException {
        Path language = TestLanguage.write(project.resolve(".mungus/test.mungustree"));
        LanguageCache cache = new LanguageCache();
        ScriptAnalysis first = cache.get(language).analysis();
        assertTrue(first != null && cache.get(language).analysis() == first);
        Files.writeString(language, "not a language");
        assertEquals("Could not read the language file test.mungustree: Not a MungusScript language file",
                cache.get(language).problem());
    }
}
