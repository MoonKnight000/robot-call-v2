package uz.murodjon.robotcallv2.operator.application.port.output;

import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipEndpoint;

import java.util.List;

/**
 * Makes Asterisk know the operators' endpoints. Best-effort by contract: a failure is
 * logged and retried on the next change, never thrown into the request that caused it.
 */
public interface OperatorEndpointConfigPort {

    void writeEndpoints(List<OperatorSipEndpoint> endpoints);
}
