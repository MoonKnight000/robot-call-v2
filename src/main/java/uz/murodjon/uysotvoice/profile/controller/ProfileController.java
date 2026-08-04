package uz.murodjon.uysotvoice.profile.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import uz.murodjon.uysotvoice.auth.dto.UserSession;
import uz.murodjon.uysotvoice.profile.dto.ChangePasswordRequest;
import uz.murodjon.uysotvoice.profile.dto.PersonalNotificationMatrixEntry;
import uz.murodjon.uysotvoice.profile.dto.Profile;
import uz.murodjon.uysotvoice.profile.dto.ScheduleSlot;
import uz.murodjon.uysotvoice.profile.dto.TodayStats;
import uz.murodjon.uysotvoice.profile.dto.UpdateCallColumnsRequest;
import uz.murodjon.uysotvoice.profile.dto.UpdatePersonalNotificationSettingsRequest;
import uz.murodjon.uysotvoice.profile.dto.UpdateProfileRequest;
import uz.murodjon.uysotvoice.profile.dto.UpdateScheduleRequest;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

import java.util.List;

/**
 * Self-service "Profilim" page (API-REQUIREMENTS §15, UI-DESIGN §8.3) — every endpoint
 * acts on the caller's own {@code app_user} row only, resolved via {@code CurrentUser}
 * (JWT), never a path id. Any logged-in role (authenticated() in {@code SecurityConfig}).
 */
@RequestMapping("/api/profile")
public interface ProfileController {

    @GetMapping
    ResponseEntity<ResponseData<Profile>> get();

    @PutMapping
    ResponseEntity<ResponseData<Profile>> update(@Valid @RequestBody UpdateProfileRequest r);

    /**
     * Avatar upload (report #11) — the only way {@code avatarFileId} changes; image-
     * only, 5 MB max, served back via {@code GET /api/files/{id}}.
     */
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ResponseData<Profile>> uploadAvatar(@RequestParam("file") MultipartFile file);

    @PutMapping("/password")
    ResponseEntity<ResponseData<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest r);

    @GetMapping("/sessions")
    ResponseEntity<ResponseData<List<UserSession>>> sessions();

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

    /**
     * "Ustunlar ⚙" tanlovi (§10.4/API-REQUIREMENTS §4) — qo'ng'iroqlar jadvalidagi ustun
     * tanlovini profilga saqlaydi, {@code GET}dagi {@code Profile.callColumns} bilan bir xil.
     */
    @PutMapping("/call-columns")
    ResponseEntity<ResponseData<List<String>>> updateCallColumns(@Valid @RequestBody UpdateCallColumnsRequest r);

    /**
     * Generic per-table UI preference (backend-uchun-talablar.md §1) — {@code key} is a
     * frontend-owned namespace (e.g. {@code "callsTableColumns"},
     * {@code "campaignsTableColumns"}); the JSON shape under it is the frontend's own
     * concern too, the backend only stores and returns it as-is. {@code null}/absent if
     * never saved for this key.
     */
    @GetMapping("/table-config/{key}")
    ResponseEntity<ResponseData<JsonNode>> tableConfig(@PathVariable String key);

    /** Saving {@code null} (or omitting the body) clears the saved value for {@code key}. */
    @PutMapping("/table-config/{key}")
    ResponseEntity<ResponseData<JsonNode>> updateTableConfig(@PathVariable String key, @RequestBody(required = false) JsonNode value);
}
