package g_mungus.munguscript.intellij;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.stream.Stream;

/**
 * Which language file a script is read by. Settings map scripts to language files by glob, first
 * match first. With none set, the convention is a {@code .mungus/*.mungustree} in the project:
 * the one named after the script ({@code build.mungus} reads by {@code build.mungustree}), or the
 * only one there. Nothing here depends on the IDE.
 */
public final class LanguageLookup {
    public static final String DIRECTORY = ".mungus";
    public static final String EXTENSION = ".mungustree";
    private static final String SETTINGS = "Settings | Languages & Frameworks | MungusScript";

    private LanguageLookup() {
    }

    /**
     * @param scripts  a glob over script paths relative to the project, with {@code /} between
     *                 directories, e.g. {@code **.mungus} for every script
     * @param language the language file, relative to the project or absolute
     */
    public record Mapping(String scripts, String language) {
    }

    /** A language file, or why there is none. */
    public sealed interface Found {
    }

    public record Language(Path file) implements Found {
    }

    public record NotFound(String reason) implements Found {
    }

    public static Found find(Path project, List<Mapping> mappings, Path script) {
        Path relative = script.startsWith(project) ? project.relativize(script) : script.getFileName();
        if (!mappings.isEmpty()) {
            return mapped(project, mappings, relative);
        }
        Path directory = project.resolve(DIRECTORY);
        List<Path> files;
        try (Stream<Path> listed = Files.isDirectory(directory) ? Files.list(directory) : Stream.empty()) {
            files = listed.filter(file -> file.getFileName().toString().endsWith(EXTENSION) && Files.isRegularFile(file))
                    .sorted().toList();
        } catch (IOException e) {
            return new NotFound("Could not list " + DIRECTORY + ": " + e.getMessage());
        }
        String name = script.getFileName().toString();
        Path named = directory.resolve((name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name) + EXTENSION);
        if (files.contains(named)) {
            return new Language(named);
        } else if (files.size() == 1) {
            return new Language(files.get(0));
        } else if (files.isEmpty()) {
            return new NotFound("No MungusScript language for this script: put a language file in " + DIRECTORY
                    + "/, or choose one in " + SETTINGS);
        }
        return new NotFound("Several language files are in " + DIRECTORY + "/: name one " + named.getFileName()
                + ", or choose one in " + SETTINGS);
    }

    private static Found mapped(Path project, List<Mapping> mappings, Path relative) {
        String path = relative.toString().replace('\\', '/');
        for (Mapping mapping : mappings) {
            PathMatcher matcher;
            try {
                matcher = FileSystems.getDefault().getPathMatcher("glob:" + mapping.scripts());
            } catch (IllegalArgumentException e) {
                return new NotFound("'" + mapping.scripts() + "' in " + SETTINGS + " is not a glob: " + e.getMessage());
            }
            if (matcher.matches(Path.of(path))) {
                Path file = project.resolve(mapping.language());
                return Files.isRegularFile(file) ? new Language(file)
                        : new NotFound("The language file " + mapping.language() + " does not exist");
            }
        }
        return new NotFound("No MungusScript language is set for " + path + " in " + SETTINGS);
    }
}
