package uz.murodjon.uysotvoice.contact.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.contact.entity.Contact;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link Contact}. */
@Repository
public interface ContactJpaRepository extends JpaRepository<Contact, Long> {

    Optional<Contact> findByIdAndCompanyId(long id, long companyId);

    boolean existsByCompanyIdAndPhone(long companyId, String phone);

    /**
     * {@code search} is a nullable, already-lowercased {@code %like%} pattern — the
     * {@code IS NULL} branch leaves the filter out entirely when the caller passes none,
     * matching the null-check idiom {@code ScenarioJpaRepository.findVisible} uses for
     * {@code builtinOnly}.
     */
    @Query("SELECT c FROM Contact c WHERE c.companyId = :companyId "
            + "AND (:search IS NULL OR lower(c.name) LIKE :search OR c.phone LIKE :search)")
    List<Contact> search(@Param("companyId") long companyId, @Param("search") String search, Pageable pageable);

    @Query("SELECT COUNT(c) FROM Contact c WHERE c.companyId = :companyId "
            + "AND (:search IS NULL OR lower(c.name) LIKE :search OR c.phone LIKE :search)")
    long countSearch(@Param("companyId") long companyId, @Param("search") String search);
}
