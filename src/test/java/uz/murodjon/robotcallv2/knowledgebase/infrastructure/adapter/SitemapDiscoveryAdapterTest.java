package uz.murodjon.robotcallv2.knowledgebase.infrastructure.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SitemapDiscoveryAdapterTest {

    private SitemapDiscoveryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SitemapDiscoveryAdapter();
    }

    @Test
    void parseLocsExtractsUrlsFromStandardUrlsetXml() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                   <url>
                      <loc>https://example.com/</loc>
                      <lastmod>2026-10-01</lastmod>
                      <changefreq>daily</changefreq>
                      <priority>1.0</priority>
                   </url>
                   <url>
                      <loc>https://example.com/about</loc>
                      <lastmod>2026-10-02</lastmod>
                   </url>
                   <url>
                      <loc>https://example.com/pricing</loc>
                   </url>
                </urlset>
                """;

        List<String> locs = adapter.parseLocs(xml, "https://example.com/sitemap.xml");

        assertThat(locs).containsExactly(
                "https://example.com/",
                "https://example.com/about",
                "https://example.com/pricing"
        );
    }

    @Test
    void parseLocsExtractsSitemapsFromSitemapIndexXml() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                   <sitemap>
                      <loc>https://example.com/sitemap-pages.xml</loc>
                      <lastmod>2026-10-01T18:23:17+00:00</lastmod>
                   </sitemap>
                   <sitemap>
                      <loc>https://example.com/sitemap-posts.xml</loc>
                   </sitemap>
                </sitemapindex>
                """;

        List<String> locs = adapter.parseLocs(xml, "https://example.com/sitemap_index.xml");

        assertThat(locs).containsExactly(
                "https://example.com/sitemap-pages.xml",
                "https://example.com/sitemap-posts.xml"
        );
    }

    @Test
    void parseLocsHandlesEmptyOrMalformedXmlGracefully() {
        assertThat(adapter.parseLocs("", "https://example.com/empty.xml")).isEmpty();
        assertThat(adapter.parseLocs("not-xml", "https://example.com/invalid.xml")).isEmpty();
        assertThat(adapter.parseLocs("<urlset><url><loc></loc></url></urlset>", "https://example.com/blank.xml")).isEmpty();
    }
}
