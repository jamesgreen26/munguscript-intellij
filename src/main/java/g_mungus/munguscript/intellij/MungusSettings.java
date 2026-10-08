package g_mungus.munguscript.intellij;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Which language file each script is read by, kept with the project in {@code .idea/munguscript.xml}. */
@Service(Service.Level.PROJECT)
@State(name = "MungusScriptSettings", storages = @Storage("munguscript.xml"))
public final class MungusSettings implements PersistentStateComponent<MungusSettings.State> {
    private State state = new State();

    public static MungusSettings getInstance(Project project) {
        return project.getService(MungusSettings.class);
    }

    /** A {@link LanguageLookup.Mapping} as settings store it. */
    public static final class Mapping {
        public String scripts = "";
        public String language = "";

        public Mapping() {
        }

        public Mapping(String scripts, String language) {
            this.scripts = scripts;
            this.language = language;
        }

        Mapping copy() {
            return new Mapping(scripts, language);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Mapping mapping && scripts.equals(mapping.scripts) && language.equals(mapping.language);
        }

        @Override
        public int hashCode() {
            return 31 * scripts.hashCode() + language.hashCode();
        }
    }

    public static final class State {
        public List<Mapping> mappings = new ArrayList<>();
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public List<Mapping> mappings() {
        return state.mappings.stream().map(Mapping::copy).toList();
    }

    public void setMappings(List<Mapping> mappings) {
        state.mappings = new ArrayList<>(mappings.stream().map(Mapping::copy).toList());
    }

    List<LanguageLookup.Mapping> lookup() {
        return state.mappings.stream()
                .filter(mapping -> !mapping.scripts.isBlank() && !mapping.language.isBlank())
                .map(mapping -> new LanguageLookup.Mapping(mapping.scripts.strip(), mapping.language.strip()))
                .toList();
    }
}
