package uz.murodjon.uysotvoice.inbound.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.inbound.entity.InboundRouteEntity;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link InboundRouteEntity}. */
@Repository
public interface InboundRouteJpaRepository extends JpaRepository<InboundRouteEntity, Long> {

    Optional<InboundRouteEntity> findByIdAndCompanyId(long id, long companyId);

    List<InboundRouteEntity> findByCompanyId(long companyId, Pageable pageable);

    long countByCompanyId(long companyId);

    /** A DID is a real phone number — globally unique among enabled routes, not per-company. */
    boolean existsByDidNumberAndEnabledTrue(String didNumber);

    boolean existsByDidNumberAndEnabledTrueAndIdNot(String didNumber, long id);

    /**
     * The runtime lookup {@code AriService} makes at {@code StasisStart} (ROADMAP C.1) —
     * deliberately unscoped by company: resolving the company from the dialled DID is
     * the whole point, so {@code CurrentCompany} cannot gate this query the way every
     * other repository's queries are gated.
     */
    Optional<InboundRouteEntity> findByDidNumberAndEnabledTrue(String didNumber);
}
