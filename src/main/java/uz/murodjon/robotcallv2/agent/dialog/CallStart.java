package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.DialogPhrases;

import java.util.function.Consumer;

/**
 * Everything either dialog engine needs to start talking on a call
 * ({@link DialogEngine#startCall}, {@link RealtimeDialogEngine#startCall}).
 *
 * @param ttsVoice      the agent's voice for this call's language, or null for the
 *                      company default
 * @param agent         who is speaking — voice per language, persona, and this agent's own
 *                      model settings over the company's. Null on a manual test call, which
 *                      runs a scenario with no agent behind it and takes the defaults
 * @param hangup        what ending the conversation does to the call
 * @param transfer      what asking for a person does to the call
 * @param dtmf          how this call presses keypad tones for IVR navigation, or null when
 *                      it cannot — the {@code sendDtmfTones} tool then tells the model so
 */
public record CallStart(String channelId,
                        RtpEndpoint endpoint,
                        CallContext context,
                        ScenarioDefinition scenario,
                        String language,
                        String ttsVoice,
                        AiAgent agent,
                        Runnable hangup,
                        Runnable transfer,
                        long callAttemptId,
                        Consumer<String> dtmf) {

    /**
     * The same call resumed on the cascade pipeline after the realtime engine dropped: the
     * agent opens with an apology for the dropped line instead of a fresh greeting, and the
     * voice is left to the company default — an agent set up for a realtime engine holds
     * that engine's voice, which no synthesizer can speak.
     */
    public CallStart withReconnectingAgent() {
        AiAgent reconnecting = agent == null ? null : agent.withSpokenOpening(DialogPhrases.reconnected(language));
        return new CallStart(channelId, endpoint, context, scenario, language, null, reconnecting,
                hangup, transfer, callAttemptId, dtmf);
    }

    /** The same call on the cascade pipeline as the agent it started with, in the company default voice. */
    public CallStart withDefaultVoice() {
        return new CallStart(channelId, endpoint, context, scenario, language, null, agent,
                hangup, transfer, callAttemptId, dtmf);
    }
}
