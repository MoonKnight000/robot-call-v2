package uz.murodjon.robotcallv2.callqueue.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import uz.murodjon.robotcallv2.callqueue.application.dto.CallQueueLiveRow;
import uz.murodjon.robotcallv2.callqueue.application.dto.SaveCallQueueRequest;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueLiveUseCase;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueUseCase;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.shared.api.SseEventStreams;

import java.util.List;

@RestController
public class CallQueueControllerImpl implements CallQueueController {

    private final CallQueueUseCase callQueueUseCase;
    private final CallQueueLiveUseCase callQueueLiveUseCase;

    public CallQueueControllerImpl(CallQueueUseCase callQueueUseCase, CallQueueLiveUseCase callQueueLiveUseCase) {
        this.callQueueUseCase = callQueueUseCase;
        this.callQueueLiveUseCase = callQueueLiveUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<CallQueue>> create(long companyId, SaveCallQueueRequest request) {
        return ResponseEntity.ok(ResponseData.ok(callQueueUseCase.create(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CallQueue>>> list(long companyId, CallQueueFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(callQueueUseCase.list(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<CallQueue>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(callQueueUseCase.requireQueue(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<CallQueue>> update(long companyId, long id, SaveCallQueueRequest request) {
        return ResponseEntity.ok(ResponseData.ok(callQueueUseCase.update(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        callQueueUseCase.delete(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<List<CallQueueLiveRow>>> live(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(callQueueLiveUseCase.findLive(companyId)));
    }

    @Override
    public SseEmitter liveStream(long companyId) {
        return SseEventStreams.open(sink -> callQueueLiveUseCase.subscribe(companyId, sink));
    }
}
