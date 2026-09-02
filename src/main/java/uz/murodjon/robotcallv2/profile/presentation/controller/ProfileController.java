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
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Self-service "Profilim" page (API-REQUIREMENTS §15, UI-DESIGN §8.3).
 */
@RequestMapping("/api/profile")
public interface ProfileController {

    @GetMapping
    ResponseEntity<ResponseData<Profile>> get();

    @PutMapping
    ResponseEntity<ResponseData<Profile>> update(@Valid @RequestBody UpdateProfileRequest r);

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ResponseData<Profile>> uploadAvatar(@RequestParam("file") MultipartFile file);

    @PutMapping("/password")
    ResponseEntity<ResponseData<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest r);

    @GetMapping("/sessions")
    ResponseEntity<ResponseData<List<UserSessionRow>>> sessions();

    @DeleteMapping("/sessions/{id}")
    ResponseEntity<ResponseData<Void>> revokeSession(@PathVariable long id);

    @GetMapping("/notifications")
    ResponseEntity<ResponseData<List<PersonalNotificationMatrixEntry>>> notifications();

    @PutMapping("/notifications")
    ResponseEntity<ResponseData<List<PersonalNotificationMatrixEntry>>> updateNotifications(
            @Valid @RequestBody UpdatePersonalNotificationSettingsRequest r);

    @GetMapping("/schedule")
    ResponseEntity<ResponseData<List<ScheduleSlot>>> schedule();

    @PutMapping("/schedule")
    ResponseEntity<ResponseData<List<ScheduleSlot>>> updateSchedule(@Valid @RequestBody UpdateScheduleRequest r);

    @GetMapping("/today-stats")
    ResponseEntity<ResponseData<TodayStats>> todayStats();

    @PutMapping("/call-columns")
    ResponseEntity<ResponseData<List<String>>> updateCallColumns(@Valid @RequestBody UpdateCallColumnsRequest r);

    @GetMapping("/table-config/{key}")
    ResponseEntity<ResponseData<JsonNode>> tableConfig(@PathVariable String key);

    @PutMapping("/table-config/{key}")
    ResponseEntity<ResponseData<JsonNode>> updateTableConfig(@PathVariable String key, @RequestBody(required = false) JsonNode value);
}
