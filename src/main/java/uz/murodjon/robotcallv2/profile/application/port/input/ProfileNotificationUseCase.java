package uz.murodjon.robotcallv2.profile.application.port.input;

import uz.murodjon.robotcallv2.profile.application.dto.UpdatePersonalNotificationSettingsRequest;
import uz.murodjon.robotcallv2.profile.domain.entity.PersonalNotificationMatrixEntry;

import java.util.List;

public interface ProfileNotificationUseCase {

    List<PersonalNotificationMatrixEntry> find();

    List<PersonalNotificationMatrixEntry> update(UpdatePersonalNotificationSettingsRequest r);
}
