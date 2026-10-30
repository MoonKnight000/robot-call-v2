package uz.murodjon.robotcallv2.siptrunk.domain.entity;

import uz.murodjon.robotcallv2.siptrunk.domain.enums.SipTrunkTransport;

/**
 * What a SIP stack other than Asterisk needs to dial out over a company's managed trunk —
 * the LiveKit engine, which gets these per call instead of a generated PJSIP file. The
 * password is in plain text, so this never leaves the call that asked for it and never
 * prints it.
 */
public record SipTrunkCredentials(
        long trunkId,
        String host,
        int port,
        SipTrunkTransport transport,
        String username,
        String password
) {
    @Override
    public String toString() {
        return "SipTrunkCredentials[trunkId=" + trunkId + ", host=" + host + ", port=" + port
                + ", transport=" + transport + ", username=" + username + ", password=***]";
    }
}
