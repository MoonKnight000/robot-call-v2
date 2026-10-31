package uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity.CallQueueMemberSkillEntity;
import uz.murodjon.robotcallv2.callqueue.infrastructure.persistence.entity.CallQueueMemberSkillId;

import java.util.Collection;
import java.util.List;

@Repository
public interface CallQueueMemberSkillJpaRepository extends JpaRepository<CallQueueMemberSkillEntity, CallQueueMemberSkillId> {

    List<CallQueueMemberSkillEntity> findByIdQueueId(Long queueId);

    List<CallQueueMemberSkillEntity> findByIdQueueIdIn(Collection<Long> queueIds);

    void deleteByIdQueueId(Long queueId);
}
