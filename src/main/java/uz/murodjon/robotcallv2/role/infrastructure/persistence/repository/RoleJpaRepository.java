package uz.murodjon.robotcallv2.role.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.role.infrastructure.persistence.entity.RoleEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleJpaRepository extends JpaRepository<RoleEntity, Long> {

    List<RoleEntity> findByCompanyIdOrderById(long companyId);

    Optional<RoleEntity> findByIdAndCompanyId(long id, long companyId);

    Optional<RoleEntity> findByCompanyIdAndCode(long companyId, String code);

    long countByCompanyIdAndSystem(long companyId, boolean system);

    @Query("SELECT COUNT(r) > 0 FROM RoleEntity r WHERE r.companyId = :companyId "
            + "AND LOWER(r.name) = LOWER(:name) AND (:excludeId IS NULL OR r.id <> :excludeId)")
    boolean existsByName(@Param("companyId") long companyId, @Param("name") String name,
                         @Param("excludeId") Long excludeId);
}
