package uz.murodjon.robotcallv2.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the one Jackson 2 {@link ObjectMapper} the application's own code reads and
 * writes JSON with — stored columns, vendor payloads, webhook bodies.
 *
 * <p>Boot 4 auto-configures Jackson 3 ({@code tools.jackson}) for HTTP, not this; the code
 * is on Jackson 2 (see build.gradle.kts), so the mapper is declared here. Plain defaults on
 * purpose: every call site was a bare {@code new ObjectMapper()} before. A site that needs
 * different settings keeps its own configured instance instead of changing this shared one.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
