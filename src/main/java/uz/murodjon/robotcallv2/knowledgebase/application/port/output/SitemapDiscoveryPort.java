package uz.murodjon.robotcallv2.knowledgebase.application.port.output;

import java.net.URI;
import java.util.List;

/**
 * Discovers and extracts webpage URLs declared in an XML sitemap or sitemap index.
 */
public interface SitemapDiscoveryPort {

    /**
     * Fetches and parses the sitemap at {@code sitemapUri}, returning discovered page URLs
     * up to {@code maxUrls}.
     */
    List<URI> discoverUrls(URI sitemapUri, int maxUrls);
}
