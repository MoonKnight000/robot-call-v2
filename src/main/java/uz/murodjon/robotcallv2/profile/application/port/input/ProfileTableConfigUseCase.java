package uz.murodjon.robotcallv2.profile.application.port.input;

import com.fasterxml.jackson.databind.JsonNode;

public interface ProfileTableConfigUseCase {

    JsonNode find(String key);

    JsonNode update(long companyId, String key, JsonNode value);
}
