package uz.murodjon.robotcallv2.aiagent.domain.service;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentRollout;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Which version a caller gets during a rollout. The split is by number, not by call: the same
 * person hears the same version on every call, so a candidate is not judged on callers who
 * met both.
 */
public final class AgentRolloutRouter {

    private AgentRolloutRouter() {
    }

    /**
     * @param phone the caller's number, normalised; null (a withheld number) stays on the live version
     */
    public static boolean routesToCandidate(AgentRollout rollout, String phone) {
        if (rollout == null || phone == null || phone.isBlank()) {
            return false;
        }
        if (rollout.stagingPhones().contains(phone)) {
            return true;
        }
        return bucketOf(rollout.agentId(), phone) < rollout.trafficPercent();
    }

    /** 0–99, stable for an agent and a number; salted with the agent so every rollout splits differently. */
    static int bucketOf(long agentId, String phone) {
        CRC32 crc = new CRC32();
        crc.update((agentId + ":" + phone).getBytes(StandardCharsets.UTF_8));
        return (int) (crc.getValue() % 100);
    }
}
