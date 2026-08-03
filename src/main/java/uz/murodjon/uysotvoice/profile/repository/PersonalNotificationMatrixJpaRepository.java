package uz.murodjon.uysotvoice.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import uz.murodjon.uysotvoice.profile.entity.PersonalNotificationMatrixEntity;

import java.util.List;

/** Spring Data repository for {@link PersonalNotificationMatrixEntity}. */
public interface PersonalNotificationMatrixJpaRepository extends JpaRepository<PersonalNotificationMatrixEntity, Long> {

    List<PersonalNotificationMatrixEntity> findByUserId(long userId);

    void deleteByUserId(long userId);
}
