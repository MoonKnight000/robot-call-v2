package uz.murodjon.robotcallv2.apikey.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.apikey.application.dto.ApiKeyRow;
import uz.murodjon.robotcallv2.apikey.domain.entity.ApiKey;

@Component
public class ApiKeyMapper {

    /** Console projection: keeps {@code keyHash} out of the response. */
    public ApiKeyRow toRow(ApiKey apiKey) {
        if (apiKey == null) {
            return null;
        }
        return new ApiKeyRow(
                apiKey.id(),
                apiKey.name(),
                apiKey.keyPrefix(),
                apiKey.scopes(),
                apiKey.lastUsedAt(),
                apiKey.expiresAt(),
                apiKey.revokedAt(),
                apiKey.createdAt());
    }
}
