package uz.murodjon.robotcallv2.profile.presentation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.murodjon.robotcallv2.auth.application.dto.UserSessionRow;
import uz.murodjon.robotcallv2.profile.application.dto.*;
import uz.murodjon.robotcallv2.profile.domain.entity.PersonalNotificationMatrixEntry;
import uz.murodjon.robotcallv2.profile.domain.entity.Profile;
import uz.murodjon.robotcallv2.profile.domain.entity.ScheduleSlot;
import uz.murodjon.robotcallv2.profile.domain.entity.TodayStats;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Self-service "Profilim" page (API-REQUIREMENTS §15, UI-DESIGN §8.3).
 */
@RequestMapping("/api/profile")
public interface ProfileController {

    @GetMapping
    ResponseEntity<ResponseData<Profile>> get(@CurrentCompanyId long companyId);

    @PutMapping
    ResponseEntity<ResponseData<Profile>> update(@CurrentCompanyId long companyId,
                                                 @Valid @RequestBody UpdateProfileRequest request);

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ResponseData<Profile>> uploadAvatar(@CurrentCompanyId long companyId,
                                                       @RequestParam("file") MultipartFile file);

    @PutMapping("/password")
    ResponseEntity<ResponseData<Void>> changePassword(@CurrentCompanyId long companyId,
                                                      @Valid @RequestBody ChangePasswordRequest request);

    @GetMapping("/sessions")
    ResponseEntity<ResponseData<List<UserSessionRow>>> sessions();

    @DeleteMapping("/sessions/{id}")
    ResponseEntity<ResponseData<Void>> revokeSession(@CurrentCompanyId long companyId, @PathVariable long id);

    @GetMapping("/notifications")
    ResponseEntity<ResponseData<List<PersonalNotificationMatrixEntry>>> notifications();

    @PutMapping("/notifications")
    ResponseEntity<ResponseData<List<PersonalNotificationMatrixEntry>>> updateNotifications(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody UpdatePersonalNotificationSettingsRequest request);

    @GetMapping("/schedule")
    ResponseEntity<ResponseData<List<ScheduleSlot>>> schedule();

    @PutMapping("/schedule")
    ResponseEntity<ResponseData<List<ScheduleSlot>>> updateSchedule(@CurrentCompanyId long companyId,
            @Valid @RequestBody UpdateScheduleRequest request);

    @GetMapping("/today-stats")
    ResponseEntity<ResponseData<TodayStats>> todayStats(@CurrentCompanyId long companyId);

    @PutMapping("/call-columns")
    ResponseEntity<ResponseData<List<String>>> updateCallColumns(@CurrentCompanyId long companyId,
            @Valid @RequestBody UpdateCallColumnsRequest request);

    @GetMapping("/table-config/{key}")
    ResponseEntity<ResponseData<JsonNode>> tableConfig(@PathVariable String key);

    @PutMapping("/table-config/{key}")
    ResponseEntity<ResponseData<JsonNode>> updateTableConfig(@CurrentCompanyId long companyId,
            @PathVariable String key, @RequestBody(required = false) JsonNode value);
}
