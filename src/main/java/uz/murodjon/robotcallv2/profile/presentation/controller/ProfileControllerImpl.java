package uz.murodjon.robotcallv2.profile.presentation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.murodjon.robotcallv2.auth.application.dto.UserSessionRow;
import uz.murodjon.robotcallv2.profile.application.dto.*;
import uz.murodjon.robotcallv2.profile.application.port.input.*;
import uz.murodjon.robotcallv2.profile.domain.entity.PersonalNotificationMatrixEntry;
import uz.murodjon.robotcallv2.profile.domain.entity.Profile;
import uz.murodjon.robotcallv2.profile.domain.entity.ScheduleSlot;
import uz.murodjon.robotcallv2.profile.domain.entity.TodayStats;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class ProfileControllerImpl implements ProfileController {

    private final ProfileUseCase profile;
    private final ProfileSessionUseCase sessions;
    private final ProfileNotificationUseCase notifications;
    private final ProfileScheduleUseCase schedule;
    private final ProfileTableConfigUseCase tableConfig;

    public ProfileControllerImpl(ProfileUseCase profile,
                                 ProfileSessionUseCase sessions,
                                 ProfileNotificationUseCase notifications,
                                 ProfileScheduleUseCase schedule,
                                 ProfileTableConfigUseCase tableConfig) {
        this.profile = profile;
        this.sessions = sessions;
        this.notifications = notifications;
        this.schedule = schedule;
        this.tableConfig = tableConfig;
    }

    @Override
    public ResponseEntity<ResponseData<Profile>> get(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(profile.find(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<Profile>> update(long companyId, UpdateProfileRequest request) {
        return ResponseEntity.ok(ResponseData.ok(profile.update(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Profile>> uploadAvatar(long companyId, MultipartFile file) {
        return ResponseEntity.ok(ResponseData.ok(profile.uploadAvatar(companyId, file)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> changePassword(long companyId, ChangePasswordRequest request) {
        profile.changePassword(companyId, request);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<List<UserSessionRow>>> sessions() {
        return ResponseEntity.ok(ResponseData.ok(sessions.list()));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> revokeSession(long companyId, long id) {
        sessions.revoke(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<List<PersonalNotificationMatrixEntry>>> notifications() {
        return ResponseEntity.ok(ResponseData.ok(notifications.find()));
    }

    @Override
    public ResponseEntity<ResponseData<List<PersonalNotificationMatrixEntry>>> updateNotifications(
            long companyId, UpdatePersonalNotificationSettingsRequest request) {
        return ResponseEntity.ok(ResponseData.ok(notifications.update(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<List<ScheduleSlot>>> schedule() {
        return ResponseEntity.ok(ResponseData.ok(schedule.find()));
    }

    @Override
    public ResponseEntity<ResponseData<List<ScheduleSlot>>> updateSchedule(long companyId, UpdateScheduleRequest request) {
        return ResponseEntity.ok(ResponseData.ok(schedule.update(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<TodayStats>> todayStats(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(profile.todayStats(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<String>>> updateCallColumns(long companyId, UpdateCallColumnsRequest request) {
        return ResponseEntity.ok(ResponseData.ok(profile.updateCallColumns(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<JsonNode>> tableConfig(String key) {
        return ResponseEntity.ok(ResponseData.ok(tableConfig.find(key)));
    }

    @Override
    public ResponseEntity<ResponseData<JsonNode>> updateTableConfig(long companyId, String key, JsonNode value) {
        return ResponseEntity.ok(ResponseData.ok(tableConfig.update(companyId, key, value)));
    }
}
