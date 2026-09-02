package uz.murodjon.robotcallv2.inbound.application.port.output;

import uz.murodjon.robotcallv2.inbound.application.dto.CreateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.application.dto.InboundRouteFilter;
import uz.murodjon.robotcallv2.inbound.application.dto.UpdateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;

import java.util.List;

public interface InboundRouteRepository {

    long create(CreateInboundRouteRequest request);

    void update(long id, UpdateInboundRouteRequest request);

    void disable(long id);

    InboundRoute find(long id);

    List<InboundRoute> findAll(InboundRouteFilter filter);

    long count(InboundRouteFilter filter);

    boolean existsEnabledByDid(String didNumber);

    boolean existsEnabledByDidExcluding(String didNumber, long id);

    InboundRoute resolveByDid(String didNumber);
}
