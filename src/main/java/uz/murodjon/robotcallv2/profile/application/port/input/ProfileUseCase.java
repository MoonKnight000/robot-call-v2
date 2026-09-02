package uz.murodjon.robotcallv2.profile.application.port.input;

import org.springframework.web.multipart.MultipartFile;
import uz.murodjon.robotcallv2.profile.application.dto.ChangePasswordRequest;
import uz.murodjon.robotcallv2.profile.application.dto.UpdateCallColumnsRequest;
import uz.murodjon.robotcallv2.profile.application.dto.UpdateProfileRequest;
import uz.murodjon.robotcallv2.profile.domain.entity.Profile;
import uz.murodjon.robotcallv2.profile.domain.entity.TodayStats;

import java.util.List;

public interface ProfileUseCase {

    Profile find();

    Profile update(UpdateProfileRequest r);

    Profile uploadAvatar(MultipartFile file);

    void changePassword(ChangePasswordRequest r);

    List<String> updateCallColumns(UpdateCallColumnsRequest r);

    TodayStats todayStats();
}
