package uz.murodjon.robotcallv2.sms.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.sms.infrastructure.persistence.entity.EskizConnectionEntity;

@Repository
public interface EskizConnectionJpaRepository extends JpaRepository<EskizConnectionEntity, Long> {
}
