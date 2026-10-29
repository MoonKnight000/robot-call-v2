package uz.murodjon.robotcallv2.phonenumber.application.port.output;

import uz.murodjon.robotcallv2.phonenumber.domain.entity.SipProbeResult;

/** Checks from this server whether a tenant-given SIP host answers; never throws, a failure is a result. */
public interface SipProbePort {

    SipProbeResult probe(String host, int port, String transport);
}
