package uz.murodjon.robotcallv2.billing.application.port.output;

import uz.murodjon.robotcallv2.billing.domain.entity.ChannelUsage;

/**
 * The company's live concurrent calls and their limit. The dialer holds both — the count and
 * the company's plan allowance with the platform default behind it — and implements this.
 */
public interface ChannelUsagePort {

    ChannelUsage findChannelUsage(long companyId);
}
