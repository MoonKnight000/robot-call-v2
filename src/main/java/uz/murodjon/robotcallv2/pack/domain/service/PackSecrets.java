package uz.murodjon.robotcallv2.pack.domain.service;

import uz.murodjon.robotcallv2.pack.domain.entity.PackSecret;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The secrets a set of tools reads, read off the tools themselves
 * (UNIVERSAL-OPERATOR-PLAN E.1).
 *
 * <p>A generated pack has no hand-written secret list: whatever the importer put in the
 * addresses, the headers and the OAuth client is what the company will have to fill in, and
 * anything else would be a list that drifts from the tools it describes.
 *
 * <p>Spring-free and sorted, so the same tools always produce the same list.
 */
public final class PackSecrets {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*secrets\\.([A-Za-z0-9_]+)\\s*}}");

    private static final String FROM_PLACEHOLDER = "Tool so'rovida ishlatiladi — qiymatini kompaniya qo'yadi";
    private static final String FROM_AUTH = "OAuth client ma'lumoti — qiymatini kompaniya qo'yadi";

    private PackSecrets() {
    }

    public static List<PackSecret> of(List<Tool> tools) {
        Map<String, String> byKey = new TreeMap<>();
        for (Tool tool : tools) {
            addPlaceholders(byKey, tool.apiUrl());
            for (ToolKeyValuePair header : tool.apiHeaders()) {
                addPlaceholders(byKey, header.value());
            }
            if (tool.auth() != null) {
                addPlaceholders(byKey, tool.auth().tokenUrl());
                // Not placeholders: these fields name a secret key outright.
                addNamed(byKey, tool.auth().clientIdSecret());
                addNamed(byKey, tool.auth().clientSecretSecret());
            }
        }
        return byKey.entrySet().stream().map(entry -> new PackSecret(entry.getKey(), entry.getValue())).toList();
    }

    private static void addPlaceholders(Map<String, String> byKey, String text) {
        if (text == null) {
            return;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            byKey.putIfAbsent(matcher.group(1), FROM_PLACEHOLDER);
        }
    }

    private static void addNamed(Map<String, String> byKey, String key) {
        if (key != null && !key.isBlank()) {
            byKey.put(key.trim(), FROM_AUTH);
        }
    }
}
