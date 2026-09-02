package uz.murodjon.robotcallv2.siptrunk.application.port.output;

import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkFilter;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunk;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.SipTrunkTransport;

import java.util.Collection;
import java.util.List;

public interface SipTrunkRepository {

    long create(String name, String pjsipEndpoint, String callerId, boolean makeDefault,
                String host, int port, String sipUsername, String sipPasswordEnc,
                SipTrunkTransport transport, List<String> codecs);

    SipTrunk find(long id);

    boolean hasDefault();

    void update(long id, String name, String pjsipEndpoint, String callerId, boolean enabled,
                String host, int port, String sipUsername, String sipPasswordEnc,
                SipTrunkTransport transport, List<String> codecs);

    void updatePjsipEndpoint(long id, String pjsipEndpoint);

    void makeDefault(long id);

    void delete(long id);

    List<SipTrunk> findAll(SipTrunkFilter filter);

    long count(SipTrunkFilter filter);

    SipTrunk findDefaultForCompany(long companyId);

    List<SipTrunk> findAllEnabledByCompany(long companyId);

    List<SipTrunk> findEnabledByIdsAndCompany(Collection<Long> ids, long companyId);

    List<SipTrunk> findAllManagedEnabled();
}
