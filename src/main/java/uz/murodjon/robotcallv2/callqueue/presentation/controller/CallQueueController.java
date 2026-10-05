package uz.murodjon.robotcallv2.callqueue.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import uz.murodjon.robotcallv2.callqueue.application.dto.CallQueueLiveRow;
import uz.murodjon.robotcallv2.callqueue.application.dto.SaveCallQueueRequest;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Operator queues and their members, and the live wallboard (docs/api/call-queues.md).
 */
@RequestMapping("/api/call-queues")
public interface CallQueueController {

    @PreAuthorize("hasAuthority('CALL_QUEUE_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<CallQueue>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody SaveCallQueueRequest request);

    @PreAuthorize("hasAuthority('CALL_QUEUE_READ')")
    @PostMapping("/list")
    ResponseEntity<ResponseData<PageableData<CallQueue>>> list(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CallQueueFilter filter);

    @PreAuthorize("hasAuthority('CALL_QUEUE_READ')")
    @GetMapping("/{id}")
    ResponseEntity<ResponseData<CallQueue>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('CALL_QUEUE_EDIT')")
    @PutMapping("/{id}")
    ResponseEntity<ResponseData<CallQueue>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody SaveCallQueueRequest request);

    @PreAuthorize("hasAuthority('CALL_QUEUE_EDIT')")
    @DeleteMapping("/{id}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('CALL_QUEUE_READ')")
    @GetMapping("/live")
    ResponseEntity<ResponseData<List<CallQueueLiveRow>>> live(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('CALL_QUEUE_READ')")
    @GetMapping(value = "/live/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter liveStream(@CurrentCompanyId long companyId);
}
