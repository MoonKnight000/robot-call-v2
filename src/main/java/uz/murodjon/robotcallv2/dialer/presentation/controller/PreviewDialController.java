package uz.murodjon.robotcallv2.dialer.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.dialer.application.dto.PreviewCardResponse;
import uz.murodjon.robotcallv2.dialer.application.dto.PreviewDialResponse;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * A PREVIEW campaign from the operator's screen (docs/api/preview-dialing.md): take the next
 * client's card, then dial it or put it back. The call is closed with
 * {@code POST /api/calls/{callId}/wrap-up}, which also settles the card.
 */
@RequestMapping("/api/operator/campaigns/{campaignId}")
public interface PreviewDialController {

    /** The card the operator holds, or a new one; {@code data} is null when no client is due now. */
    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping("/next")
    ResponseEntity<ResponseData<PreviewCardResponse>> next(
            @CurrentCompanyId long companyId,
            @PathVariable long campaignId);

    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping("/targets/{targetId}/dial")
    ResponseEntity<ResponseData<PreviewDialResponse>> dial(
            @CurrentCompanyId long companyId,
            @PathVariable long campaignId,
            @PathVariable long targetId);

    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping("/targets/{targetId}/skip")
    ResponseEntity<ResponseData<Void>> skip(
            @CurrentCompanyId long companyId,
            @PathVariable long campaignId,
            @PathVariable long targetId);
}
