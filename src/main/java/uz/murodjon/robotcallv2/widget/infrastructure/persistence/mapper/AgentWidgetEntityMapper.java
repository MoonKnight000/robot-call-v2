package uz.murodjon.robotcallv2.widget.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.widget.domain.entity.AgentWidget;
import uz.murodjon.robotcallv2.widget.domain.entity.WidgetTheme;
import uz.murodjon.robotcallv2.widget.infrastructure.persistence.entity.AgentWidgetEntity;

import java.util.List;

/**
 * Maps between {@link AgentWidgetEntity} and {@link AgentWidget}, including the two JSON
 * columns (allowed origins, theme). A column that does not parse reads as its default
 * rather than failing: a broken theme must not take the widget down with it.
 */
@Component
public class AgentWidgetEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(AgentWidgetEntityMapper.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> ORIGINS_TYPE = new TypeReference<>() {};
    private static final TypeReference<WidgetTheme> THEME_TYPE = new TypeReference<>() {};

    public AgentWidget toAgentWidget(AgentWidgetEntity entity) {
        return new AgentWidget(
                entity.getId(),
                entity.getWidgetKey(),
                entity.getCompanyId(),
                entity.getAgentId(),
                entity.getName(),
                entity.isEnabled(),
                readJson(entity.getAllowedOrigins(), ORIGINS_TYPE, List.of()),
                readJson(entity.getTheme(), THEME_TYPE, WidgetTheme.defaultTheme()),
                entity.isConsentRequired(),
                entity.getConsentText(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /** The widget's own fields; the company and agent references and the timestamps are the adapter's. */
    public void applyToEntity(AgentWidget widget, AgentWidgetEntity entity) {
        entity.setWidgetKey(widget.widgetKey());
        entity.setName(widget.name());
        entity.setEnabled(widget.enabled());
        entity.setAllowedOrigins(writeJson(widget.allowedOriginsOrEmpty()));
        entity.setTheme(writeJson(widget.themeOrDefault()));
        entity.setConsentRequired(widget.consentRequired());
        entity.setConsentText(widget.consentText());
    }

    private static String writeJson(Object obj) {
        try {
            return JSON.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static <T> T readJson(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return JSON.readValue(json, type);
        } catch (Exception e) {
            log.warn("Failed to parse widget JSON, using fallback: {}", e.getMessage());
            return fallback;
        }
    }
}
