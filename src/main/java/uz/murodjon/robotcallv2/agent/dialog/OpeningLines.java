package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.shared.util.PersonNames;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The agent's greeting line, filled in for this call. The client's name goes in the way a live
 * operator says it — the first name ("Anvar aka"), never the full name from the CRM: a recorded
 * call opened with "Assalomu alaykum, Anvar Jo'rayev!".
 */
final class OpeningLines {

    private static final Pattern CLIENT_NAME = Pattern.compile("\\{\\{\\s*clientName\\b");

    private OpeningLines() {
    }

    /**
     * The line to say, or {@code null} when it names the client and this call has no usable
     * name: the model then writes the greeting itself and asks the name, instead of the
     * template's fallback ("hurmatli mijoz") that no person says.
     */
    static String render(String line, Map<String, Object> facts, String language) {
        if (line == null) {
            return null;
        }
        Map<String, Object> variables = facts == null ? Map.of() : facts;
        if (!CLIENT_NAME.matcher(line).find()) {
            return PromptTemplateEngine.render(line, variables);
        }
        Object name = variables.get("clientName");
        if (name == null || !PersonNames.isUsable(name.toString())) {
            return null;
        }
        boolean uzbek = language != null && language.toLowerCase(Locale.ROOT).startsWith("uz");
        Map<String, Object> spoken = new HashMap<>(variables);
        spoken.put("clientName", uzbek
                ? PersonNames.findUzbekAddress(name.toString())
                : PersonNames.findFirstName(name.toString()));
        return PromptTemplateEngine.render(line, spoken);
    }
}
