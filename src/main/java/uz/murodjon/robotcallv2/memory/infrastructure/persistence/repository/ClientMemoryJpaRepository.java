package uz.murodjon.robotcallv2.memory.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.memory.infrastructure.persistence.entity.ClientMemoryEntity;

import java.util.Optional;

@Repository
public interface ClientMemoryJpaRepository extends JpaRepository<ClientMemoryEntity, Long> {

    Optional<ClientMemoryEntity> findByCompanyIdAndPhone(long companyId, String phone);
}
