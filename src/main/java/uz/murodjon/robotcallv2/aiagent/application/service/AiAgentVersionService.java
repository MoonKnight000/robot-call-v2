package uz.murodjon.robotcallv2.aiagent.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentRolloutRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentVersionRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.PublishAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.StartAgentRolloutRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.UpdateAgentRolloutRequest;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentVersionUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AgentEvalRepository;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AgentRolloutRepository;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AgentTestCaseRepository;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentVersionRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentRollout;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentVersionChange;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentVersionStats;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentVersion;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalRunStatus;
import uz.murodjon.robotcallv2.aiagent.domain.service.AgentRolloutValidator;
import uz.murodjon.robotcallv2.aiagent.domain.service.AgentVersionDiffer;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentSnapshot;
import uz.murodjon.robotcallv2.aiagent.infrastructure.config.AgentEvalProperties;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Draft → published versions of an agent, and back (NEXT-PLAN A.1). History is append-only. */
@Service
public class AiAgentVersionService implements AiAgentVersionUseCase {

    /** How far back publishing looks for a passing evaluation of the draft. */
    private static final int RUNS_CHECKED = 20;

    private final AiAgentVersionRepository versionRepository;
    private final AiAgentDrafts drafts;
    private final UserUseCase userUseCase;
    private final AuditUseCase auditUseCase;
    private final CurrentUser currentUser;
    private final AgentTestCaseRepository testCaseRepository;
    private final AgentEvalRepository evalRepository;
    private final AiAgentUseCase aiAgentUseCase;
    private final AgentEvalProperties agentEvalProperties;
    private final AgentRolloutRepository rolloutRepository;

    public AiAgentVersionService(AiAgentVersionRepository versionRepository, AiAgentDrafts drafts,
                                 UserUseCase userUseCase, AuditUseCase auditUseCase, CurrentUser currentUser,
                                 AgentTestCaseRepository testCaseRepository, AgentEvalRepository evalRepository,
                                 AiAgentUseCase aiAgentUseCase, AgentEvalProperties agentEvalProperties,
                                 AgentRolloutRepository rolloutRepository) {
        this.rolloutRepository = rolloutRepository;
        this.versionRepository = versionRepository;
        this.drafts = drafts;
        this.userUseCase = userUseCase;
        this.auditUseCase = auditUseCase;
        this.currentUser = currentUser;
        this.testCaseRepository = testCaseRepository;
        this.evalRepository = evalRepository;
        this.aiAgentUseCase = aiAgentUseCase;
        this.agentEvalProperties = agentEvalProperties;
    }

