package uz.murodjon.uysotvoice.profile.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import uz.murodjon.uysotvoice.auth.dto.UserSessionRow;
import uz.murodjon.uysotvoice.profile.dto.ChangePasswordRequest;
import uz.murodjon.uysotvoice.profile.dto.PersonalNotificationMatrixEntry;
import uz.murodjon.uysotvoice.profile.dto.Profile;
import uz.murodjon.uysotvoice.profile.dto.ScheduleSlot;
import uz.murodjon.uysotvoice.profile.dto.TodayStats;
import uz.murodjon.uysotvoice.profile.dto.UpdateCallColumnsRequest;
import uz.murodjon.uysotvoice.profile.dto.UpdatePersonalNotificationSettingsRequest;
import uz.murodjon.uysotvoice.profile.dto.UpdateProfileRequest;
import uz.murodjon.uysotvoice.profile.dto.UpdateScheduleRequest;
import uz.murodjon.uysotvoice.profile.service.ProfileNotificationService;
import uz.murodjon.uysotvoice.profile.service.ProfileScheduleService;
import uz.murodjon.uysotvoice.profile.service.ProfileService;
import uz.murodjon.uysotvoice.profile.service.ProfileSessionService;
import uz.murodjon.uysotvoice.profile.service.ProfileTableConfigService;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

import java.util.List;

@RestController
public class ProfileControllerImpl implements ProfileController {

    private final ProfileService profile;
    private final ProfileSessionService sessions;
    private final ProfileNotificationService notifications;
    private final ProfileScheduleService schedule;
    private final ProfileTableConfigService tableConfig;

    public ProfileControllerImpl(ProfileService profile, ProfileSessionService sessions,
                                  ProfileNotificationService notifications, ProfileScheduleService schedule,
                                  ProfileTableConfigService tableConfig) {
        this.profile = profile;
        this.sessions = sessions;
        this.notifications = notifications;
        this.schedule = schedule;
        this.tableConfig = tableConfig;
    }

    @Override
    public ResponseEntity<ResponseData<Profile>> get() {
        return ResponseEntity.ok(ResponseData.ok(profile.find()));
    }

    @Override
    public ResponseEntity<ResponseData<Profile>> update(UpdateProfileRequest r) {
        return ResponseEntity.ok(ResponseData.ok(profile.update(r)));
    }

    @Override
    public ResponseEntity<ResponseData<Profile>> uploadAvatar(MultipartFile file) {
        return ResponseEntity.ok(ResponseData.ok(profile.uploadAvatar(file)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> changePassword(ChangePasswordRequest r) {
        profile.changePassword(r);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<List<UserSessionRow>>> sessions() {
        return ResponseEntity.ok(ResponseData.ok(sessions.list()));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> revokeSession(long id) {
        sessions.revoke(id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<List<PersonalNotificationMatrixEntry>>> notifications() {
        return ResponseEntity.ok(ResponseData.ok(notifications.find()));
    }

    @Override
    public ResponseEntity<ResponseData<List<PersonalNotificationMatrixEntry>>> updateNotifications(
            UpdatePersonalNotificationSettingsRequest r) {
        return ResponseEntity.ok(ResponseData.ok(notifications.update(r)));
    }

    @Override
    public ResponseEntity<ResponseData<List<ScheduleSlot>>> schedule() {
        return ResponseEntity.ok(ResponseData.ok(schedule.find()));
    }

    @Override
    public ResponseEntity<ResponseData<List<ScheduleSlot>>> updateSchedule(UpdateScheduleRequest r) {
        return ResponseEntity.ok(ResponseData.ok(schedule.update(r)));
    }

    @Override
    public ResponseEntity<ResponseData<TodayStats>> todayStats() {
        return ResponseEntity.ok(ResponseData.ok(profile.todayStats()));
    }

    @Override
    public ResponseEntity<ResponseData<List<String>>> updateCallColumns(UpdateCallColumnsRequest r) {
        return ResponseEntity.ok(ResponseData.ok(profile.updateCallColumns(r)));
    }

    @Override
    public ResponseEntity<ResponseData<JsonNode>> tableConfig(String key) {
        return ResponseEntity.ok(ResponseData.ok(tableConfig.find(key)));
    }

    @Override
    public ResponseEntity<ResponseData<JsonNode>> updateTableConfig(String key, JsonNode value) {
        return ResponseEntity.ok(ResponseData.ok(tableConfig.update(key, value)));
    }
}
