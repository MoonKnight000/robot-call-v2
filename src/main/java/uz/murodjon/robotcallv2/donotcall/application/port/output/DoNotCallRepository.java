package uz.murodjon.robotcallv2.donotcall.application.port.output;

import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallFilter;
import uz.murodjon.robotcallv2.donotcall.domain.entity.DoNotCall;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;

import java.util.List;

/**
 * Outbound SPI repository port for Do-Not-Call list data access.
 */
public interface DoNotCallRepository {

    void add(long companyId, String phone, String reason, DoNotCallSource source);

    boolean contains(long companyId, String phone);

    default boolean isBlocked(long companyId, String phone) {
        return contains(companyId, phone);
    }

    List<DoNotCall> findAll(long companyId, DoNotCallFilter filter);

    long count(long companyId, DoNotCallFilter filter);

    boolean remove(long companyId, String phone, String removedBy);
}
