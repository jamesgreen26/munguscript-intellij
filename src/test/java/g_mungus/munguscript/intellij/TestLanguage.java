package g_mungus.munguscript.intellij;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import g_mungus.munguscript.engine.codec.ArgumentShape;
import g_mungus.munguscript.engine.codec.PortableArgument;
import g_mungus.munguscript.language.ScriptRegistrar;
import g_mungus.munguscript.language.node.ScriptNodes;
import g_mungus.munguscript.language.type.ScriptType;
import g_mungus.munguscript.language.type.TypeKey;
import g_mungus.munguscript.runner.ScriptRunner;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static g_mungus.munguscript.language.builtin.BuiltInTypes.BOOLEAN;
import static g_mungus.munguscript.language.builtin.BuiltInTypes.STRING;

/**
 * A small language, written to a language file as its host would, for the plugin to read scripts
 * by. The plugin has no language of its own.
 */
final class TestLanguage {
    private TestLanguage() {
    }

    record Pair(String from, String to) {
    }

    /** {@code "a" to "b"}, which says how it reads. */
    static final class PairArgument implements ArgumentType<Pair>, PortableArgument {
        private static final SimpleCommandExceptionType EXPECTED_TO =
                new SimpleCommandExceptionType(new LiteralMessage("Expected 'to'"));

        @Override
        public Pair parse(StringReader reader) throws CommandSyntaxException {
            String from = reader.readString();
            reader.expect(' ');
            int at = reader.getCursor();
            if (!reader.readUnquotedString().equals("to")) {
                reader.setCursor(at);
                throw EXPECTED_TO.createWithContext(reader);
            }
            reader.expect(' ');
            return new Pair(from, reader.readString());
        }

        @Override
        public ArgumentShape shape() {
            ArgumentShape path = new ArgumentShape.Text(ArgumentShape.Text.Kind.QUOTABLE);
            return new ArgumentShape.Sequence(List.of(path, new ArgumentShape.Word("to"), path));
        }
    }

    static final ScriptType<Pair> PAIR = ScriptType.writable(new TypeKey("test", "pair"), Pair.class)
            .hint("from to destination")
            .argument(new PairArgument())
            .print(pair -> "\"" + pair.from() + "\" to \"" + pair.to() + "\"")
            .parse(text -> {
                throw new UnsupportedOperationException();
            })
            .build();

    static void register(ScriptRegistrar registrar) {
        registrar.registerType(PAIR);
        registrar.register(ScriptNodes.getter("project_version", STRING, context -> "1.0"));
        registrar.register(ScriptNodes.getter("classes_dir", STRING, context -> "target/classes"));
        registrar.register(ScriptNodes.argumentMapper("ends_with", STRING, BOOLEAN, STRING,
                (text, context) -> text.endsWith(context.argumentValue(String.class))));
        registrar.register(ScriptNodes.argumentMapper("to", STRING, PAIR, "destination", STRING,
                (text, context) -> new Pair(text, context.argumentValue(String.class))));
        registrar.register(ScriptNodes.executor("log", STRING, (text, context) -> 1));
        registrar.register(ScriptNodes.executor("warn", STRING, (text, context) -> 1));
        registrar.register(ScriptNodes.executor("write_file", PAIR, (pair, context) -> 1));
        registrar.register(ScriptNodes.executor("copy", PAIR, (pair, context) -> 1));
    }

    /** Writes the language to {@code file}, as its host would. */
    static Path write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            ScriptRunner.builder("test").register(TestLanguage::register).build().writeLanguage(out);
        }
        return file;
    }
}
