package uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.entity.CallWrapUpEntity;

@Repository
public interface CallWrapUpJpaRepository extends JpaRepository<CallWrapUpEntity, Long> {
}
