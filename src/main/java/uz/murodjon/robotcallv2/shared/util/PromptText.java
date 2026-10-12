package uz.murodjon.robotcallv2.shared.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Static LLM prompt text kept under {@code resources/prompts/} instead of in Java string
 * literals. Lines starting with {@code #} are notes for whoever edits the file and never
 * reach the model.
 */
public final class PromptText {

    private PromptText() {
    }

    /** The whole of {@code prompts/<name>} without its comment lines; blank lines kept, ends with a newline. */
    public static String text(String name) {
        return String.join("\n", read(name).lines()
                .filter(line -> !line.strip().startsWith("#"))
                .toList()).strip() + "\n";
    }

    /** The non-comment, non-blank lines of {@code prompts/<name>}, in file order — one list entry each. */
    public static List<String> lines(String name) {
        return read(name).lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }

    private static String read(String name) {
        String path = "prompts/" + name;
        try (InputStream in = PromptText.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("prompt resource missing from the classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("prompt resource unreadable: " + path, e);
        }
    }
}
