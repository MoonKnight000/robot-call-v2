package uz.murodjon.uysotvoice.company.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.company.entity.CompanyEntity;

import java.util.List;

/** Spring Data repository for {@link CompanyEntity}. */
@Repository
public interface CompanyJpaRepository extends JpaRepository<CompanyEntity, Long> {

    /**
     * {@code search} is a nullable, already-lowercased {@code %like%} pattern — the
     * {@code IS NULL} branch leaves the filter out entirely when the caller passes none,
     * matching the null-check idiom {@code ContactJpaRepository.search} uses.
     */
    @Query("SELECT c FROM CompanyEntity c WHERE :search IS NULL OR lower(c.name) LIKE :search")
    List<CompanyEntity> search(@Param("search") String search, Pageable pageable);

    @Query("SELECT COUNT(c) FROM CompanyEntity c WHERE :search IS NULL OR lower(c.name) LIKE :search")
    long countSearch(@Param("search") String search);
}
