package uz.murodjon.robotcallv2.missedcall.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.missedcall.application.dto.AssignMissedCallRequest;
import uz.murodjon.robotcallv2.missedcall.application.dto.MissedCallCallBackResponse;
import uz.murodjon.robotcallv2.missedcall.application.dto.UpdateMissedCallRequest;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCall;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCallFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/** Callers still owed a call — the operators' work list (docs/api/missed-calls.md). */
@RequestMapping("/api/missed-calls")
public interface MissedCallController {

    @PreAuthorize("hasAuthority('MISSED_CALL_READ')")
    @PostMapping("/list")
    ResponseEntity<ResponseData<PageableData<MissedCall>>> list(@CurrentCompanyId long companyId,
                                                                @Valid @RequestBody MissedCallFilter filter);

    @PreAuthorize("hasAuthority('MISSED_CALL_READ')")
    @GetMapping("/{id}")
    ResponseEntity<ResponseData<MissedCall>> get(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('MISSED_CALL_EDIT')")
    @PutMapping("/{id}")
    ResponseEntity<ResponseData<MissedCall>> update(@CurrentCompanyId long companyId, @PathVariable long id,
                                                    @Valid @RequestBody UpdateMissedCallRequest request);

    @PreAuthorize("hasAuthority('MISSED_CALL_EDIT')")
    @PutMapping("/{id}/assign")
    ResponseEntity<ResponseData<MissedCall>> assign(@CurrentCompanyId long companyId, @PathVariable long id,
                                                    @Valid @RequestBody AssignMissedCallRequest request);

    /** Rings the current operator's softphone; when they answer, the caller is dialled. */
    @PreAuthorize("hasAuthority('MISSED_CALL_EDIT')")
    @PostMapping("/{id}/call-back")
    ResponseEntity<ResponseData<MissedCallCallBackResponse>> callBack(@CurrentCompanyId long companyId,
                                                                      @PathVariable long id);
}
