package uz.murodjon.robotcallv2.apikey.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.apikey.domain.entity.ApiKey;
import uz.murodjon.robotcallv2.apikey.infrastructure.persistence.entity.ApiKeyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

import java.util.Set;

@Component
public class ApiKeyEntityMapper {

    public ApiKey toApiKey(ApiKeyEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ApiKey(
                entity.getId(),
                entity.getCompanyId(),
                entity.getName(),
                entity.getKeyPrefix(),
                entity.getKeyHash(),
                Set.copyOf(entity.getScopes()),
                entity.getCreatedBy(),
                entity.getLastUsedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getCreatedAt());
    }

    public ApiKeyEntity toApiKeyEntity(ApiKey apiKey, CompanyEntity company) {
        if (apiKey == null) {
            return null;
        }
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(apiKey.id());
        entity.setCompany(company);
        entity.setName(apiKey.name());
        entity.setKeyPrefix(apiKey.keyPrefix());
        entity.setKeyHash(apiKey.keyHash());
        entity.setScopes(apiKey.scopes());
        entity.setCreatedBy(apiKey.createdBy());
        entity.setLastUsedAt(apiKey.lastUsedAt());
        entity.setExpiresAt(apiKey.expiresAt());
        entity.setRevokedAt(apiKey.revokedAt());
        entity.setCreatedAt(apiKey.createdAt());
        return entity;
    }
}
