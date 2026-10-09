package uz.murodjon.robotcallv2.tool.infrastructure.persistence.repository;

public interface ToolExecutionStatsProjection {
    Long getToolId();
    String getToolName();
    long getTotal();
    long getOk();
    long getHttpError();
    long getFailed();
    double getAvgLatencyMs();
    double getP95LatencyMs();
}
