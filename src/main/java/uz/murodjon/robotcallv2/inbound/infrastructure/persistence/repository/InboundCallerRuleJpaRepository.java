package uz.murodjon.robotcallv2.inbound.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.entity.InboundCallerRuleEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface InboundCallerRuleJpaRepository extends JpaRepository<InboundCallerRuleEntity, Long> {

    Optional<InboundCallerRuleEntity> findByIdAndCompanyId(long id, long companyId);

    List<InboundCallerRuleEntity> findByCompanyId(long companyId, Pageable pageable);

    List<InboundCallerRuleEntity> findByCompanyId(long companyId);

    long countByCompanyId(long companyId);

    boolean existsByCompanyIdAndPhonePrefix(long companyId, String phonePrefix);

    boolean existsByCompanyIdAndPhonePrefixAndIdNot(long companyId, String phonePrefix, long id);
}
