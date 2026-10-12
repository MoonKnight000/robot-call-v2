package uz.murodjon.robotcallv2.agent.ari;

import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.dialog.*;
import uz.murodjon.robotcallv2.agent.rtp.*;
import uz.murodjon.robotcallv2.agent.tts.TtsRouter;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallTranscriptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Plays an agent's test case as a real call: the persona's lines are synthesized, put under
 * background noise and sent as RTP into a real {@link RtpEndpoint}, which runs the call's
 * whole audio chain — VAD, speech gate, recognizer, turn detection, barge-in — and answers
 * through the bot's own TTS (VOICE-PIPELINE-PLAN Bosqich 5).
 *
 * <p><b>Why.</b> {@link AgentSimulator} hands the agent clean text. A caller's words never
 * arrive that way: they come through a recognizer that mishears an amount on a noisy line,
 * and an endpointer that cuts a sentence in half. Those failures are where real calls go
 * wrong, and only this mode can catch them before a customer does.
 *
 * <p>No Asterisk: the simulator is the far end of the RTP leg itself, on loopback. The first
 * packet it sends latches the endpoint's peer, exactly as externalMedia's would. Company
 * REST and MCP tools are not offered ({@link DialogEngine#startSimulatedCall}), and no
 * teardown runs — no billing, CRM, webhook or summary for a test.
 */
@Service
public class VoiceCallSimulator {

    private static final Logger log = LoggerFactory.getLogger(VoiceCallSimulator.class);

    /** Phone recorded on a simulated call's attempt row — not a number, like {@code WEB-TEST}. */
    private static final String SIM_PHONE = "VOICE-SIM";
    private static final long POLL_MS = 50;

    private final RtpPortAllocator portAllocator;
    private final RtpProperties rtpProperties;
    private final EventLoopGroup rtpEventLoopGroup;
    private final AudioPipelineAssembler audioPipelineAssembler;
    private final AriEventLanes ariEventLanes;
    private final DialogEngine dialogEngine;
    private final AgentSimulator agentSimulator;
    private final TtsRouter ttsRouter;
    private final CallAttemptUseCase callAttemptUseCase;
    private final CallTranscriptUseCase callTranscriptUseCase;
    private final VoiceSimulationProperties voiceSimulationProperties;

    VoiceCallSimulator(RtpPortAllocator portAllocator, RtpProperties rtpProperties, EventLoopGroup rtpEventLoopGroup,
                       AudioPipelineAssembler audioPipelineAssembler, AriEventLanes ariEventLanes,
                       DialogEngine dialogEngine, AgentSimulator agentSimulator, TtsRouter ttsRouter,
                       CallAttemptUseCase callAttemptUseCase, CallTranscriptUseCase callTranscriptUseCase,
                       VoiceSimulationProperties voiceSimulationProperties) {
        this.portAllocator = portAllocator;
        this.rtpProperties = rtpProperties;
        this.rtpEventLoopGroup = rtpEventLoopGroup;
        this.audioPipelineAssembler = audioPipelineAssembler;
        this.ariEventLanes = ariEventLanes;
        this.dialogEngine = dialogEngine;
        this.agentSimulator = agentSimulator;
        this.ttsRouter = ttsRouter;
        this.callAttemptUseCase = callAttemptUseCase;
        this.callTranscriptUseCase = callTranscriptUseCase;
        this.voiceSimulationProperties = voiceSimulationProperties;
    }

    /**
     * One whole call, the same contract as {@link AgentSimulator#simulate}. The transcript is
     * the call's own — what the recognizer heard, not what the persona said — because that
     * is what the agent answered.
     */
    public SimulatedCall simulate(long companyId, ScenarioDefinition scenario, AiAgent agent, String language,
                                  SimulationPersona persona, int maxTurns, String personaModel) {
        if (!dialogEngine.available() || !agentSimulator.available()) {
            return failed("no LLM ChatModel is configured — the voice simulation needs one for each side");
        }
        String channelId = "voice-sim-" + UUID.randomUUID();
        long attemptId = callAttemptUseCase.startAttempt(companyId, CallAttempt.NO_TARGET, channelId, SIM_PHONE,
                language, null, null);
        if (attemptId == 0) {
            return failed("could not open a call attempt for the simulation");
        }
        callAttemptUseCase.markAnswered(attemptId);
        Instant startedAt = Instant.now();
        List<String> problems = new ArrayList<>();
        Disposition disposition = null;
        String stage = null;
        int port = -1;
        RtpEndpoint endpoint = null;
        CallerLeg caller = null;
        try {
            port = portAllocator.allocate();
            endpoint = new RtpEndpoint(channelId, port, rtpProperties.codec(), null, List.of(),
                    rtpProperties.receiveQueueCapacity(), rtpProperties.maxQueuedPlaybackSeconds());
            endpoint.bind(rtpEventLoopGroup);
            CallContext context = new CallContext(persona.facts(), null);
            endpoint.replaceListeners(audioPipelineAssembler.buildAudioListeners(channelId, companyId, attemptId,
                    startedAt, language, null, context, agent, endpoint::recentOutboundLevel));
            // The caller's leg is cleaned as a real call's is — the noise added below is
            // there to test exactly this.
            if (agent != null) {
                endpoint.setNoiseCancellation(agent.ambience().noiseCancellationEnabled(),
                        agent.ambience().noiseCancellationMode());
            }
            audioPipelineAssembler.attachDenoiser(endpoint);
            // Silence from the first moment, as a phone line carries: it latches the
            // endpoint's peer, and without a steady stream the VAD has no windows to close
            // the caller's utterances on.
            caller = new CallerLeg(port, rtpProperties.codec(), voiceSimulationProperties.snrDb());
            caller.start();

            AtomicBoolean hungUp = new AtomicBoolean();
            dialogEngine.startSimulatedCall(new CallStart(channelId, endpoint, context, scenario, language,
                    agent != null ? agent.voiceFor(language) : null, agent,
                    () -> hungUp.set(true), () -> hungUp.set(true), attemptId, null));
            DialogSession session = dialogEngine.findSession(channelId);
            if (session == null) {
                problems.add("the dialog did not start");
            } else {
                converse(session, endpoint, caller, hungUp, persona, language, maxTurns, personaModel, problems);
                disposition = dialogEngine.outcome(channelId).disposition();
                stage = session.state();
            }
        } catch (Exception e) {
            log.warn("[{}] voice simulation failed: {}", channelId, e.getMessage());
            problems.add("error: " + e.getMessage());
        } finally {
            dialogEngine.endCall(channelId);
            if (caller != null) {
                caller.close();
            }
            if (endpoint != null) {
                endpoint.close();
            }
            if (port >= 0) {
                portAllocator.release(port);
            }
        }
        List<String> transcript = transcriptOf(channelId, attemptId);
        callAttemptUseCase.finishAttempt(attemptId, disposition, null,
                (int) Duration.between(startedAt, Instant.now()).toSeconds());
        return new SimulatedCall(transcript, disposition, stage, problems);
    }

    /** Bot speaks, persona answers out loud, until the call ends or runs out of turns. */
    private void converse(DialogSession session, RtpEndpoint endpoint, CallerLeg caller, AtomicBoolean hungUp,
                          SimulationPersona persona, String language, int maxTurns, String personaModel,
                          List<String> problems) throws InterruptedException {
        List<Message> callerHistory = new ArrayList<>();
        long playedBeforeBot = 0;
        for (int turn = 1; turn <= maxTurns; turn++) {
            boolean answered = awaitBotTurn(session, endpoint, hungUp, playedBeforeBot);
            if (hungUp.get() || session.isEnded()) {
                return;
            }
            if (!answered) {
                problems.add("turn " + turn + ": the agent did not answer within "
                        + voiceSimulationProperties.turnTimeoutMs() + " ms");
                return;
            }
            String agentLine = session.lastAgentText();
            callerHistory.add(new AssistantMessage(agentLine != null ? agentLine : ""));
            String reply = agentSimulator.callerReply(persona, callerHistory, personaModel);
            if (reply == null || reply.isBlank()) {
                problems.add("turn " + turn + ": the caller said nothing");
                return;
            }
            callerHistory.add(new UserMessage(reply));
            short[] speech = ttsRouter.synthesize(reply, language, blankToNull(voiceSimulationProperties.personaVoice()));
            caller.say(speech);
            playedBeforeBot = endpoint.playedSamples();
        }
    }

    /**
     * Waits for the bot to say something after {@code playedBefore} and then fall quiet for
     * {@code botQuietMs}. Measured on the audio actually played, not on the text: the bot
     * repeating itself ("qaytaring") is a turn too, and the persona must hear it out.
     *
     * @return whether the bot finished a turn; false on the timeout or a hang-up
     */
    private boolean awaitBotTurn(DialogSession session, RtpEndpoint endpoint, AtomicBoolean hungUp,
                                 long playedBefore) throws InterruptedException {
        long deadline = System.currentTimeMillis() + voiceSimulationProperties.turnTimeoutMs();
        long quietSince = -1;
        while (System.currentTimeMillis() < deadline) {
            if (hungUp.get() || session.isEnded()) {
                return false;
            }
            boolean spoke = endpoint.playedSamples() > playedBefore;
            if (endpoint.isPlaying() || !spoke) {
                quietSince = -1;
            } else if (quietSince < 0) {
                quietSince = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - quietSince >= voiceSimulationProperties.botQuietMs()) {
                return true;
            }
            Thread.sleep(POLL_MS);
        }
        return false;
    }

    /**
     * The call's transcript as stored. Caller lines are written on the channel's event lane,
     * so the lane is drained first — the lane runs in order, so once this task has run every
     * line queued before it is in.
     */
    private List<String> transcriptOf(String channelId, long attemptId) {
        CountDownLatch drained = new CountDownLatch(1);
        ariEventLanes.execute(channelId, drained::countDown);
        try {
            drained.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String text = callTranscriptUseCase.findTranscriptText(attemptId);
        return text.isBlank() ? List.of() : Arrays.stream(text.split("\n")).filter(l -> !l.isBlank()).toList();
    }

    private static SimulatedCall failed(String problem) {
        return new SimulatedCall(List.of(), null, null, List.of(problem));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * The far end of the RTP leg: a 20 ms clock that sends the persona's queued speech or,
     * between lines, silence — both under the same background noise, as a real line would
     * carry. What comes back is drained and dropped; the bot's turns are read off the
     * endpoint itself.
     */
    private static final class CallerLeg implements AutoCloseable {

        private static final int FRAME = 160;
        private static final long FRAME_NANOS = 20_000_000L;
        /** Assumed speech level until the first line says otherwise — a typical TTS RMS. */
        private static final double DEFAULT_SPEECH_RMS = 2000;

        private final DatagramSocket socket;
        private final InetSocketAddress target;
        private final RtpCodec codec;
        private final double snrDb;
        private final ConcurrentLinkedQueue<short[]> frames = new ConcurrentLinkedQueue<>();
        private final Random random = new Random();
        private final long ssrc = random.nextInt() & 0xFFFFFFFFL;
        private volatile boolean running = true;
        private volatile double noiseRms;
        private int sequence;
        private long timestamp;

        CallerLeg(int endpointPort, RtpCodec codec, double snrDb) throws IOException {
            this.socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
            this.target = new InetSocketAddress(InetAddress.getLoopbackAddress(), endpointPort);
            this.codec = codec;
            this.snrDb = snrDb;
            this.noiseRms = noiseFor(DEFAULT_SPEECH_RMS);
        }

        void start() {
            Thread.ofVirtual().name("voice-sim-send").start(this::send);
            Thread.ofVirtual().name("voice-sim-receive").start(this::drain);
        }

        /** Queues one line and returns once all of it has gone out on the wire. */
        void say(short[] pcm) throws InterruptedException {
            if (pcm == null || pcm.length == 0) {
                return;
            }
            double sum = 0;
            for (short s : pcm) {
                sum += (double) s * s;
            }
            noiseRms = noiseFor(Math.sqrt(sum / pcm.length));
            for (int offset = 0; offset < pcm.length; offset += FRAME) {
                frames.add(Arrays.copyOfRange(pcm, offset, Math.min(pcm.length, offset + FRAME)));
            }
            while (running && !frames.isEmpty()) {
                Thread.sleep(FRAME_NANOS / 1_000_000);
            }
        }

        private double noiseFor(double speechRms) {
            return snrDb > 0 ? speechRms / Math.pow(10, snrDb / 20) : 0;
        }

        private void send() {
            long next = System.nanoTime();
            while (running) {
                short[] speech = frames.poll();
                byte[] payload = new byte[FRAME];
                double noise = noiseRms;
                for (int i = 0; i < FRAME; i++) {
                    double sample = (speech != null && i < speech.length ? speech[i] : 0)
                            + (noise > 0 ? random.nextGaussian() * noise : 0);
                    payload[i] = codec.encode((short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample)));
                }
                byte[] packet = RtpPacket.toBytes(codec.payloadType(), sequence++ & 0xFFFF, timestamp, ssrc,
                        false, payload);
                timestamp += FRAME;
                try {
                    socket.send(new DatagramPacket(packet, packet.length, target));
                } catch (IOException e) {
                    if (running) {
                        log.debug("voice simulation send failed: {}", e.getMessage());
                    }
                }
                next += FRAME_NANOS;
                LockSupport.parkNanos(next - System.nanoTime());
            }
        }

        private void drain() {
            byte[] buffer = new byte[2048];
            while (running) {
                try {
                    socket.receive(new DatagramPacket(buffer, buffer.length));
                } catch (IOException e) {
                    return; // closed
                }
            }
        }

        @Override
        public void close() {
            running = false;
            socket.close();
        }
    }
}
