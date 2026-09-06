package uz.murodjon.robotcallv2.live.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.agent.dialog.LiveCallControlHub;
import uz.murodjon.robotcallv2.live.application.dto.LiveControlResponse;
import uz.murodjon.robotcallv2.live.application.dto.TakeoverRequest;
import uz.murodjon.robotcallv2.live.application.dto.WhisperRequest;
import uz.murodjon.robotcallv2.live.application.port.input.LiveControlUseCase;

/**
 * Operator supervision of a call that is still running: whisper guidance to the bot,
 * or a takeover that hands the channel to a human.
 */
@Service
public class LiveControlService implements LiveControlUseCase {

    private final LiveCallControlHub liveCallControlHub;

    public LiveControlService(LiveCallControlHub liveCallControlHub) {
        this.liveCallControlHub = liveCallControlHub;
    }

    @Override
    public LiveControlResponse injectWhisper(String channelId, WhisperRequest request) {
        liveCallControlHub.injectWhisper(channelId, request.instruction());
        return LiveControlResponse.whisperInjected(channelId);
    }

    @Override
    public LiveControlResponse takeoverCall(String channelId, TakeoverRequest request) {
        liveCallControlHub.takeoverCall(channelId, request == null ? null : request.extension());
        return LiveControlResponse.takeoverTriggered(channelId);
    }
}
