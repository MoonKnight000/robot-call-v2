package uz.murodjon.uysotvoice.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import uz.murodjon.uysotvoice.profile.entity.TableConfigEntity;

import java.util.Optional;

/** Spring Data repository for {@link TableConfigEntity}. */
public interface TableConfigJpaRepository extends JpaRepository<TableConfigEntity, Long> {

    Optional<TableConfigEntity> findByUserIdAndConfigKey(long userId, String configKey);

    void deleteByUserIdAndConfigKey(long userId, String configKey);
}
