package uz.murodjon.robotcallv2.partner.infrastructure.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.partner.infrastructure.persistence.entity.PartnerClientEntity;

@Repository
public interface PartnerClientJpaRepository extends JpaRepository<PartnerClientEntity, Long> {

    Page<PartnerClientEntity> findAllByPartnerCompanyId(Long partnerCompanyId, Pageable pageable);
}
