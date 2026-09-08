package uz.murodjon.robotcallv2.mcp.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.mcp.application.dto.McpConnectionRow;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpConnection;

@Component
public class McpMapper {

    public McpConnectionRow toRow(McpConnection connection) {
        return new McpConnectionRow(
                connection.id(),
                connection.name(),
                connection.url(),
                connection.authType(),
                connection.secretKey(),
                connection.status(),
                connection.toolCount(),
                connection.usableToolCount(),
                connection.allowWrites(),
                connection.lastError(),
                connection.refreshedAt(),
                connection.createdAt()
        );
    }
}
