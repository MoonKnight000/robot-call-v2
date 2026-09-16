package uz.murodjon.robotcallv2.company.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

@Repository
public interface CompanyJpaRepository extends JpaRepository<CompanyEntity, Long>,
        JpaSpecificationExecutor<CompanyEntity> {
}
