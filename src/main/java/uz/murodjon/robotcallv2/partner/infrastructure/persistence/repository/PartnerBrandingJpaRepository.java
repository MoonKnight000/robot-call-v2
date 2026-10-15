package uz.murodjon.robotcallv2.partner.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.partner.infrastructure.persistence.entity.PartnerBrandingEntity;

import java.util.Optional;

@Repository
public interface PartnerBrandingJpaRepository extends JpaRepository<PartnerBrandingEntity, Long> {

    Optional<PartnerBrandingEntity> findByDomain(String domain);

    boolean existsByDomainAndCompanyIdNot(String domain, Long companyId);
}
