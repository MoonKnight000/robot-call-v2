package uz.murodjon.robotcallv2.tool.application.port.output;

import java.util.Map;

/** An OpenAPI document as a tree of maps and lists, from an address or from text (UNIVERSAL-OPERATOR-PLAN B.2). */
public interface OpenApiDocumentPort {

    /**
     * Fetches it from a public address; the address is only ever reached through the pinned
     * public client, and nothing the document refers to is fetched.
     *
     * @return the document, or null when the answer is neither JSON nor YAML
     */
    Map<String, Object> fetchDocument(String url);

    /** @return the document, or null when the text is neither JSON nor YAML */
    Map<String, Object> parseDocument(String text);
}
