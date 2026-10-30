package uz.murodjon.robotcallv2.integration.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity.CompanyIntegrationToolEntity;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity.CompanyIntegrationToolId;

import java.util.List;

@Repository
public interface CompanyIntegrationToolJpaRepository
        extends JpaRepository<CompanyIntegrationToolEntity, CompanyIntegrationToolId> {

    List<CompanyIntegrationToolEntity> findByIdCompanyId(Long companyId);

    /** Writes {@code enabled} as the choice of every company that has none for the tool. */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO company_integration_tool (company_id, tool_id, enabled) "
            + "SELECT c.id, :toolId, :enabled FROM company c WHERE NOT EXISTS ("
            + "SELECT 1 FROM company_integration_tool o WHERE o.company_id = c.id AND o.tool_id = :toolId)",
            nativeQuery = true)
    int pinCompaniesWithoutChoice(@Param("toolId") long toolId, @Param("enabled") boolean enabled);
}
