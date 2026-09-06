package uz.murodjon.robotcallv2.inbound.application.port.input;

import uz.murodjon.robotcallv2.inbound.application.dto.CreateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRouteFilter;
import uz.murodjon.robotcallv2.inbound.application.dto.InboundRouteRow;
import uz.murodjon.robotcallv2.inbound.application.dto.UpdateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.report.domain.entity.InboundRouteStats;
import uz.murodjon.robotcallv2.shared.api.PageableData;

public interface InboundRouteUseCase {

    InboundRouteRow create(long companyId, CreateInboundRouteRequest request);

    InboundRouteRow update(long companyId, long id, UpdateInboundRouteRequest request);

    InboundRouteRow disable(long companyId, long id);

    PageableData<InboundRouteRow> list(long companyId, InboundRouteFilter filter);

    InboundRoute requireRoute(long companyId, long id);

    InboundRouteRow routeRow(long companyId, long id);

    InboundRoute resolveByDid(String did);

    InboundRouteStats stats(long companyId, long id);
}

