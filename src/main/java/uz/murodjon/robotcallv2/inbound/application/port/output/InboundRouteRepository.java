package uz.murodjon.robotcallv2.inbound.application.port.output;

import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRouteFilter;

import java.util.List;

public interface InboundRouteRepository {

    /** Id, company and creation time on {@code route} are ignored — the adapter stamps them. */
    long create(long companyId, InboundRoute route);

    /** A null enum or a {@code ringTimeoutSec} of 0 keeps the route's current value. */
    void update(long companyId, long id, InboundRoute route);

    void disable(long companyId, long id);

    InboundRoute find(long companyId, long id);

    List<InboundRoute> findAll(long companyId, InboundRouteFilter filter);

    long count(long companyId, InboundRouteFilter filter);

    boolean existsEnabledByDid(String didNumber);

    boolean existsEnabledByDidExcluding(String didNumber, long id);

    InboundRoute resolveByDid(String didNumber);
}
