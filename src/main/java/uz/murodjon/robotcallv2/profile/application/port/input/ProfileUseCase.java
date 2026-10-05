package uz.murodjon.robotcallv2.profile.application.port.input;

import uz.murodjon.robotcallv2.auth.application.dto.TwoFactorSetupResponse;
import uz.murodjon.robotcallv2.profile.application.dto.*;
import uz.murodjon.robotcallv2.profile.domain.entity.Profile;
import uz.murodjon.robotcallv2.profile.domain.entity.TodayStats;
import uz.murodjon.robotcallv2.shared.api.UploadedFile;

import java.util.List;

public interface ProfileUseCase {

    Profile find(long companyId);

    Profile update(long companyId, UpdateProfileRequest request);

    Profile uploadAvatar(long companyId, UploadedFile file);

    void changePassword(long companyId, ChangePasswordRequest request);

    List<String> updateCallColumns(long companyId, UpdateCallColumnsRequest request);

    TodayStats findTodayStats(long companyId);

    /** A new authenticator secret for the calling user (NEXT-PLAN S.3). */
    TwoFactorSetupResponse setupTwoFactor(long companyId);

    void enableTwoFactor(long companyId, TwoFactorCodeRequest request);

    void disableTwoFactor(long companyId, DisableTwoFactorRequest request);
}
