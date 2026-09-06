package uz.murodjon.robotcallv2.memory.application.port.input;

import uz.murodjon.robotcallv2.memory.application.dto.UpdateClientMemoryRequest;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;

import java.util.Map;

public interface ClientMemoryUseCase {

    ClientMemory findForCurrentCompany(String phone);

    ClientMemory updateForCurrentCompany(String phone, UpdateClientMemoryRequest request);

    /** Read by the call pipeline before dialing / on answer; {@code null} when there is nothing to tell. */
    ClientMemory findByCompanyIdAndPhone(long companyId, String phone);

    /**
     * Written by the post-call pipeline: prepends {@code call} and overlays {@code facts}.
     * Phones that are not real numbers ({@code MANUAL}, {@code INBOUND} placeholders) are
     * ignored.
     */
    void rememberCall(long companyId, String phone, RememberedCall call, Map<String, Object> facts);
}
