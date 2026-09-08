package uz.murodjon.robotcallv2.widget.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AiAgentJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.widget.application.port.output.AgentWidgetRepository;
import uz.murodjon.robotcallv2.widget.domain.entity.AgentWidget;
import uz.murodjon.robotcallv2.widget.domain.entity.WidgetTheme;
import uz.murodjon.robotcallv2.widget.infrastructure.persistence.entity.AgentWidgetEntity;
import uz.murodjon.robotcallv2.widget.infrastructure.persistence.repository.AgentWidgetJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class AgentWidgetRepositoryAdapter implements AgentWidgetRepository {

    private static final Logger log = LoggerFactory.getLogger(AgentWidgetRepositoryAdapter.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> ORIGINS_TYPE = new TypeReference<>() {};
    private static final TypeReference<WidgetTheme> THEME_TYPE = new TypeReference<>() {};

    private final AgentWidgetJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final AiAgentJpaRepository aiAgentJpaRepository;

    public AgentWidgetRepositoryAdapter(AgentWidgetJpaRepository jpaRepository,
                                        CompanyJpaRepository companyJpaRepository,
                                        AiAgentJpaRepository aiAgentJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.aiAgentJpaRepository = aiAgentJpaRepository;
    }

    @Override
    @Transactional
    public AgentWidget save(AgentWidget widget) {
        AgentWidgetEntity entity;
        if (widget.id() > 0) {
            entity = jpaRepository.findByCompanyIdAndId(widget.companyId(), widget.id())
                    .orElseGet(AgentWidgetEntity::new);
        } else {
            entity = new AgentWidgetEntity();
            entity.setCreatedAt(widget.createdAt() != null ? widget.createdAt() : Instant.now());
        }

        entity.setWidgetKey(widget.widgetKey());
        entity.setCompany(companyJpaRepository.getReferenceById(widget.companyId()));
        entity.setAgent(aiAgentJpaRepository.getReferenceById(widget.agentId()));
        entity.setName(widget.name());
        entity.setEnabled(widget.enabled());
        entity.setAllowedOrigins(writeJson(widget.allowedOriginsOrEmpty()));
        entity.setTheme(writeJson(widget.themeOrDefault()));
        entity.setConsentRequired(widget.consentRequired());
        entity.setConsentText(widget.consentText());
        entity.setUpdatedAt(Instant.now());

        AgentWidgetEntity saved = jpaRepository.save(entity);
        return toAgentWidget(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentWidget> findByCompanyIdAndId(long companyId, long id) {
        return jpaRepository.findByCompanyIdAndId(companyId, id).map(this::toAgentWidget);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentWidget> findByWidgetKey(String widgetKey) {
        return jpaRepository.findByWidgetKey(widgetKey).map(this::toAgentWidget);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentWidget> findByCompanyIdAndAgentId(long companyId, long agentId) {
        return jpaRepository.findByCompanyIdAndAgentIdOrderByCreatedAtDesc(companyId, agentId)
                .stream()
                .map(this::toAgentWidget)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByCompanyIdAndId(long companyId, long id) {
        jpaRepository.deleteByCompanyIdAndId(companyId, id);
    }

    private AgentWidget toAgentWidget(AgentWidgetEntity entity) {
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
