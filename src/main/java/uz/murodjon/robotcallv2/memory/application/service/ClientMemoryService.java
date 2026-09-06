package uz.murodjon.robotcallv2.memory.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.memory.application.dto.UpdateClientMemoryRequest;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.memory.application.port.output.ClientMemoryRepository;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-call memory per client (company + phone). The dialog reads it into the system
 * prompt; the post-call pipeline writes every summarized conversation back.
 */
@Service
public class ClientMemoryService implements ClientMemoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(ClientMemoryService.class);

    private final ClientMemoryRepository repository;
    private final AuditService audit;

    public ClientMemoryService(ClientMemoryRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
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
        ClientMemory current = orEmpty(repository.findByCompanyIdAndPhone(companyId, normalized), companyId, normalized);
        ClientMemory updated = new ClientMemory(
                current.id(), companyId, normalized,
                apply(current.preferredName(), request.preferredName()),
                apply(current.preferredLanguage(), request.preferredLanguage()),
                apply(current.operatorNotes(), request.operatorNotes()),
                current.recentCalls(), current.facts(), Instant.now());
        ClientMemory saved = repository.upsert(companyId, updated);
        audit.record(companyId, "CLIENT_MEMORY_UPDATE", "client_memory", normalized, "Memory updated by operator");
        return saved;
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
        ClientMemory current = orEmpty(repository.findByCompanyIdAndPhone(companyId, normalized), companyId, normalized);

        List<RememberedCall> recent = new ArrayList<>();
        recent.add(call);
        recent.addAll(current.recentCalls());
        if (recent.size() > ClientMemory.MAX_RECENT_CALLS) {
            recent = recent.subList(0, ClientMemory.MAX_RECENT_CALLS);
        }

        Map<String, Object> mergedFacts = new LinkedHashMap<>(current.facts());
        if (facts != null) {
            facts.forEach((key, value) -> {
                if (value != null && !value.toString().isBlank()) {
                    mergedFacts.put(key, value);
                }
            });
        }

        repository.upsert(companyId, new ClientMemory(
                current.id(), companyId, normalized,
                current.preferredName(), current.preferredLanguage(), current.operatorNotes(),
                recent, mergedFacts, Instant.now()));
    }

    private static ClientMemory orEmpty(ClientMemory memory, long companyId, String phone) {
        return memory != null ? memory : ClientMemory.empty(companyId, phone);
    }

    /** Request semantics: {@code null} keeps, blank clears, anything else replaces. */
    private static String apply(String current, String incoming) {
        if (incoming == null) {
            return current;
        }
        return incoming.isBlank() ? null : incoming.trim();
    }
}
