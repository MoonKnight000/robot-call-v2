package uz.murodjon.robotcallv2.profile.application.port.output;

import uz.murodjon.robotcallv2.profile.domain.entity.PersonalNotificationMatrixEntry;

import java.util.List;

public interface PersonalNotificationMatrixRepository {

    List<PersonalNotificationMatrixEntry> find(long userId);

    List<PersonalNotificationMatrixEntry> save(long userId, List<PersonalNotificationMatrixEntry> rows);
}
