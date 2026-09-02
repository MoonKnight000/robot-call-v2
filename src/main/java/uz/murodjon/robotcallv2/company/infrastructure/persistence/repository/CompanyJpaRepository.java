package uz.murodjon.robotcallv2.company.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

import java.util.List;

@Repository
public interface CompanyJpaRepository extends JpaRepository<CompanyEntity, Long> {

    @Query("SELECT c FROM CompanyEntity c WHERE :search IS NULL OR LOWER(c.name) LIKE :search OR LOWER(c.address) LIKE :search")
    List<CompanyEntity> search(@Param("search") String search, Pageable pageable);

    @Query("SELECT COUNT(c) FROM CompanyEntity c WHERE :search IS NULL OR LOWER(c.name) LIKE :search OR LOWER(c.address) LIKE :search")
    long countSearch(@Param("search") String search);
}
