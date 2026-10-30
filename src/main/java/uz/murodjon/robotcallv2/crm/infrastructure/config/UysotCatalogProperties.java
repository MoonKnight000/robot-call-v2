package uz.murodjon.robotcallv2.crm.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * The Uysot Open API reads the agent and the settings screen browse: residential complexes,
 * flats, contract payment schedules, employees and pipelines. Bound from
 * {@code voice-agent.crm.catalog.*}.
 *
 * <p>Paths are configuration, like {@link CrmProperties}'s, because they are the part that
 * can be corrected without a release. A blank path switches that read off: the agent's tool
 * answers "not available" and the settings endpoint answers {@code 502 UYSOT_ENDPOINT_NOT_CONFIGURED}.
 *
 * @param housesPath          GET — the company's residential complexes
 * @param flatFilterPath      POST — flats by rooms / price / floor / complex / status
 * @param employeeFilterPath  POST — the company's employees
 * @param pipePath            GET — sales pipelines and their statuses
 * @param contractPaymentPath GET — a contract's monthly payment schedule, {@code {id}} = contract id
 * @param bookingFilterPath   POST — bookings of a client, found by the phone's last digits in {@code search}
 * @param directoryTtl        how long complexes, employees and pipelines are cached per company
 * @param flatTtl             how long one flat search is cached — prices and availability change
 */
@ConfigurationProperties(prefix = "voice-agent.crm.catalog")
public record UysotCatalogProperties(
        String housesPath,
        String flatFilterPath,
        String employeeFilterPath,
        String pipePath,
        String contractPaymentPath,
        String bookingFilterPath,
        Duration directoryTtl,
        Duration flatTtl
) {
    public UysotCatalogProperties {
        directoryTtl = directoryTtl != null ? directoryTtl : Duration.ofMinutes(15);
        flatTtl = flatTtl != null ? flatTtl : Duration.ofMinutes(3);
    }
}
