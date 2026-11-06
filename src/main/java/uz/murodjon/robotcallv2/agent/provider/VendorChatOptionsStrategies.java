package uz.murodjon.robotcallv2.agent.provider;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The {@link VendorChatOptionsStrategy} of each vendor, picked by {@link ChatVendor}. */
@Component
public class VendorChatOptionsStrategies {

    private final Map<ChatVendor, VendorChatOptionsStrategy> byVendor = new EnumMap<>(ChatVendor.class);

    public VendorChatOptionsStrategies(List<VendorChatOptionsStrategy> strategies) {
        for (VendorChatOptionsStrategy strategy : strategies) {
            if (byVendor.put(strategy.vendor(), strategy) != null) {
                throw new IllegalStateException("Two option strategies for " + strategy.vendor());
            }
        }
        for (ChatVendor vendor : ChatVendor.values()) {
            if (!byVendor.containsKey(vendor)) {
                throw new IllegalStateException("No option strategy for " + vendor);
            }
        }
    }

    public VendorChatOptionsStrategy findByVendor(ChatVendor vendor) {
        return byVendor.get(vendor);
    }
}
