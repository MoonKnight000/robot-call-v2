package uz.murodjon.robotcallv2.siptrunk.application.port.output;

import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkFilter;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunk;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.SipTrunkTransport;

import java.util.Collection;
import java.util.List;

public interface SipTrunkRepository {

    long create(long companyId, String name, String pjsipEndpoint, String callerId, boolean makeDefault,
                String host, int port, String sipUsername, String sipPasswordEnc,
                SipTrunkTransport transport, List<String> codecs);

    SipTrunk find(long companyId, long id);

    boolean hasDefault(long companyId);

    void update(long companyId, long id, String name, String pjsipEndpoint, String callerId, boolean enabled,
                String host, int port, String sipUsername, String sipPasswordEnc,
                SipTrunkTransport transport, List<String> codecs);

    void updatePjsipEndpoint(long companyId, long id, String pjsipEndpoint);

    void makeDefault(long companyId, long id);

    void delete(long companyId, long id);

    List<SipTrunk> findAll(long companyId, SipTrunkFilter filter);

    long count(long companyId, SipTrunkFilter filter);

    SipTrunk findDefaultForCompany(long companyId);

    List<SipTrunk> findAllEnabledByCompany(long companyId);

    List<SipTrunk> findEnabledByIdsAndCompany(Collection<Long> ids, long companyId);

    List<SipTrunk> findAllManagedEnabled();
}