    @Override
    @Transactional
    public AiAgentVersionRow publishAgent(long companyId, long id, PublishAiAgentRequest request) {
        AiAgent draft = drafts.requireAgent(companyId, id);
        requireNoRollout(companyId, id);
        AiAgentVersion current = versionRepository.findPublished(companyId, id);
        if (current != null && AiAgentSnapshot.isSameConfiguration(draft, current.snapshot())) {
            throw new ConflictException(ErrorCode.AI_AGENT_NOTHING_TO_PUBLISH, id);
        }
        requirePassingEvaluation(companyId, id, draft);
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
        requireNoRollout(companyId, id);
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

    /**
     * With the publish gate on, the draft goes live only if an evaluation of exactly this
     * configuration passed every case. Matched on the fingerprint, not on time: an edit
     * after the run changes the fingerprint, and so does a change to a shared scenario the
     * agent speaks. An agent with no test cases has nothing to be gated on.
     */
    @Override
    @Transactional
    public AgentRolloutRow startRollout(long companyId, long id, StartAgentRolloutRequest request) {
        AiAgent draft = drafts.requireAgent(companyId, id);
        requireNoRollout(companyId, id);
        List<String> stagingPhones = normalize(request.stagingPhones());
        AgentRolloutValidator.validateSplit(request.trafficPercent(), stagingPhones);
        AiAgentVersion live = versionRepository.findPublished(companyId, id);
        if (live == null) {
            throw new ConflictException(ErrorCode.AGENT_ROLLOUT_NOT_PUBLISHED, id);
        }
        if (AiAgentSnapshot.isSameConfiguration(draft, live.snapshot())) {
            throw new ConflictException(ErrorCode.AI_AGENT_NOTHING_TO_PUBLISH, id);
        }
        requirePassingEvaluation(companyId, id, draft);
        String note = request.note() != null ? request.note().trim() : null;
        Long userId = currentUser.id().orElse(null);
        AiAgentVersion candidate = versionRepository.createCandidate(AiAgentVersion.publishing(draft, note, userId));
        AgentRollout rollout = rolloutRepository.create(new AgentRollout(id, companyId, candidate.version(),
                request.trafficPercent(), stagingPhones, null, userId));
        auditUseCase.record(companyId, "AI_AGENT_ROLLOUT_START", "ai_agent", String.valueOf(id),
                "v" + candidate.version() + " at " + rollout.trafficPercent() + "%");
        return rolloutRow(companyId, rollout, live.version());
    }

    @Override
    public AgentRolloutRow updateRollout(long companyId, long id, UpdateAgentRolloutRequest request) {
        AgentRollout rollout = requireRollout(companyId, id);
        List<String> stagingPhones = normalize(request.stagingPhones());
        AgentRolloutValidator.validateSplit(request.trafficPercent(), stagingPhones);
        AgentRollout updated = rolloutRepository.update(rollout.withSplit(request.trafficPercent(), stagingPhones));
        if (updated == null) {
            throw new NotFoundException(ErrorCode.AGENT_ROLLOUT_NOT_FOUND, id);
        }
        auditUseCase.record(companyId, "AI_AGENT_ROLLOUT_UPDATE", "ai_agent", String.valueOf(id),
                updated.trafficPercent() + "%");
        return rolloutRow(companyId, updated, requireLiveVersion(companyId, id));
    }

    @Override
    public AgentRolloutRow findRollout(long companyId, long id) {
        return rolloutRow(companyId, requireRollout(companyId, id), requireLiveVersion(companyId, id));
    }

    @Override
    @Transactional
    public AiAgentVersionRow promoteRollout(long companyId, long id) {
        AgentRollout rollout = requireRollout(companyId, id);
        AiAgentVersion candidate = drafts.requireVersion(companyId, id, rollout.candidateVersion());
        // The company may have withdrawn the human-like persona since the rollout started.
        drafts.requirePersonaAllowed(companyId, candidate.snapshot().persona());
        versionRepository.promoteCandidate(companyId, id);
        rolloutRepository.deleteByAgentId(companyId, id);
        auditUseCase.record(companyId, "AI_AGENT_ROLLOUT_PROMOTE", "ai_agent", String.valueOf(id),
                "v" + candidate.version());
        return versionRow(companyId, drafts.requireVersion(companyId, id, candidate.version()));
    }

    @Override
    @Transactional
    public void cancelRollout(long companyId, long id) {
        AgentRollout rollout = requireRollout(companyId, id);
        versionRepository.archiveCandidate(companyId, id);
        rolloutRepository.deleteByAgentId(companyId, id);
        auditUseCase.record(companyId, "AI_AGENT_ROLLOUT_CANCEL", "ai_agent", String.valueOf(id),
                "v" + rollout.candidateVersion());
    }

    @Override
    public List<AgentVersionChange> diffVersions(long companyId, long id, int from, Integer to) {
        AiAgent draft = drafts.requireAgent(companyId, id);
        AiAgent before = drafts.requireVersion(companyId, id, from).snapshot();
        AiAgent after = to == null ? draft : drafts.requireVersion(companyId, id, to).snapshot();
        return AgentVersionDiffer.diff(before, after);
    }

    /** A publish or a rollback during a rollout would leave the candidate racing a version it never met. */
    private void requireNoRollout(long companyId, long agentId) {
        if (rolloutRepository.findByAgentId(companyId, agentId) != null) {
            throw new ConflictException(ErrorCode.AGENT_ROLLOUT_ACTIVE, agentId);
        }
    }

    private AgentRollout requireRollout(long companyId, long agentId) {
        drafts.requireAgent(companyId, agentId);
        AgentRollout rollout = rolloutRepository.findByAgentId(companyId, agentId);
        if (rollout == null) {
            throw new NotFoundException(ErrorCode.AGENT_ROLLOUT_NOT_FOUND, agentId);
        }
        return rollout;
    }

    /** A rollout is only ever started against a live version, and nothing unpublishes one. */
    private int requireLiveVersion(long companyId, long agentId) {
        AiAgentVersion live = versionRepository.findPublished(companyId, agentId);
        if (live == null) {
            throw new ConflictException(ErrorCode.AGENT_ROLLOUT_NOT_PUBLISHED, agentId);
        }
        return live.version();
    }

    private AgentRolloutRow rolloutRow(long companyId, AgentRollout rollout, int liveVersion) {
        Map<Integer, AgentVersionStats> stats = versionRepository
                .findCallStatsSince(companyId, rollout.agentId(), rollout.startedAt()).stream()
                .collect(Collectors.toMap(AgentVersionStats::version, s -> s));
        return AgentRolloutRow.of(rollout, liveVersion,
                stats.getOrDefault(liveVersion, AgentVersionStats.none(liveVersion)),
                stats.getOrDefault(rollout.candidateVersion(), AgentVersionStats.none(rollout.candidateVersion())));
    }

    /** Staging numbers as calls carry them; an unreadable one stays null for the validator to name. */
    private static List<String> normalize(List<String> phones) {
        List<String> normalized = new ArrayList<>();
        if (phones != null) {
            for (String phone : phones) {
                String value = PhoneNumbers.normalize(phone);
                if (!normalized.contains(value)) {
                    normalized.add(value);
                }
            }
        }
        return normalized;
    }

    private void requirePassingEvaluation(long companyId, long agentId, AiAgent draft) {
        AgentEvalMode gate = agentEvalProperties.publishGate();
        if (gate == null || testCaseRepository.findByAgent(companyId, agentId).isEmpty()) {
            return;
        }
        Scenario scenario = aiAgentUseCase.resolveScenario(companyId, draft);
        String fingerprint = AiAgentSnapshot.fingerprint(draft, scenario != null ? scenario.definition() : null);
        boolean passed = evalRepository.findRuns(companyId, agentId, RUNS_CHECKED).stream()
                .filter(run -> run.agentVersion() == null && fingerprint.equals(run.configHash()))
                .filter(run -> gate == AgentEvalMode.TEXT || run.mode() == AgentEvalMode.VOICE)
                .anyMatch(run -> run.status() == AgentEvalRunStatus.DONE && run.passed() == run.total());
        if (!passed) {
            throw new ConflictException(ErrorCode.AI_AGENT_EVAL_REQUIRED, agentId, gate);
        }
    }

    private AiAgentVersionRow versionRow(long companyId, AiAgentVersion version) {
        String publisherName = version.publishedBy() != null
                ? userUseCase.findNamesByIds(companyId, List.of(version.publishedBy())).get(version.publishedBy())
                : null;
        return AiAgentVersionRow.of(version, publisherName);
    }
}
