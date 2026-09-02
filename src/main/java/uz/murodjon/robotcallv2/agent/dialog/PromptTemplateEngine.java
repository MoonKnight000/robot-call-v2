package uz.murodjon.robotcallv2.agent.dialog;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Universal dynamic template engine supporting {{variable_name}} and {{variable_name | fallback}}
 * placeholders in scenario prompts, greetings, and messages (matching Retell AI, Bland AI, Vapi standards).
 */
public final class PromptTemplateEngine {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "\\{\\{\\s*(?<var>[a-zA-Z0-9_\\-]+)(?:\\s*\\|\\s*(?:\"(?<fallbackQuoted>[^\"]*)\"|(?<fallbackPlain>[^}]+)))?\\s*\\}\\}"
    );

    private PromptTemplateEngine() {
    }

    /**
     * Replaces placeholders in the given template with values from the variables map.
     * Example: "Salom {{clientName | Hurmatli mijoz}}, sizda {{debtAmount}} so'm to'lov bor."
     */
    public static String render(String template, Map<String, Object> variables) {
        if (template == null || template.isBlank() || variables == null || variables.isEmpty()) {
            return template;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group("var");
            String fallbackQuoted = matcher.group("fallbackQuoted");
            String fallbackPlain = matcher.group("fallbackPlain");
            String fallback = fallbackQuoted != null ? fallbackQuoted : (fallbackPlain != null ? fallbackPlain.trim() : "");

            Object value = variables.get(varName);
            if (value == null) {
                // Try case-insensitive lookup
                for (Map.Entry<String, Object> entry : variables.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(varName)) {
                        value = entry.getValue();
                        break;
                    }
                }
            }

            String replacement;
            if (value != null && !value.toString().isBlank()) {
                replacement = value.toString().trim();
            } else {
                replacement = fallback;
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
