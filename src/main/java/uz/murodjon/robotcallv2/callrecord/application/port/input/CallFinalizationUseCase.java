package uz.murodjon.robotcallv2.callrecord.application.port.input;

import uz.murodjon.robotcallv2.agent.dialog.DialogTechnicalSnapshot;
import uz.murodjon.robotcallv2.agent.rtp.RtpQuality;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.nio.file.Path;
import java.time.Instant;

/** The post-call pipeline, run once a call ends: recording, summary, CRM, billing. */
public interface CallFinalizationUseCase {

    /**
     * @param agent        the agent that ran the call, or null when none did
     * @param phone        the number the call talked to, or null when unknown
     * @param mediaQuality the call's inbound audio quality, stored on the "Texnik" tab; null
     *                     when the call had no media to measure
     * @return the disposition the call actually settled on — the one passed in, unless the
     *         summary found an outcome the dialog never got to record
     */
    Disposition finalizeCall(long callAttemptId, long clientId, long scenarioId, Path wav, Instant startedAt,
                             Disposition disposition, String channelName, String trunk,
                             DialogTechnicalSnapshot technical, AiAgent agent, String phone,
                             RtpQuality mediaQuality);
}
