package uz.murodjon.robotcallv2.report.application.dto;

public record DirectionMix(
        DirectionStats outbound,
        DirectionStats inbound
) {}
