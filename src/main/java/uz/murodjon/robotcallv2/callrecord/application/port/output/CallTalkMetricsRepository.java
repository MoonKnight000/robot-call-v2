package uz.murodjon.robotcallv2.callrecord.application.port.output;

import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTalkMetrics;

public interface CallTalkMetricsRepository {

    /** Replaces the call's row if it has one; false when the call itself no longer exists. */
    boolean upsert(CallTalkMetrics metrics);

    /** Null when no operator was on the call. */
    CallTalkMetrics findByCallId(long callId);
}
