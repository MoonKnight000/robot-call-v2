package uz.murodjon.robotcallv2.integration.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity.IntegrationToolEntity;

import java.util.List;

@Repository
public interface IntegrationToolJpaRepository extends JpaRepository<IntegrationToolEntity, Long> {

    List<IntegrationToolEntity> findByProviderOrderBySortOrderAscIdAsc(CrmProvider provider);
}
