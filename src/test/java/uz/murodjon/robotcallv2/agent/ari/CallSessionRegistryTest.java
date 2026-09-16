package uz.murodjon.robotcallv2.agent.ari;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.session.CallSession;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The call lifecycle ({@link CallPhase}): whatever order StasisEnd, the setup thread, the
 * ghost reaper and the shutdown run in, a call is torn down exactly once — twice hands its
 * RTP port back twice, never leaves a dialog talking on a dead line.
 */
class CallSessionRegistryTest {

    private final CallSessionRegistry registry = new CallSessionRegistry();

    @Test
    void aCallSetUpWithoutInterruptionBecomesActive() {
        registry.markSettingUp("c1");
        assertThat(registry.isActive("c1")).isFalse();

        assertThat(registry.register(session("c1"))).isTrue();
        registry.finishSettingUp("c1");

        assertThat(registry.isActive("c1")).isTrue();
        assertThat(registry.find("c1")).isNotNull();
    }

    @Test
    void stasisEndDuringSetupLeavesTheTeardownToTheSetupThread() {
        registry.markSettingUp("c1");

        // StasisEnd: the call is still being set up, and there is nothing for it to claim.
        assertThat(registry.requestEnd("c1")).isTrue();
        assertThat(registry.claim("c1")).isNull();

        // The setup registers the session, is told the caller left, and claims it itself.
        CallSession session = session("c1");
        assertThat(registry.register(session)).isFalse();
        assertThat(registry.claim("c1")).isSameAs(session);
        assertThat(registry.find("c1")).isNull();
        assertThat(registry.isActive("c1")).isFalse();

        registry.finishSettingUp("c1");
        registry.finishEnding("c1");
        registry.markSettingUp("c1");
        assertThat(registry.requestEnd("c1")).isTrue(); // forgotten: a fresh start for the id
    }

    @Test
    void aSetupThatNeverRegisteredIsForgotten() {
        registry.markSettingUp("c1");
        registry.requestEnd("c1");

        registry.finishSettingUp("c1");

        assertThat(registry.requestEnd("c1")).isFalse();
        assertThat(registry.register(session("c1"))).isTrue();
    }

    @Test
    void stasisEndOnAnActiveCallClaimsIt() {
        registry.markSettingUp("c1");
        registry.register(session("c1"));
        registry.finishSettingUp("c1");

        assertThat(registry.requestEnd("c1")).isFalse();
        assertThat(registry.claim("c1")).isNotNull();
    }

    @Test
    void anActiveCallIsClaimedExactlyOnceUnderConcurrentCallers() throws Exception {
        int callers = 8;
        try (ExecutorService executor = Executors.newFixedThreadPool(callers)) {
            for (int round = 0; round < 200; round++) {
                String channelId = "c" + round;
                CallSession session = session(channelId);
                registry.register(session);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<CallSession>> attempts = new ArrayList<>();
                for (int i = 0; i < callers; i++) {
                    attempts.add(executor.submit(() -> {
                        start.await();
                        return registry.claim(channelId);
                    }));
                }
                start.countDown();

                List<CallSession> claimed = new ArrayList<>();
                for (Future<CallSession> attempt : attempts) {
                    CallSession result = attempt.get(5, TimeUnit.SECONDS);
                    if (result != null) {
                        claimed.add(result);
                    }
                }
                assertThat(claimed).containsExactly(session);
            }
        }
    }

    @Test
    void aClaimedCallStaysEndingUntilItsTeardownFinishes() {
        registry.register(session("c1"));
        assertThat(registry.claim("c1")).isNotNull();

        assertThat(registry.claim("c1")).isNull();
        registry.markSettingUp("c1");
        assertThat(registry.requestEnd("c1")).isFalse(); // still ENDING, not a new setup

        registry.finishEnding("c1");
        registry.markSettingUp("c1");
        assertThat(registry.requestEnd("c1")).isTrue();
    }

    @Test
    void shutdownClaimsEachLiveCallOnceAndLeavesStasisEndNothing() {
        registry.register(session("c1"));
        registry.register(session("c2"));

        List<CallSession> claimed = new ArrayList<>();
        for (CallSession live : registry.findAll()) {
            CallSession session = registry.claim(live.channelId());
            if (session != null) {
                claimed.add(session);
            }
        }

        assertThat(claimed).extracting(CallSession::channelId).containsExactlyInAnyOrder("c1", "c2");
        assertThat(registry.isEmpty()).isTrue();
        // The StasisEnd the shutdown's hangup produces.
        assertThat(registry.requestEnd("c1")).isFalse();
        assertThat(registry.claim("c1")).isNull();
    }

    @Test
    void aSessionIsReplacedOnlyWhileItsCallIsActive() {
        CallSession first = session("c1");
        registry.register(first);
        CallSession withAgent = first.withAgent(null, 9L, true);

        registry.update(withAgent);
        assertThat(registry.find("c1")).isSameAs(withAgent);

        registry.claim("c1");
        registry.update(first);
        assertThat(registry.find("c1")).isNull();
    }

    private static CallSession session(String channelId) {
        return new CallSession(channelId, 1L, channelId + "-ext", channelId + "-bridge", 10_000, null, 7L,
                Instant.now(), channelId + ".wav", "PJSIP/trunk_1_2-00000001", "trunk_1_2", 0L, null, false);
    }
}
