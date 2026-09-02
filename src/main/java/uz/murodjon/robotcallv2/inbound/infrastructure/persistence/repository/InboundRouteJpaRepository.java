package uz.murodjon.robotcallv2.inbound.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.entity.InboundRouteEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface InboundRouteJpaRepository extends JpaRepository<InboundRouteEntity, Long> {

    Optional<InboundRouteEntity> findByIdAndCompanyId(long id, long companyId);

    List<InboundRouteEntity> findByCompanyId(long companyId, Pageable pageable);

    long countByCompanyId(long companyId);

    boolean existsByDidNumberAndEnabledTrue(String didNumber);

    boolean existsByDidNumberAndEnabledTrueAndIdNot(String didNumber, long id);

    Optional<InboundRouteEntity> findByDidNumberAndEnabledTrue(String didNumber);
}
