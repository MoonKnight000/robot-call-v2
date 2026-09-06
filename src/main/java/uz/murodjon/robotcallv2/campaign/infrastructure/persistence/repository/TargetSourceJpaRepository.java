package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.TargetSourceEntity;

public interface TargetSourceJpaRepository extends JpaRepository<TargetSourceEntity, Long> {
}
