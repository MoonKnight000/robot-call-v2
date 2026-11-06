package uz.murodjon.robotcallv2.memory.application.port.input;

import uz.murodjon.robotcallv2.memory.application.dto.UpdateClientMemoryRequest;
import uz.murodjon.robotcallv2.memory.application.dto.UpdateRememberedCallRequest;
import uz.murodjon.robotcallv2.memory.application.dto.UpsertClientMemoryFactRequest;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.MemorySource;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;

import java.time.Instant;
import java.util.Map;

public interface ClientMemoryUseCase {

    /** 404 when nothing is remembered — the operator asked for a specific client. */
    ClientMemory requireByCompanyIdAndPhone(long companyId, String phone);

    ClientMemory updateByCompanyIdAndPhone(long companyId, String phone, UpdateClientMemoryRequest request);

    /** An operator sets one fact by hand; it is recorded as theirs. */
    ClientMemory upsertFactByCompanyIdAndPhone(long companyId, String phone, String key,
                                               UpsertClientMemoryFactRequest request);

    /** An operator removes one fact; 404 when it is not there. */
    ClientMemory deleteFactByCompanyIdAndPhone(long companyId, String phone, String key);

    /** An operator corrects the summary of one remembered conversation; 404 when it is not there. */
    ClientMemory updateCallByCompanyIdAndPhone(long companyId, String phone, String callId,
                                               UpdateRememberedCallRequest request);

    /** An operator removes one remembered conversation; 404 when it is not there. */
    ClientMemory deleteCallByCompanyIdAndPhone(long companyId, String phone, String callId);

    /** Forgets everything about the client — notes, conversations and facts; 404 when nothing was remembered. */
    void deleteByCompanyIdAndPhone(long companyId, String phone);

    /** Read by the call pipeline before dialing / on answer; {@code null} when there is nothing to tell. */
    ClientMemory findByCompanyIdAndPhone(long companyId, String phone);

    /**
     * Written by the post-call pipeline: prepends {@code call} and overlays {@code facts}, each recorded as
     * coming from that call (or chat). Phones that are not real numbers ({@code MANUAL}, {@code INBOUND}
     * placeholders) are ignored.
     */
    void rememberCall(long companyId, String phone, RememberedCall call, Map<String, Object> facts);

    /**
     * Written mid-conversation by the agent's tools: overlays {@code facts} — the flats and
     * products a client asked about or liked — without adding a remembered call, each recorded as
     * coming from {@code source}. A blank value is ignored; phones that are not real numbers are ignored.
     */
    void rememberFacts(long companyId, String phone, Map<String, Object> facts, MemorySource source);

    /**
     * The retention sweep: deletes up to {@code batchSize} memories, across every company,
     * that no call or operator has written since {@code cutoff}; returns how many went.
     */
    int deleteNotUpdatedSince(Instant cutoff, int batchSize);
}
