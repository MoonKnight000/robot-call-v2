package uz.murodjon.robotcallv2.billing.application.dto;

/**
 * One JSON-RPC call from Payme. Parsed by the use case rather than by Spring, so that a body
 * that is not JSON is still answered in Payme's format (-32700) instead of with a 400.
 *
 * @param method the Merchant API method, e.g. {@code CheckPerformTransaction}
 * @param params its parameters; which ones are set depends on the method
 * @param id     the JSON-RPC request id, echoed back exactly as it came (number or string)
 */
public record PaymeRequest(
        String method,
        PaymeParams params,
        Object id
) {
}
