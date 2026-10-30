package uz.murodjon.robotcallv2.webhook.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.ari.LiveKitCallService;
import uz.murodjon.robotcallv2.agent.livekit.LiveKitTelephonyService;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.webhook.application.dto.LiveKitBridgeDtmf;
import uz.murodjon.robotcallv2.webhook.application.port.input.LiveKitBridgeUseCase;

import java.io.IOException;

/** Implements {@link LiveKitBridgeUseCase}. */
@Service
public class LiveKitBridgeService implements LiveKitBridgeUseCase {

    private final LiveKitTelephonyService liveKitTelephonyService;
    private final LiveKitCallService liveKitCallService;
    private final ObjectMapper objectMapper;

    public LiveKitBridgeService(LiveKitTelephonyService liveKitTelephonyService,
                                LiveKitCallService liveKitCallService, ObjectMapper objectMapper) {
        this.liveKitTelephonyService = liveKitTelephonyService;
        this.liveKitCallService = liveKitCallService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handleDtmf(String signature, byte[] body) {
        liveKitTelephonyService.verifyBridgeSignature(signature, body);
        LiveKitBridgeDtmf event;
        try {
            event = objectMapper.readValue(body, LiveKitBridgeDtmf.class);
        } catch (IOException e) {
            throw new ValidationException(ErrorCode.LIVEKIT_WEBHOOK_PAYLOAD_INVALID);
        }
        if (event.room() == null || event.digit() == null || event.digit().length() != 1) {
            throw new ValidationException(ErrorCode.LIVEKIT_WEBHOOK_PAYLOAD_INVALID);
        }
        liveKitCallService.onCallerDtmf(event.room(), event.digit().charAt(0));
    }
}
