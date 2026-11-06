package uz.murodjon.robotcallv2.contact.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.entity.ContactFieldEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactFieldJpaRepository extends JpaRepository<ContactFieldEntity, Long> {

    @Query("SELECT f FROM ContactFieldEntity f WHERE f.company.id = :companyId ORDER BY f.id")
    List<ContactFieldEntity> findAllByCompanyId(@Param("companyId") long companyId);

    @Query("SELECT f FROM ContactFieldEntity f WHERE f.id = :id AND f.company.id = :companyId")
    Optional<ContactFieldEntity> findByIdAndCompanyId(@Param("id") long id, @Param("companyId") long companyId);

    @Query("SELECT count(f) > 0 FROM ContactFieldEntity f WHERE f.company.id = :companyId AND lower(f.key) = lower(:key)")
    boolean existsByCompanyIdAndKey(@Param("companyId") long companyId, @Param("key") String key);

    @Query("SELECT count(f) FROM ContactFieldEntity f WHERE f.company.id = :companyId")
    long countByCompanyId(@Param("companyId") long companyId);
}
