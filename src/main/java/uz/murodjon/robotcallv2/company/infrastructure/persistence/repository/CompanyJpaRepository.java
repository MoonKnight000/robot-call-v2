package uz.murodjon.robotcallv2.company.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyJpaRepository extends JpaRepository<CompanyEntity, Long>,
        JpaSpecificationExecutor<CompanyEntity> {

    /**
     * The company with its switches in one joined query rather than the two follow-up selects of
     * the eager SUBSELECT collections — this lookup sits on every call, API-key request and login.
     */
    @Override
    @EntityGraph(attributePaths = {"features", "integrationAccess"})
    Optional<CompanyEntity> findById(Long id);

    List<CompanyEntity> findByStatusAndExpiresAtLessThanEqual(CompanyStatus status, Instant expiresAt);

    /**
     * Native rather than a persist: {@code company.id} is {@code GenerationType.IDENTITY},
     * so a persist would discard the caller's id and let the sequence pick another one.
     */
    @Modifying
    @Query(value = "INSERT INTO company(id, name, status, created_at) VALUES (:id, :name, 'ACTIVE', now()) "
            + "ON CONFLICT (id) DO NOTHING", nativeQuery = true)
    void insertWithIdIfAbsent(@Param("id") long id, @Param("name") String name);
}
