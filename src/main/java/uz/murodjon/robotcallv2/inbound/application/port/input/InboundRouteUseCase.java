package uz.murodjon.robotcallv2.inbound.application.port.input;

import uz.murodjon.robotcallv2.inbound.application.dto.CreateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.application.dto.InboundRouteFilter;
import uz.murodjon.robotcallv2.inbound.application.dto.InboundRouteRow;
import uz.murodjon.robotcallv2.inbound.application.dto.UpdateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.report.domain.entity.InboundRouteStats;
import uz.murodjon.robotcallv2.shared.api.PageableData;

public interface InboundRouteUseCase {

    InboundRouteRow create(CreateInboundRouteRequest r);

    InboundRouteRow update(long id, UpdateInboundRouteRequest r);

    InboundRouteRow disable(long id);

    PageableData<InboundRouteRow> list(InboundRouteFilter filter);

    InboundRoute requireRoute(long id);

    InboundRouteRow routeRow(long id);

    InboundRoute resolveByDid(String did);

    InboundRouteStats stats(long id);
}

