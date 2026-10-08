# MungusScript for IntelliJ

IntelliJ support for `.mungus` scripts: highlighting, errors and completion. The plugin knows no
language of its own. Each script is read by the MungusScript engine, in the language its host
wrote to a language file (`.mungustree`), so the editor sees the script as the host will run it.

## Writing scripts

The editor follows the ZeroPointSystems script terminal:

- Words are coloured as they are typed, by the engine, line by line. Once the script has been read
  in full, it is coloured again, which catches lines whose meaning an edit changed, such as those
  using an alias just defined.
- Completion comes up after a space, `(`, `#` or `=`, as well as on letters, so what can come next
  shows before a word is started. Each word is coloured as in a script and says what it takes or
  gives.
- A hint under the line shows what the argument being written wants, such as `<int>` or
  `<from to destination>`, or, at the end of a line that cannot go on, why. Parameter Info (⌘P /
  Ctrl+P) shows it anywhere.
- The line being written is not underlined for what it still lacks; leaving it shows its errors.
- On `#def` lines, `#def `, `= ` and `<alias>` are offered where they go.

## Language files

A host writes its language with `ScriptRunner.writeLanguage`, or `ScriptLanguageFile` if it has
an engine of its own. For build scripts, `mvn munguscript:language` writes
`.mungus/build.mungustree`.

Which file a script is read by:

1. **Settings | Languages & Frameworks | MungusScript**, if it lists any: globs over script paths in
   the project (such as `**.mungus` for all of them, or `tools/**.mungus`), each with a language
   file. The first glob that matches is used. They are kept in `.idea/munguscript.xml`.
2. Otherwise a `*.mungustree` in `.mungus/` or `run/.mungus/` of the project (where a game run in
   development can write its language): the file named after the script (`deploy.mungus` reads by
   `deploy.mungustree`), looked for in that order, or the only one in either.

A script with no language says why, at the top of the editor. When a language file is written
again, open scripts are read again.

Opening a `.mungustree` file shows the words it holds: what each executor takes, what each getter
gives, the mappers that can follow each type, and the types themselves, with a search box and
links between types. It is read only, and shown again when the file is written again.

A language file holds the host's argument types only by their shape. One that does not describe
its shape (a `PortableArgument`) is read loosely, as a word or a quoted string, so the editor may
accept what the host would not.

## How it fits together

- `MungusLexer` only tells lines apart: comments, `#def` definitions and commands. What each word
  is depends on the language, which a grammar could not know. For colouring,
  `MungusHighlightingLexer` splits each line into words by what the engine says they are.
- `ScriptAnalysis` reads a whole script as `ScriptRunner` does, without running anything, through
  the `ScriptView` read from a language file: `highlight` and `highlightDefinition` for colours,
  `check` for errors, and `suggest` and `suggestExpression` for completion.
- `LanguageLookup` finds a script's language file, and `LanguageCache` reads each once, and again
  when it changes.
- `VocabularyPage` lays out a language's words (`ScriptView.vocabulary`) as a page, which
  `LanguageFileEditor` shows for `.mungustree` files.
- Those four do not depend on the IDE, and are tested on their own. `MungusAnnotator`,
  `MungusCompletionContributor`, `MungusLanguages` and the settings are the IDE's side.
- Colours can be changed in Settings | Editor | Color Scheme | MungusScript.

## Building

munguscript must be in `~/.m2` first: `./gradlew publishToMavenLocal` in the munguscript repo.

```
./gradlew test          # the analysis and lookup tests
./gradlew runIde        # an IntelliJ IDEA with the plugin installed, to try it
./gradlew buildPlugin   # build/distributions/*.zip, installable from disk
./gradlew verifyPlugin  # checks the plugin against the IDE's API
```

The plugin is built against the IntelliJ IDEA at `intellijLocalPath` in `gradle.properties`,
2025.3 or later.
