package g_mungus.munguscript.intellij;

import g_mungus.munguscript.engine.Vocabulary;
import g_mungus.munguscript.language.type.TypeKey;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A language's words as a page to read: executors, getters, mappers by the type they follow, and
 * types, each with what it takes and gives. Type names link to the type's own section. Nothing
 * here depends on the IDE.
 */
public final class VocabularyPage {

    /**
     * How each kind of word is coloured, as in the editor.
     *
     * @param type the links to a type's section
     * @param text notes, such as the counts at the top
     */
    public record Colours(Color executor, Color getter, Color mapper, Color argument, Color type, Color text) {
    }

    private final Vocabulary vocabulary;
    private final Function<TypeKey, String> typeName;
    private final Colours colours;

    /**
     * @param typeName how the language writes a type's key, e.g. {@code int}
     */
    public VocabularyPage(Vocabulary vocabulary, Function<TypeKey, String> typeName, Colours colours) {
        this.vocabulary = vocabulary;
        this.typeName = typeName;
        this.colours = colours;
    }

    /** The anchor a type's section has, which its links lead to. */
    public static String anchor(String typeName) {
        return "type-" + typeName.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    /**
     * The page, showing only the words whose name or types hold {@code filter}, ignoring case.
     *
     * @param title what the language is called, e.g. its file's name
     */
    public String html(String title, String filter) {
        String wanted = filter.strip().toLowerCase(Locale.ROOT);
        List<Vocabulary.Word> executors = matching(vocabulary.executors(), wanted);
        List<Vocabulary.Word> getters = matching(vocabulary.getters(), wanted);
        List<Vocabulary.Word> mappers = matching(vocabulary.mappers(), wanted);
        List<Vocabulary.Type> types = vocabulary.types().stream()
                .filter(type -> wanted.isEmpty() || name(type.key()).toLowerCase(Locale.ROOT).contains(wanted))
                .sorted((a, b) -> name(a.key()).compareTo(name(b.key())))
                .toList();

        StringBuilder page = new StringBuilder("<html><body style='margin: 8px 12px'>");
        page.append("<h2>").append(escape(title)).append("</h2>");
        page.append("<p style='color: ").append(hex(colours.text())).append("'>")
                .append(count(vocabulary.executors().size(), "executor")).append(", ")
                .append(count(vocabulary.getters().size(), "getter")).append(", ")
                .append(count(vocabulary.mappers().size(), "mapper")).append(" and ")
                .append(count(vocabulary.types().size(), "type"))
                .append(". Read only: the language's host writes this file.</p>");
        if (executors.isEmpty() && getters.isEmpty() && mappers.isEmpty() && types.isEmpty()) {
            page.append("<p>Nothing matches '").append(escape(filter.strip())).append("'.</p>");
        }

        if (!executors.isEmpty()) {
            page.append("<h3>Executors</h3><p>What a line does. Each starts a line, or follows a condition.</p>");
            table(page, List.of("Executor", "Takes"), executors.stream()
                    .map(word -> List.of(word(escape(word.name()), colours.executor()), takes(word.forms())))
                    .toList());
        }
        if (!getters.isEmpty()) {
            page.append("<h3>Getters</h3><p>Where an expression starts: a value to read.</p>");
            table(page, List.of("Getter", "Gives"), getters.stream()
                    .map(word -> List.of(word(escape(word.name()), colours.getter()), gives(word.forms())))
                    .toList());
        }
        if (!mappers.isEmpty()) {
            page.append("<h3>Mappers</h3><p>What can follow a value, by the value's type.</p>");
            Map<TypeKey, List<Vocabulary.Word>> byInput = new LinkedHashMap<>();
            for (Vocabulary.Word mapper : mappers) {
                byInput.computeIfAbsent(mapper.input(), key -> new ArrayList<>()).add(mapper);
            }
            for (Map.Entry<TypeKey, List<Vocabulary.Word>> group : byInput.entrySet()) {
                page.append("<h4>After ").append(typeLink(group.getKey())).append("</h4>");
                table(page, List.of("Mapper", "Takes", "Gives"), group.getValue().stream()
                        .map(word -> List.of(word(escape(word.name()), colours.mapper()), takes(word.forms()), gives(word.forms())))
                        .toList());
            }
        }
        if (!types.isEmpty()) {
            page.append("<h3>Types</h3>");
            List<List<String>> rows = new ArrayList<>();
            for (Vocabulary.Type type : types) {
                String name = name(type.key());
                rows.add(List.of("<a name='" + anchor(name) + "'></a><code>" + escape(name) + "</code>",
                        type.usableAs().isEmpty() ? "&mdash;"
                                : type.usableAs().stream().map(this::typeLink).collect(Collectors.joining(", ")),
                        String.valueOf(mappersAfter(type.key()))));
            }
            table(page, List.of("Type", "Usable as", "Mappers"), rows);
        }
        return page.append("</body></html>").toString();
    }

    private List<Vocabulary.Word> matching(List<Vocabulary.Word> words, String wanted) {
        if (wanted.isEmpty()) {
            return words;
        }
        return words.stream().filter(word -> word.name().toLowerCase(Locale.ROOT).contains(wanted)
                || word.input() != null && name(word.input()).toLowerCase(Locale.ROOT).contains(wanted)
                || word.forms().stream().anyMatch(form -> form.takes().stream().anyMatch(
                        type -> name(type).toLowerCase(Locale.ROOT).contains(wanted))
                        || form.gives() != null && name(form.gives()).toLowerCase(Locale.ROOT).contains(wanted)))
                .toList();
    }

    private long mappersAfter(TypeKey type) {
        return vocabulary.mappers().stream().filter(mapper -> type.equals(mapper.input())).count();
    }

    /**
     * Each argument a word may take: its hint, linked when it is just its type's name, and
     * otherwise followed by the types it may be given as.
     */
    private String takes(List<Vocabulary.Form> forms) {
        List<String> taken = new ArrayList<>();
        for (Vocabulary.Form form : forms) {
            if (form.argument() == null) {
                continue;
            }
            List<TypeKey> types = form.takes();
            if (types.size() == 1 && name(types.get(0)).equals(form.argument())) {
                taken.add("<code>&lt;" + typeLinkBare(types.get(0)) + "&gt;</code>");
            } else {
                String hint = word("&lt;" + escape(form.argument()) + "&gt;", colours.argument());
                taken.add(types.isEmpty() ? hint
                        : hint + " " + types.stream().map(this::typeLink).collect(Collectors.joining(" or ")));
            }
        }
        return taken.isEmpty() ? "nothing" : String.join("<br>", taken);
    }

    private String gives(List<Vocabulary.Form> forms) {
        String gives = forms.stream().map(Vocabulary.Form::gives).filter(type -> type != null).distinct()
                .map(this::typeLink).collect(Collectors.joining(" or "));
        return gives.isEmpty() ? "&mdash;" : gives;
    }

    private String typeLink(@Nullable TypeKey type) {
        return type == null ? "&mdash;" : "<code>" + typeLinkBare(type) + "</code>";
    }

    private String typeLinkBare(TypeKey type) {
        String name = name(type);
        return "<a href='#" + anchor(name) + "'><font color='" + hex(colours.type()) + "'>" + escape(name)
                + "</font></a>";
    }

    private String name(TypeKey type) {
        return typeName.apply(type);
    }

    private static String word(String html, Color colour) {
        return "<code><b><font color='" + hex(colour) + "'>" + html + "</font></b></code>";
    }

    private static void table(StringBuilder page, List<String> headings, List<List<String>> rows) {
        page.append("<table cellspacing='0' cellpadding='3'><tr>");
        for (String heading : headings) {
            page.append("<th align='left'>").append(heading).append("</th>");
        }
        page.append("</tr>");
        for (List<String> row : rows) {
            page.append("<tr valign='top'>");
            for (String cell : row) {
                page.append("<td>").append(cell).append("</td>");
            }
            page.append("</tr>");
        }
        page.append("</table>");
    }

    private static String count(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    private static String hex(Color colour) {
        return String.format("#%02x%02x%02x", colour.getRed(), colour.getGreen(), colour.getBlue());
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;")
                .replace("\"", "&quot;");
    }
}
