package uz.murodjon.uysotvoice.profile.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.profile.dto.PersonalNotificationMatrixEntry;
import uz.murodjon.uysotvoice.profile.entity.PersonalNotificationMatrixEntity;

import java.util.List;

/** JPA-backed DAO for {@code personal_notification_matrix} (API-REQUIREMENTS §15). */
@Repository
public class PersonalNotificationMatrixRepository {

    private final PersonalNotificationMatrixJpaRepository jpa;
    private final CurrentCompany company;

    public PersonalNotificationMatrixRepository(PersonalNotificationMatrixJpaRepository jpa, CurrentCompany company) {
        this.jpa = jpa;
        this.company = company;
    }

    public List<PersonalNotificationMatrixEntry> find(long userId) {
        return jpa.findByUserId(userId).stream()
                .map(e -> new PersonalNotificationMatrixEntry(e.getType(), e.getChannel(), e.isEnabled()))
                .toList();
    }

    /** Whole-grid replace — same reasoning as {@code NotificationSettingsRepository#save}. */
    @Transactional
    public List<PersonalNotificationMatrixEntry> save(long userId, List<PersonalNotificationMatrixEntry> rows) {
        jpa.deleteByUserId(userId);
        long companyId = company.id();
        for (PersonalNotificationMatrixEntry row : rows) {
            PersonalNotificationMatrixEntity entity = new PersonalNotificationMatrixEntity();
            entity.setCompanyId(companyId);
            entity.setUserId(userId);
            entity.setType(row.type());
            entity.setChannel(row.channel());
            entity.setEnabled(row.enabled());
            jpa.save(entity);
        }
        return find(userId);
    }
}
