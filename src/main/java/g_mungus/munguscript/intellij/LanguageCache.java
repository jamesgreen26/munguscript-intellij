package g_mungus.munguscript.intellij;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Language files, read once each and again when they change on disk, as a host writes them anew.
 * Nothing here depends on the IDE.
 */
public final class LanguageCache {
    private final Map<Path, Entry> entries = new HashMap<>();

    /** A language, or why it could not be read. */
    public record Loaded(@Nullable ScriptAnalysis analysis, @Nullable String problem) {
    }

    private record Entry(FileTime modified, long size, Loaded loaded) {
    }

    public synchronized Loaded get(Path file) {
        FileTime modified;
        long size;
        try {
            modified = Files.getLastModifiedTime(file);
            size = Files.size(file);
        } catch (IOException e) {
            return new Loaded(null, "Could not read the language file " + file + ": " + e.getMessage());
        }
        Entry entry = entries.get(file);
        if (entry == null || !entry.modified().equals(modified) || entry.size() != size) {
            entry = new Entry(modified, size, load(file));
            entries.put(file, entry);
        }
        return entry.loaded();
    }

    private static Loaded load(Path file) {
        try {
            return new Loaded(ScriptAnalysis.read(file), null);
        } catch (IOException | RuntimeException e) {
            return new Loaded(null, "Could not read the language file " + file.getFileName() + ": " + e.getMessage());
        }
    }
}
