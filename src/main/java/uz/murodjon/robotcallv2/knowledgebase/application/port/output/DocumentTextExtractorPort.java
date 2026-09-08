package uz.murodjon.robotcallv2.knowledgebase.application.port.output;

import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceType;

import java.io.InputStream;

/** Turns an uploaded document or a fetched web page into the plain text that gets indexed. */
public interface DocumentTextExtractorPort {

    /**
     * @param content the document's bytes; closed by the implementation
     * @return the document's text, or an empty string when it carries none (a scanned PDF
     *         with no text layer, an empty spreadsheet)
     */
    String extract(InputStream content, KnowledgeSourceType sourceType);

    /** Fetches {@code url} and returns its readable text, with markup and scripts stripped. */
    String extractFromUrl(String url);
}
