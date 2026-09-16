package uz.murodjon.robotcallv2.voicemail.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.voicemail.application.dto.SaveVoicemailBoxRequest;
import uz.murodjon.robotcallv2.voicemail.application.dto.UpdateVoicemailStatusRequest;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailBox;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailBoxFilter;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailMessage;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailMessageFilter;

/**
 * Voicemail boxes (call-flow settings) and the messages left in them (the operators'
 * work list) — docs/api/voicemail.md.
 */
@RequestMapping("/api")
public interface VoicemailController {

    @PreAuthorize("hasAuthority('CALL_FLOW_EDIT')")
    @PostMapping("/voicemail-boxes")
    ResponseEntity<ResponseData<VoicemailBox>> createBox(@CurrentCompanyId long companyId,
                                                         @Valid @RequestBody SaveVoicemailBoxRequest request);

    @PreAuthorize("hasAuthority('CALL_FLOW_READ')")
    @PostMapping("/voicemail-boxes/list")
    ResponseEntity<ResponseData<PageableData<VoicemailBox>>> listBoxes(@CurrentCompanyId long companyId,
                                                                       @Valid @RequestBody VoicemailBoxFilter filter);

    @PreAuthorize("hasAuthority('CALL_FLOW_READ')")
    @GetMapping("/voicemail-boxes/{id}")
    ResponseEntity<ResponseData<VoicemailBox>> getBox(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('CALL_FLOW_EDIT')")
    @PutMapping("/voicemail-boxes/{id}")
    ResponseEntity<ResponseData<VoicemailBox>> updateBox(@CurrentCompanyId long companyId, @PathVariable long id,
                                                         @Valid @RequestBody SaveVoicemailBoxRequest request);

    @PreAuthorize("hasAuthority('CALL_FLOW_EDIT')")
    @DeleteMapping("/voicemail-boxes/{id}")
    ResponseEntity<ResponseData<Void>> deleteBox(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('MISSED_CALL_READ')")
    @PostMapping("/voicemails/list")
    ResponseEntity<ResponseData<PageableData<VoicemailMessage>>> listMessages(@CurrentCompanyId long companyId,
                                                                              @Valid @RequestBody VoicemailMessageFilter filter);

    @PreAuthorize("hasAuthority('MISSED_CALL_EDIT')")
    @PutMapping("/voicemails/{id}/status")
    ResponseEntity<ResponseData<VoicemailMessage>> updateStatus(@CurrentCompanyId long companyId, @PathVariable long id,
                                                                @Valid @RequestBody UpdateVoicemailStatusRequest request);

    /** The message audio (WAV); supports {@code Range} like a call recording. */
    @PreAuthorize("hasAuthority('MISSED_CALL_READ')")
    @GetMapping("/voicemails/{id}/audio")
    ResponseEntity<Resource> audio(@CurrentCompanyId long companyId, @PathVariable long id,
                                   @RequestHeader(value = HttpHeaders.RANGE, required = false) String range);
}
