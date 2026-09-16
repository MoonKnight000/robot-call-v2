package uz.murodjon.robotcallv2.integration.infrastructure.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.integration.application.port.output.UysotOAuthPort;
import uz.murodjon.robotcallv2.integration.domain.entity.OAuthTokenReply;
import uz.murodjon.robotcallv2.integration.infrastructure.config.UysotOAuthProperties;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** {@link UysotOAuthPort} over the JDK HTTP client, as form posts (RFC 6749). */
@Component
public class HttpClientUysotOAuthAdapter implements UysotOAuthPort {

    private final UysotOAuthProperties uysotOAuthProperties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public HttpClientUysotOAuthAdapter(UysotOAuthProperties uysotOAuthProperties) {
        this.uysotOAuthProperties = uysotOAuthProperties;
    }

    @Override
    public OAuthTokenReply exchangeCode(String code) throws IOException, InterruptedException {
        return postToTokenUrl("grant_type=authorization_code"
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(uysotOAuthProperties.redirectUri())
                + "&client_id=" + encode(uysotOAuthProperties.clientId())
                + "&client_secret=" + encode(uysotOAuthProperties.clientSecret()));
    }

    @Override
    public OAuthTokenReply refreshToken(String refreshToken) throws IOException, InterruptedException {
        return postToTokenUrl("grant_type=refresh_token"
                + "&refresh_token=" + encode(refreshToken)
                + "&client_id=" + encode(uysotOAuthProperties.clientId())
                + "&client_secret=" + encode(uysotOAuthProperties.clientSecret()));
    }

    @Override
    public void revokeToken(String token) throws IOException, InterruptedException {
        String body = "token=" + encode(token)
                + "&client_id=" + encode(uysotOAuthProperties.clientId())
                + "&client_secret=" + encode(uysotOAuthProperties.clientSecret());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uysotOAuthProperties.revokeUrl()))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private OAuthTokenReply postToTokenUrl(String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uysotOAuthProperties.tokenUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new OAuthTokenReply(response.statusCode(), response.body());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
