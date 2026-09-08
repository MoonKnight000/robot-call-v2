package uz.murodjon.robotcallv2.mcp.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpConnection;
import uz.murodjon.robotcallv2.mcp.infrastructure.persistence.entity.McpConnectionEntity;

@Component
public class McpConnectionMapper {

    public McpConnection toMcpConnection(McpConnectionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new McpConnection(
                entity.getId(),
                entity.getCompanyId(),
                entity.getName(),
                entity.getUrl(),
                entity.getAuthType(),
                entity.getSecretKey(),
                entity.getStatus(),
                entity.getToolCount() != null ? entity.getToolCount() : 0,
                entity.getUsableToolCount() != null ? entity.getUsableToolCount() : 0,
                Boolean.TRUE.equals(entity.getAllowWrites()),
                entity.getToolsJson(),
                entity.getLastError(),
                entity.getRefreshedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public McpConnectionEntity toEntity(McpConnection connection, CompanyEntity company) {
        if (connection == null) {
            return null;
        }
        McpConnectionEntity entity = new McpConnectionEntity();
        entity.setId(connection.id());
        entity.setCompany(company);
        entity.setName(connection.name());
        entity.setUrl(connection.url());
        entity.setAuthType(connection.authType());
        entity.setSecretKey(connection.secretKey());
        entity.setStatus(connection.status());
        entity.setToolCount(connection.toolCount());
        entity.setUsableToolCount(connection.usableToolCount());
        entity.setAllowWrites(connection.allowWrites());
        entity.setToolsJson(connection.toolsJson());
        entity.setLastError(connection.lastError());
        entity.setRefreshedAt(connection.refreshedAt());
        entity.setCreatedAt(connection.createdAt());
        entity.setUpdatedAt(connection.updatedAt());
        return entity;
    }
}
