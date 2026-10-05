package uz.murodjon.robotcallv2.pack.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.pack.application.port.output.PackCatalogPort;
import uz.murodjon.robotcallv2.pack.domain.entity.VerticalPack;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/** The packs under {@code resources/packs/}, one JSON file each, in file-name order. */
@Component
public class ClasspathJsonPackCatalogAdapter implements PackCatalogPort {

    private static final String LOCATION = "classpath:packs/*.json";

    /** Strict on unknown fields on purpose: a misspelt key in a pack would otherwise be silently dropped. */
    private final ObjectMapper objectMapper;

    private final Map<String, VerticalPack> packs;

    public ClasspathJsonPackCatalogAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.packs = readPacks();
    }

    @Override
    public List<VerticalPack> findAll() {
        return List.copyOf(packs.values());
    }

    @Override
    public VerticalPack findByKey(String key) {
        return packs.get(key);
    }

    /**
     * Read once, at startup, and a broken file stops the application: the packs ship inside
     * the jar, so one that does not parse is a build defect — found at boot rather than by the
     * first company to press "install".
     */
    private Map<String, VerticalPack> readPacks() {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
        } catch (IOException e) {
            throw new IllegalStateException("cannot list " + LOCATION, e);
        }
        Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
        Map<String, VerticalPack> byKey = new LinkedHashMap<>();
        for (Resource resource : resources) {
            VerticalPack pack;
            try (InputStream in = resource.getInputStream()) {
                pack = objectMapper.readValue(in, VerticalPack.class);
            } catch (IOException e) {
                throw new IllegalStateException("pack " + resource.getFilename() + " cannot be read", e);
            }
            if (byKey.putIfAbsent(pack.key(), pack) != null) {
                throw new IllegalStateException("two packs share the key " + pack.key());
            }
        }
        return Collections.unmodifiableMap(byKey);
    }
}
