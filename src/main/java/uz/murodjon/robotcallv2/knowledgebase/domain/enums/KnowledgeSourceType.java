package uz.murodjon.robotcallv2.knowledgebase.domain.enums;

/**
 * Supported knowledge source document types.
 */
public enum KnowledgeSourceType {
    PDF,
    TXT,
    DOCX,
    CSV,
    XLSX,
    XLS,
    URL,
    /**
     * An XML sitemap (or sitemap index) URL. Discovers and indexes all linked web pages.
     */
    SITEMAP,
    /**
     * A product catalog — a CSV/XLSX/JSON file, or a URL answering one — read row by row
     * into catalog items the agent searches with {@code searchCatalog}
     * (UNIVERSAL-OPERATOR-PLAN B.4), not split into passages of prose.
     */
    STRUCTURED_FEED
}
