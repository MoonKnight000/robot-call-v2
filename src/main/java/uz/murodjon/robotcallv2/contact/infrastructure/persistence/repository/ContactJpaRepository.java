package uz.murodjon.robotcallv2.contact.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.contact.infrastructure.persistence.entity.ContactEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContactJpaRepository extends JpaRepository<ContactEntity, Long> {

    @Query("SELECT c FROM ContactEntity c WHERE c.id = :id AND c.company.id = :companyId")
    Optional<ContactEntity> findByIdAndCompanyId(@Param("id") long id, @Param("companyId") long companyId);

    @Query("SELECT count(c) > 0 FROM ContactEntity c WHERE c.company.id = :companyId AND c.phone = :phone")
    boolean existsByCompanyIdAndPhone(@Param("companyId") long companyId, @Param("phone") String phone);

    @Query("SELECT c.phone, c.name FROM ContactEntity c WHERE c.company.id = :companyId AND c.phone IN :phones")
    List<Object[]> findNamesByPhones(@Param("companyId") long companyId, @Param("phones") Collection<String> phones);

    @Query("SELECT c FROM ContactEntity c WHERE c.company.id = :companyId "
            + "AND (:search IS NULL OR lower(c.name) LIKE :search OR c.phone LIKE :search)")
    List<ContactEntity> search(@Param("companyId") long companyId, @Param("search") String search, Pageable pageable);

    @Query("SELECT COUNT(c) FROM ContactEntity c WHERE c.company.id = :companyId "
            + "AND (:search IS NULL OR lower(c.name) LIKE :search OR c.phone LIKE :search)")
    long countSearch(@Param("companyId") long companyId, @Param("search") String search);
}
