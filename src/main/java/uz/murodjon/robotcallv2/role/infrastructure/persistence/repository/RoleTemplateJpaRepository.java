package uz.murodjon.robotcallv2.role.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.role.infrastructure.persistence.entity.RoleTemplateEntity;

import java.util.List;

@Repository
public interface RoleTemplateJpaRepository extends JpaRepository<RoleTemplateEntity, Long> {

    List<RoleTemplateEntity> findAllByOrderById();

    @Query("SELECT COUNT(t) > 0 FROM RoleTemplateEntity t "
            + "WHERE LOWER(t.name) = LOWER(:name) AND (:excludeId IS NULL OR t.id <> :excludeId)")
    boolean existsByName(@Param("name") String name, @Param("excludeId") Long excludeId);
}
