package uz.murodjon.robotcallv2.profile.application.port.input;

import org.springframework.web.multipart.MultipartFile;
import uz.murodjon.robotcallv2.profile.application.dto.ChangePasswordRequest;
import uz.murodjon.robotcallv2.profile.application.dto.UpdateCallColumnsRequest;
import uz.murodjon.robotcallv2.profile.application.dto.UpdateProfileRequest;
import uz.murodjon.robotcallv2.profile.domain.entity.Profile;
import uz.murodjon.robotcallv2.profile.domain.entity.TodayStats;

import java.util.List;

public interface ProfileUseCase {

    Profile find(long companyId);

    Profile update(long companyId, UpdateProfileRequest request);

    Profile uploadAvatar(long companyId, MultipartFile file);

    void changePassword(long companyId, ChangePasswordRequest request);

    List<String> updateCallColumns(long companyId, UpdateCallColumnsRequest request);

    TodayStats todayStats(long companyId);
}
