package uz.murodjon.robotcallv2.profile.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.profile.infrastructure.persistence.entity.TableConfigEntity;

import java.util.Optional;

@Repository
public interface TableConfigJpaRepository extends JpaRepository<TableConfigEntity, Long> {

    @Query("SELECT c FROM TableConfigEntity c WHERE c.user.id = :userId AND c.configKey = :configKey")
    Optional<TableConfigEntity> findByUserIdAndConfigKey(@Param("userId") long userId, @Param("configKey") String configKey);

    @Modifying
    @Query("DELETE FROM TableConfigEntity c WHERE c.user.id = :userId AND c.configKey = :configKey")
    void deleteByUserIdAndConfigKey(@Param("userId") long userId, @Param("configKey") String configKey);
}
