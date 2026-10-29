package uz.murodjon.robotcallv2.crm.domain.entity;

import java.util.List;

/** One sales pipeline and its stages, in the CRM's order. */
public record CrmPipe(long id, String name, List<CrmPipeStatus> statuses) {
}
