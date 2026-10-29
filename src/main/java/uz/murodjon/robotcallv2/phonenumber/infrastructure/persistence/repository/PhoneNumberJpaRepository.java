package uz.murodjon.robotcallv2.phonenumber.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.phonenumber.infrastructure.persistence.entity.PhoneNumberEntity;

import java.util.Collection;
import java.util.List;

public interface PhoneNumberJpaRepository extends JpaRepository<PhoneNumberEntity, Long>,
        JpaSpecificationExecutor<PhoneNumberEntity> {

    boolean existsByPhoneNumber(String phoneNumber);

    @Query("SELECT p.phoneNumber FROM PhoneNumberEntity p WHERE p.phoneNumber IN :phoneNumbers")
    List<String> findPhoneNumbersIn(@Param("phoneNumbers") Collection<String> phoneNumbers);
}
