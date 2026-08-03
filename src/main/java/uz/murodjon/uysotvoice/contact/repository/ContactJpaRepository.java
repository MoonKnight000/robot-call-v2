package uz.murodjon.uysotvoice.contact.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.contact.entity.ContactEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link ContactEntity}. */
@Repository
public interface ContactJpaRepository extends JpaRepository<ContactEntity, Long> {

    Optional<ContactEntity> findByIdAndCompanyId(long id, long companyId);

    boolean existsByCompanyIdAndPhone(long companyId, String phone);

    /** Cheap phone→name lookup for other features to enrich rows with a contact name
     * (e.g. {@code DoNotCallRow}) — contacts are keyed by phone within a company. */
    @Query("SELECT c.phone, c.name FROM ContactEntity c WHERE c.companyId = :companyId AND c.phone IN :phones")
    List<Object[]> findNamesByPhones(@Param("companyId") long companyId, @Param("phones") Collection<String> phones);

    /**
     * {@code search} is a nullable, already-lowercased {@code %like%} pattern — the
     * {@code IS NULL} branch leaves the filter out entirely when the caller passes none,
     * matching the null-check idiom {@code ScenarioJpaRepository.findVisible} uses for
     * {@code builtinOnly}.
     */
    @Query("SELECT c FROM ContactEntity c WHERE c.companyId = :companyId "
            + "AND (:search IS NULL OR lower(c.name) LIKE :search OR c.phone LIKE :search)")
    List<ContactEntity> search(@Param("companyId") long companyId, @Param("search") String search, Pageable pageable);

    @Query("SELECT COUNT(c) FROM ContactEntity c WHERE c.companyId = :companyId "
            + "AND (:search IS NULL OR lower(c.name) LIKE :search OR c.phone LIKE :search)")
    long countSearch(@Param("companyId") long companyId, @Param("search") String search);
}
