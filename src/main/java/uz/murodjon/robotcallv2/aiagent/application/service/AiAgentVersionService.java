package uz.murodjon.robotcallv2.aiagent.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentVersionRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.PublishAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentVersionUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentVersionRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentVersionStats;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentVersion;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentSnapshot;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Draft → published versions of an agent, and back (NEXT-PLAN A.1). History is append-only. */
@Service
public class AiAgentVersionService implements AiAgentVersionUseCase {

    private final AiAgentVersionRepository versionRepository;
    private final AiAgentDrafts drafts;
    private final UserUseCase userUseCase;
    private final AuditUseCase auditUseCase;
    private final CurrentUser currentUser;

    public AiAgentVersionService(AiAgentVersionRepository versionRepository, AiAgentDrafts drafts,
                                 UserUseCase userUseCase, AuditUseCase auditUseCase, CurrentUser currentUser) {
        this.versionRepository = versionRepository;
        this.drafts = drafts;
        this.userUseCase = userUseCase;
        this.auditUseCase = auditUseCase;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional
    public AiAgentVersionRow publishAgent(long companyId, long id, PublishAiAgentRequest request) {
        AiAgent draft = drafts.requireAgent(companyId, id);
        AiAgentVersion current = versionRepository.findPublished(companyId, id);
        if (current != null && AiAgentSnapshot.isSameConfiguration(draft, current.snapshot())) {
            throw new ConflictException(ErrorCode.AI_AGENT_NOTHING_TO_PUBLISH, id);
        }
        String note = request != null && request.note() != null ? request.note().trim() : null;
        AiAgentVersion stored = versionRepository.publish(
                AiAgentVersion.publishing(draft, note, currentUser.id().orElse(null)));
        auditUseCase.record(companyId, "AI_AGENT_PUBLISH", "ai_agent", String.valueOf(id), "v" + stored.version());
        return versionRow(companyId, stored);
    }

    @Override
    @Transactional
    public AiAgentVersionRow rollbackAgent(long companyId, long id, int version) {
        AiAgent draft = drafts.requireAgent(companyId, id);
        AiAgentVersion target = drafts.requireVersion(companyId, id, version);
        // The company may have withdrawn the human-like persona since this version went live.
        drafts.requirePersonaAllowed(companyId, target.snapshot().persona());
        // The old snapshot becomes the draft again and goes live as a new number: history
        // is append-only, so "version 3" never quietly means something else later.
        AiAgent restored = target.snapshot().withEnabled(draft.enabled()).withPublishedVersion(null)
                .withVersion(draft.version());
        drafts.update(companyId, id, restored);
        AiAgentVersion stored = versionRepository.publish(
                AiAgentVersion.publishing(restored, "rollback to v" + version, currentUser.id().orElse(null)));
        auditUseCase.record(companyId, "AI_AGENT_ROLLBACK", "ai_agent", String.valueOf(id),
                "v" + version + " -> v" + stored.version());
        return versionRow(companyId, stored);
    }

    @Override
    public List<AiAgentVersionRow> findVersions(long companyId, long id) {
        drafts.requireAgent(companyId, id);
        List<AiAgentVersion> versions = versionRepository.findByAgentId(companyId, id);
        Map<Long, String> names = userUseCase.findNamesByIds(companyId,
                versions.stream().map(AiAgentVersion::publishedBy).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Integer, AgentVersionStats> stats = versionRepository.findCallStats(companyId, id).stream()
                .collect(Collectors.toMap(AgentVersionStats::version, s -> s));
        return versions.stream()
                .map(v -> AiAgentVersionRow.of(v, v.publishedBy() != null ? names.get(v.publishedBy()) : null,
                        stats.getOrDefault(v.version(), AgentVersionStats.none(v.version()))))
                .toList();
    }

    private AiAgentVersionRow versionRow(long companyId, AiAgentVersion version) {
        String publisherName = version.publishedBy() != null
                ? userUseCase.findNamesByIds(companyId, List.of(version.publishedBy())).get(version.publishedBy())
                : null;
        return AiAgentVersionRow.of(version, publisherName);
    }
}
