package uz.murodjon.robotcallv2.copilot.infrastructure.persistence.adapter;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.copilot.application.port.output.CopilotRepository;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotMessage;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSession;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSessionFilter;
import uz.murodjon.robotcallv2.copilot.infrastructure.persistence.entity.CopilotMessageEntity;
import uz.murodjon.robotcallv2.copilot.infrastructure.persistence.entity.CopilotSessionEntity;
import uz.murodjon.robotcallv2.copilot.infrastructure.persistence.mapper.CopilotEntityMapper;
import uz.murodjon.robotcallv2.copilot.infrastructure.persistence.repository.CopilotMessageJpaRepository;
import uz.murodjon.robotcallv2.copilot.infrastructure.persistence.repository.CopilotSessionJpaRepository;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.time.Instant;
import java.util.List;

@Component
public class CopilotRepositoryAdapter implements CopilotRepository {

    private final CopilotSessionJpaRepository sessionJpaRepository;
    private final CopilotMessageJpaRepository messageJpaRepository;
    private final CopilotEntityMapper mapper;

    public CopilotRepositoryAdapter(CopilotSessionJpaRepository sessionJpaRepository,
                                    CopilotMessageJpaRepository messageJpaRepository, CopilotEntityMapper mapper) {
        this.sessionJpaRepository = sessionJpaRepository;
        this.messageJpaRepository = messageJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public CopilotSession createSession(long companyId, CopilotSession session) {
        CopilotSessionEntity entity = new CopilotSessionEntity();
        entity.setCompanyId(companyId);
        entity.setUserId(session.userId());
        entity.setTitle(session.title());
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return mapper.toCopilotSession(sessionJpaRepository.save(entity));
    }

    @Override
    public CopilotSession findSessionByCompanyIdAndUserIdAndId(long companyId, long userId, long id) {
        return sessionJpaRepository.findByCompanyIdAndUserIdAndId(companyId, userId, id)
                .map(mapper::toCopilotSession).orElse(null);
    }

    @Override
    public PageableData<CopilotSession> findSessionPage(long companyId, long userId, CopilotSessionFilter filter) {
        Specification<CopilotSessionEntity> own = (root, query, cb) ->
                cb.and(cb.equal(root.get("companyId"), companyId), cb.equal(root.get("userId"), userId));
        Page<CopilotSessionEntity> page = sessionJpaRepository.findAll(own, filter.pageable());
        return PageableData.of(page.getContent().stream().map(mapper::toCopilotSession).toList(),
                filter.pageable().getPageNumber(), filter.pageable().getPageSize(), page.getTotalElements());
    }

    @Override
    public void touchSession(long id) {
        sessionJpaRepository.touch(id);
    }

    @Override
    public void deleteSession(long companyId, long userId, long id) {
        sessionJpaRepository.deleteByCompanyIdAndUserIdAndId(companyId, userId, id);
    }

    @Override
    public CopilotMessage createMessage(long companyId, CopilotMessage message) {
        CopilotMessageEntity entity = mapper.toCopilotMessageEntity(companyId, message);
        entity.setCreatedAt(Instant.now());
        return mapper.toCopilotMessage(messageJpaRepository.save(entity));
    }

    @Override
    public List<CopilotMessage> findMessagesBySessionId(long sessionId) {
        return messageJpaRepository.findBySessionIdOrderById(sessionId).stream()
                .map(mapper::toCopilotMessage).toList();
    }

    @Override
    public void updateMessageCharge(long id, long chargedUzs) {
        messageJpaRepository.updateChargedUzs(id, chargedUzs);
    }
}
