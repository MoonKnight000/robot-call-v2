package uz.murodjon.robotcallv2.memory.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.memory.application.dto.UpdateClientMemoryRequest;
import uz.murodjon.robotcallv2.memory.application.dto.UpdateRememberedCallRequest;
import uz.murodjon.robotcallv2.memory.application.dto.UpsertClientMemoryFactRequest;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.memory.application.port.output.ClientMemoryRepository;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.MemorySource;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.memory.domain.enums.MemorySourceKind;
import uz.murodjon.robotcallv2.memory.infrastructure.config.ClientMemoryProperties;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Cross-call memory per client (company + phone). The dialog reads it into the system
 * prompt; the post-call pipeline writes every summarized conversation back, the agent's
 * tools add facts mid-call, and an operator can correct or remove any of it.
 */
@Service
public class ClientMemoryService implements ClientMemoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(ClientMemoryService.class);

    /** A fact name is pasted into the prompt as a label, so it stays an identifier. */
    private static final Pattern FACT_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    private final ClientMemoryRepository repository;
    private final AuditUseCase audit;
    private final ClientMemoryProperties clientMemoryProperties;

    public ClientMemoryService(ClientMemoryRepository repository, AuditUseCase audit,
                               ClientMemoryProperties clientMemoryProperties) {
        this.repository = repository;
        this.audit = audit;
        this.clientMemoryProperties = clientMemoryProperties;
    }

    @Override
    public ClientMemory requireByCompanyIdAndPhone(long companyId, String phone) {
        ClientMemory memory = findByCompanyIdAndPhone(companyId, PhoneNumbers.require(phone));
        if (memory == null) {
            throw new NotFoundException(ErrorCode.CLIENT_MEMORY_NOT_FOUND, phone);
        }
        return memory;
    }

    @Override
    @Transactional
    public ClientMemory updateByCompanyIdAndPhone(long companyId, String phone, UpdateClientMemoryRequest request) {
        String normalized = PhoneNumbers.require(phone);
        ClientMemory current = lock(companyId, normalized);
        ClientMemory saved = repository.upsert(companyId, current.toBuilder()
                .preferredName(apply(current.preferredName(), request.preferredName()))
                .preferredLanguage(apply(current.preferredLanguage(), request.preferredLanguage()))
                .operatorNotes(apply(current.operatorNotes(), request.operatorNotes()))
                .updatedAt(Instant.now())
                .build());
        audit.record(companyId, "CLIENT_MEMORY_UPDATE", "client_memory", normalized, "Memory updated by operator");
        return saved;
    }

    @Override
    @Transactional
    public ClientMemory upsertFactByCompanyIdAndPhone(long companyId, String phone, String key,
                                                      UpsertClientMemoryFactRequest request) {
        String normalized = PhoneNumbers.require(phone);
        if (key == null || !FACT_KEY.matcher(key).matches()) {
            throw new ValidationException(ErrorCode.CLIENT_MEMORY_FACT_KEY_INVALID, key);
        }
        ClientMemory current = lock(companyId, normalized);
        Instant now = Instant.now();
        Map<String, Object> facts = new LinkedHashMap<>(current.facts());
        Map<String, MemorySource> sources = new LinkedHashMap<>(current.factSources());
        facts.put(key, request.value().trim());
        sources.put(key, MemorySource.operator(now));
        trimFacts(facts, sources);
        ClientMemory saved = repository.upsert(companyId, current.toBuilder()
                .facts(facts).factSources(sources).updatedAt(now).build());
        audit.record(companyId, "CLIENT_MEMORY_FACT_UPSERT", "client_memory", normalized, "Fact " + key + " set");
        return saved;
    }

    @Override
    @Transactional
    public ClientMemory deleteFactByCompanyIdAndPhone(long companyId, String phone, String key) {
        String normalized = PhoneNumbers.require(phone);
        ClientMemory current = lock(companyId, normalized);
        if (!current.facts().containsKey(key)) {
            throw new NotFoundException(ErrorCode.CLIENT_MEMORY_FACT_NOT_FOUND, key, phone);
        }
        Map<String, Object> facts = new LinkedHashMap<>(current.facts());
        Map<String, MemorySource> sources = new LinkedHashMap<>(current.factSources());
        facts.remove(key);
        sources.remove(key);
        ClientMemory saved = repository.upsert(companyId, current.toBuilder()
                .facts(facts).factSources(sources).updatedAt(Instant.now()).build());
        audit.record(companyId, "CLIENT_MEMORY_FACT_DELETE", "client_memory", normalized, "Fact " + key + " removed");
        return saved;
    }

    @Override
    @Transactional
    public ClientMemory updateCallByCompanyIdAndPhone(long companyId, String phone, String callId,
                                                      UpdateRememberedCallRequest request) {
        String normalized = PhoneNumbers.require(phone);
        ClientMemory current = lock(companyId, normalized);
        List<RememberedCall> calls = new ArrayList<>(current.recentCalls());
        int index = indexOf(calls, callId, phone);
        calls.set(index, calls.get(index).withSummary(request.summary().trim()));
        ClientMemory saved = repository.upsert(companyId, current.toBuilder()
                .recentCalls(calls).updatedAt(Instant.now()).build());
        audit.record(companyId, "CLIENT_MEMORY_CALL_UPDATE", "client_memory", normalized,
                "Remembered conversation " + callId + " corrected");
        return saved;
    }

    @Override
    @Transactional
    public ClientMemory deleteCallByCompanyIdAndPhone(long companyId, String phone, String callId) {
        String normalized = PhoneNumbers.require(phone);
        ClientMemory current = lock(companyId, normalized);
        List<RememberedCall> calls = new ArrayList<>(current.recentCalls());
        calls.remove(indexOf(calls, callId, phone));
        ClientMemory saved = repository.upsert(companyId, current.toBuilder()
                .recentCalls(calls).updatedAt(Instant.now()).build());
        audit.record(companyId, "CLIENT_MEMORY_CALL_DELETE", "client_memory", normalized,
                "Remembered conversation " + callId + " removed");
        return saved;
    }

    @Override
    @Transactional
    public void deleteByCompanyIdAndPhone(long companyId, String phone) {
        String normalized = PhoneNumbers.require(phone);
        if (!repository.deleteByCompanyIdAndPhone(companyId, normalized)) {
            throw new NotFoundException(ErrorCode.CLIENT_MEMORY_NOT_FOUND, phone);
        }
        audit.record(companyId, "CLIENT_MEMORY_DELETE", "client_memory", normalized, "Memory cleared by operator");
    }

    @Override
    public ClientMemory findByCompanyIdAndPhone(long companyId, String phone) {
        String normalized = PhoneNumbers.normalize(phone);
        return normalized == null ? null : repository.findByCompanyIdAndPhone(companyId, normalized);
    }

    @Override
    @Transactional
    public void rememberCall(long companyId, String phone, RememberedCall call, Map<String, Object> facts) {
        String normalized = PhoneNumbers.normalize(phone);
        if (normalized == null) {
            log.debug("Not remembering call to '{}': not a client phone", phone);
            return;
        }
        ClientMemory current = lock(companyId, normalized);

        // The outbox retries a call whose memory write it could not confirm: the same call replaces its entry.
        List<RememberedCall> recent = new ArrayList<>();
        recent.add(call.withId(UUID.randomUUID().toString()));
        current.recentCalls().stream()
                .filter(existing -> !isSameConversation(existing, call))
                .forEach(recent::add);
        if (recent.size() > clientMemoryProperties.keptCalls()) {
            recent = recent.subList(0, clientMemoryProperties.keptCalls());
        }

        Map<String, Object> mergedFacts = new LinkedHashMap<>(current.facts());
        Map<String, MemorySource> sources = new LinkedHashMap<>(current.factSources());
        overlay(mergedFacts, sources, facts, MemorySource.conversation(call, call.at() != null ? call.at() : Instant.now()));
        trimFacts(mergedFacts, sources);

        String preferredName = current.preferredName();
        if ((preferredName == null || preferredName.isBlank()) && facts != null) {
            Object name = facts.get("clientName");
            if (name == null) {
                name = facts.get("preferredName");
            }
            if (name != null && !name.toString().isBlank()) {
                preferredName = name.toString().trim();
            }
        }
        String preferredLanguage = current.preferredLanguage();
        if ((preferredLanguage == null || preferredLanguage.isBlank()) && facts != null) {
            Object lang = facts.get("preferredLanguage");
            if (lang == null) {
                lang = facts.get("language");
            }
            if (lang != null && !lang.toString().isBlank()) {
                preferredLanguage = lang.toString().trim();
            }
        }

        repository.upsert(companyId, current.toBuilder()
                .preferredName(preferredName)
                .preferredLanguage(preferredLanguage)
                .recentCalls(recent)
                .facts(mergedFacts)
                .factSources(sources)
                .updatedAt(Instant.now())
                .build());
    }

    @Override
    @Transactional
    public void rememberFacts(long companyId, String phone, Map<String, Object> facts, MemorySource source) {
        String normalized = PhoneNumbers.normalize(phone);
        if (normalized == null || facts == null || facts.isEmpty()) {
            return;
        }
        ClientMemory current = lock(companyId, normalized);
        Map<String, Object> mergedFacts = new LinkedHashMap<>(current.facts());
        Map<String, MemorySource> sources = new LinkedHashMap<>(current.factSources());
        overlay(mergedFacts, sources, facts, source);
        trimFacts(mergedFacts, sources);
        repository.upsert(companyId, current.toBuilder()
                .facts(mergedFacts).factSources(sources).updatedAt(Instant.now()).build());
    }

    @Override
    public int deleteNotUpdatedSince(Instant cutoff, int batchSize) {
        return repository.deleteNotUpdatedSince(cutoff, batchSize);
    }

    private ClientMemory lock(long companyId, String phone) {
        ClientMemory memory = repository.lockByCompanyIdAndPhone(companyId, phone);
        return memory != null ? memory : ClientMemory.empty(companyId, phone);
    }

    /** Each non-blank incoming value replaces the remembered one and is recorded as {@code source}'s. */
    private static void overlay(Map<String, Object> facts, Map<String, MemorySource> sources,
                                Map<String, Object> incoming, MemorySource source) {
        if (incoming == null) {
            return;
        }
        incoming.forEach((key, value) -> {
            if (key != null && value != null && !value.toString().isBlank()) {
                facts.put(key, value);
                if (source != null) {
                    sources.put(key, source);
                }
            }
        });
    }

    /**
     * Past {@code maxFacts}, the fact written longest ago goes first — one with no recorded source counts as
     * the oldest. What an operator typed is never dropped to make room.
     */
    private void trimFacts(Map<String, Object> facts, Map<String, MemorySource> sources) {
        while (facts.size() > clientMemoryProperties.maxFacts()) {
            String oldest = null;
            Instant oldestAt = null;
            for (String key : facts.keySet()) {
                MemorySource source = sources.get(key);
                if (source != null && source.kind() == MemorySourceKind.OPERATOR) {
                    continue;
                }
                Instant at = source != null && source.at() != null ? source.at() : Instant.MIN;
                if (oldestAt == null || at.isBefore(oldestAt)) {
                    oldest = key;
                    oldestAt = at;
                }
            }
            if (oldest == null) {
                return;
            }
            facts.remove(oldest);
            sources.remove(oldest);
        }
    }

    private static boolean isSameConversation(RememberedCall existing, RememberedCall incoming) {
        return (incoming.callAttemptId() != null && Objects.equals(existing.callAttemptId(), incoming.callAttemptId()))
                || (incoming.chatSessionId() != null && Objects.equals(existing.chatSessionId(), incoming.chatSessionId()));
    }

    private static int indexOf(List<RememberedCall> calls, String callId, String phone) {
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).id().equals(callId)) {
                return i;
            }
        }
        throw new NotFoundException(ErrorCode.CLIENT_MEMORY_CALL_NOT_FOUND, callId, phone);
    }

    /** Request semantics: {@code null} keeps, blank clears, anything else replaces. */
    private static String apply(String current, String incoming) {
        if (incoming == null) {
            return current;
        }
        return incoming.isBlank() ? null : incoming.trim();
    }
}
