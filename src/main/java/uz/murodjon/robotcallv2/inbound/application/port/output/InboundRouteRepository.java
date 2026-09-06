package uz.murodjon.robotcallv2.inbound.application.port.output;

import uz.murodjon.robotcallv2.inbound.application.dto.CreateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRouteFilter;
import uz.murodjon.robotcallv2.inbound.application.dto.UpdateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;

import java.util.List;

public interface InboundRouteRepository {

    long create(long companyId, CreateInboundRouteRequest request);

    void update(long companyId, long id, UpdateInboundRouteRequest request);

    void disable(long companyId, long id);

    InboundRoute find(long companyId, long id);

    List<InboundRoute> findAll(long companyId, InboundRouteFilter filter);

    long count(long companyId, InboundRouteFilter filter);

    boolean existsEnabledByDid(String didNumber);

    boolean existsEnabledByDidExcluding(String didNumber, long id);

    InboundRoute resolveByDid(String didNumber);
}
