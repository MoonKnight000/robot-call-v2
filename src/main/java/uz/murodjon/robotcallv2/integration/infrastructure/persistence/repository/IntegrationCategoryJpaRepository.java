package uz.murodjon.robotcallv2.integration.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity.IntegrationCategoryEntity;

import java.util.List;

@Repository
public interface IntegrationCategoryJpaRepository extends JpaRepository<IntegrationCategoryEntity, String> {

    List<IntegrationCategoryEntity> findAllByOrderBySortOrderAscKeyAsc();
}
