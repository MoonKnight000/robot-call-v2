package uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueFilter;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.callqueue.domain.enums.CallQueueStrategy;
import uz.murodjon.robotcallv2.callqueue.domain.enums.QueueOverflowAction;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity.CallQueueEntity;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity.CallQueueMemberEntity;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity.CallQueueMemberSkillEntity;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.mapper.CallQueueEntityMapper;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.repository.CallQueueJpaRepository;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.repository.CallQueueMemberSkillJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CallQueueRepositoryAdapterTest {

    private static final long COMPANY_ID = 1L;
    private static final long QUEUE_ID = 100L;

    private CallQueueJpaRepository jpaRepository;
    private CallQueueMemberSkillJpaRepository skillJpaRepository;
    private CallQueueEntityMapper mapper;
    private CallQueueRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(CallQueueJpaRepository.class);
        skillJpaRepository = mock(CallQueueMemberSkillJpaRepository.class);
        mapper = new CallQueueEntityMapper();
        adapter = new CallQueueRepositoryAdapter(jpaRepository, skillJpaRepository, mapper);
    }

    @Test
    void createPersistsQueueAndMemberSkills() {
        CallQueueMember member1 = new CallQueueMember(10L, 0, Set.of("uz-UZ", "ru-RU"));
        CallQueueMember member2 = new CallQueueMember(20L, 1, Set.of("sales"));
        CallQueue queue = new CallQueue(0L, COMPANY_ID, "Support", CallQueueStrategy.SKILL_BASED,
                15, 10, 300, 20, 60, true, null, 0, null, 0, null,
                QueueOverflowAction.HANGUP, null, 20, 0, false, null, false, true,
                List.of(member1, member2), Instant.now());

        CallQueueEntity savedEntity = new CallQueueEntity();
        savedEntity.setCompanyId(COMPANY_ID);
        savedEntity.setName("Support");
        // Simulate database generated ID
        try {
            var field = CallQueueEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(savedEntity, QUEUE_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(jpaRepository.save(any(CallQueueEntity.class))).thenReturn(savedEntity);

        long createdId = adapter.create(COMPANY_ID, queue);

        assertThat(createdId).isEqualTo(QUEUE_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CallQueueMemberSkillEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(skillJpaRepository).saveAll(captor.capture());

        List<CallQueueMemberSkillEntity> skills = captor.getValue();
        assertThat(skills).hasSize(3);
        assertThat(skills).extracting(CallQueueMemberSkillEntity::getSkill)
                .containsExactlyInAnyOrder("uz-UZ", "ru-RU", "sales");
    }

    @Test
    void findLoadsQueueAndCombinesMemberSkills() {
        CallQueueEntity entity = new CallQueueEntity();
        try {
            var field = CallQueueEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, QUEUE_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        entity.setCompanyId(COMPANY_ID);
        entity.setName("Support");
        entity.setMembers(List.of(
                new CallQueueMemberEntity(10L, 0),
                new CallQueueMemberEntity(20L, 1)
        ));

        when(jpaRepository.findByIdAndCompanyId(QUEUE_ID, COMPANY_ID)).thenReturn(Optional.of(entity));
        when(skillJpaRepository.findByIdQueueId(QUEUE_ID)).thenReturn(List.of(
                new CallQueueMemberSkillEntity(QUEUE_ID, 10L, "uz-UZ"),
                new CallQueueMemberSkillEntity(QUEUE_ID, 10L, "ru-RU"),
                new CallQueueMemberSkillEntity(QUEUE_ID, 20L, "sales")
        ));

        CallQueue result = adapter.find(COMPANY_ID, QUEUE_ID);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(QUEUE_ID);
        assertThat(result.members()).hasSize(2);

        CallQueueMember m1 = result.members().stream().filter(m -> m.userId() == 10L).findFirst().orElseThrow();
        assertThat(m1.skills()).containsExactlyInAnyOrder("uz-UZ", "ru-RU");

        CallQueueMember m2 = result.members().stream().filter(m -> m.userId() == 20L).findFirst().orElseThrow();
        assertThat(m2.skills()).containsExactly("sales");
    }

    @Test
    void findAllBatchLoadsSkillsForMultipleQueues() {
        CallQueueEntity q1 = new CallQueueEntity();
        CallQueueEntity q2 = new CallQueueEntity();
        try {
            var field = CallQueueEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(q1, 101L);
            field.set(q2, 102L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        q1.setCompanyId(COMPANY_ID);
        q1.setName("Q1");
        q1.setMembers(List.of(new CallQueueMemberEntity(10L, 0)));

        q2.setCompanyId(COMPANY_ID);
        q2.setName("Q2");
        q2.setMembers(List.of(new CallQueueMemberEntity(20L, 0)));

        when(jpaRepository.findByCompanyId(eq(COMPANY_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(q1, q2)));

        when(skillJpaRepository.findByIdQueueIdIn(List.of(101L, 102L))).thenReturn(List.of(
                new CallQueueMemberSkillEntity(101L, 10L, "uz-UZ"),
                new CallQueueMemberSkillEntity(102L, 20L, "ru-RU")
        ));

        List<CallQueue> queues = adapter.findAll(COMPANY_ID, new CallQueueFilter(1, 10));

        assertThat(queues).hasSize(2);
        assertThat(queues.get(0).members().get(0).skills()).containsExactly("uz-UZ");
        assertThat(queues.get(1).members().get(0).skills()).containsExactly("ru-RU");

        verify(skillJpaRepository).findByIdQueueIdIn(List.of(101L, 102L));
        verify(skillJpaRepository, never()).findByIdQueueId(any());
    }

    @Test
    void updateReplacesSkills() {
        CallQueueEntity entity = new CallQueueEntity();
        try {
            var field = CallQueueEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, QUEUE_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        entity.setCompanyId(COMPANY_ID);
        entity.setName("Old");

        when(jpaRepository.findByIdAndCompanyId(QUEUE_ID, COMPANY_ID)).thenReturn(Optional.of(entity));

        CallQueue queue = new CallQueue(QUEUE_ID, COMPANY_ID, "New Name", CallQueueStrategy.ROUND_ROBIN,
                20, 10, 180, 10, 30, false, null, 0, null, 0, null,
                QueueOverflowAction.HANGUP, null, 15, 0, false, null, false, true,
                List.of(new CallQueueMember(10L, 0, Set.of("vip"))), Instant.now());

        adapter.update(COMPANY_ID, QUEUE_ID, queue);

        verify(jpaRepository).save(entity);
        verify(skillJpaRepository).deleteByIdQueueId(QUEUE_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CallQueueMemberSkillEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(skillJpaRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(CallQueueMemberSkillEntity::getSkill).containsExactly("vip");
    }

    @Test
    void overflowTargetPopulatesTypedColumnsCorrectly() {
        CallQueueEntity entity = new CallQueueEntity();

        entity.setOverflowAction(QueueOverflowAction.VOICEMAIL);
        entity.setOverflowTarget("42");
        assertThat(entity.getOverflowVoicemailId()).isEqualTo(42L);
        assertThat(entity.getOverflowQueueId()).isNull();
        assertThat(entity.getOverflowAgentId()).isNull();
        assertThat(entity.getOverflowExternalPhone()).isNull();
        assertThat(entity.getOverflowTarget()).isEqualTo("42");

        entity.setOverflowAction(QueueOverflowAction.AI_AGENT);
        entity.setOverflowTarget("99");
        assertThat(entity.getOverflowAgentId()).isEqualTo(99L);
        assertThat(entity.getOverflowVoicemailId()).isNull();
        assertThat(entity.getOverflowTarget()).isEqualTo("99");

        entity.setOverflowAction(QueueOverflowAction.EXTERNAL_NUMBER);
        entity.setOverflowTarget("+998901234567");
        assertThat(entity.getOverflowExternalPhone()).isEqualTo("+998901234567");
        assertThat(entity.getOverflowAgentId()).isNull();
        assertThat(entity.getOverflowTarget()).isEqualTo("+998901234567");
    }
}
