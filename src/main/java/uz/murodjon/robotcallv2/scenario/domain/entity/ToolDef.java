package uz.murodjon.robotcallv2.scenario.domain.entity;

import java.util.List;

public record ToolDef(
        String name,
        String description,
        List<ToolParamDef> params
) {
}
