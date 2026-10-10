package uz.murodjon.robotcallv2.knowledgebase.infrastructure.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.SitemapDiscoveryPort;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.http.PublicHttpClient;
import uz.murodjon.robotcallv2.shared.http.PublicHttpRequest;
import uz.murodjon.robotcallv2.shared.http.PublicHttpResponse;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Discovers and extracts webpage URLs declared in an XML sitemap or sitemap index.
 */
@Component
public class SitemapDiscoveryAdapter implements SitemapDiscoveryPort {

    private static final Logger log = LoggerFactory.getLogger(SitemapDiscoveryAdapter.class);

    private static final Duration SITEMAP_TIMEOUT = Duration.ofSeconds(25);
    private static final int MAX_SITEMAP_BYTES = 5 * 1024 * 1024;
    private static final int MAX_RECURSION_DEPTH = 1;

    private final PublicHttpClient httpClient = PublicHttpClient.create(Duration.ofSeconds(10), SITEMAP_TIMEOUT);

    @Override
    public List<URI> discoverUrls(URI sitemapUri, int maxUrls) {
        Set<URI> discovered = new LinkedHashSet<>();
        discoverRecursive(sitemapUri, discovered, maxUrls, 0);
        return new ArrayList<>(discovered);
    }

    private void discoverRecursive(URI uri, Set<URI> collected, int maxUrls, int depth) {
        if (collected.size() >= maxUrls || depth > MAX_RECURSION_DEPTH) {
            return;
        }

        String content;
        try {
            PublicHttpRequest request = PublicHttpRequest.create("GET", uri)
                    .setTimeout(SITEMAP_TIMEOUT)
                    .setMaxResponseBytes(MAX_SITEMAP_BYTES)
                    .addHeader("User-Agent", "Mozilla/5.0 (compatible; RobotCallKnowledgeBase/1.0)")
                    .addHeader("Accept", "text/xml,application/xml,text/plain");

            PublicHttpResponse response = httpClient.send(request);
            if (response.statusCode() / 100 != 2) {
                log.warn("Sitemap fetch failed for {} with status {}", uri, response.statusCode());
                return;
            }
            content = response.readBodyText();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.KNOWLEDGE_SOURCE_FETCH_INTERRUPTED, "sitemap", e, uri.toString());
        } catch (Exception e) {
            log.warn("Failed to fetch sitemap {}: {}", uri, e.getMessage());
            return;
        }

        List<String> locs = parseLocs(content, uri.toString());
        List<URI> subSitemaps = new ArrayList<>();

        for (String loc : locs) {
            if (collected.size() >= maxUrls) {
                break;
            }
            URI validUri = PublicUrlGuard.parsePublic(loc);
            if (validUri != null) {
                String path = validUri.getPath();
                if (path != null && (path.endsWith(".xml") || path.endsWith(".xml.gz"))) {
                    subSitemaps.add(validUri);
                } else {
                    collected.add(validUri);
                }
            }
        }

        for (URI subSitemap : subSitemaps) {
            if (collected.size() >= maxUrls) {
                break;
            }
            discoverRecursive(subSitemap, collected, maxUrls, depth + 1);
        }
    }

    List<String> parseLocs(String xml, String sourceUri) {
        List<String> locs = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            return locs;
        }

        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

        try {
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
            boolean insideLoc = false;
            StringBuilder currentText = new StringBuilder();

            while (reader.hasNext()) {
                int event = reader.next();
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        if ("loc".equalsIgnoreCase(reader.getLocalName())) {
                            insideLoc = true;
                            currentText.setLength(0);
                        }
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                        if (insideLoc) {
                            currentText.append(reader.getText());
                        }
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        if ("loc".equalsIgnoreCase(reader.getLocalName()) && insideLoc) {
                            insideLoc = false;
                            String text = currentText.toString().trim();
                            if (!text.isEmpty()) {
                                locs.add(text);
                            }
                        }
                    }
                    default -> {
                        // Ignore comments, processing instructions, etc.
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing sitemap XML from {}: {}", sourceUri, e.getMessage());
        }
        return locs;
    }
}
