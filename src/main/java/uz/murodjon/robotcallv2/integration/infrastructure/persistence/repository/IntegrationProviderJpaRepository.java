package uz.murodjon.robotcallv2.integration.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity.IntegrationProviderEntity;

import java.util.List;

@Repository
public interface IntegrationProviderJpaRepository extends JpaRepository<IntegrationProviderEntity, String> {

    List<IntegrationProviderEntity> findAllByOrderBySortOrderAscKeyAsc();

    long countByCategory(String category);
}
