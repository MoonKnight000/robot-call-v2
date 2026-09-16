package uz.murodjon.robotcallv2.donotcall.application.port.output;

import uz.murodjon.robotcallv2.donotcall.domain.entity.DoNotCall;
import uz.murodjon.robotcallv2.donotcall.domain.entity.DoNotCallFilter;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Outbound SPI repository port for Do-Not-Call list data access.
 */
public interface DoNotCallRepository {

    void add(long companyId, String phone, String reason, DoNotCallSource source);

    /** Whether the company has an active entry for {@code phone}, spelled exactly as given. */
    boolean existsByPhone(long companyId, String phone);

    /** The phones among {@code phones} that have an active entry, spelled exactly as given. */
    Set<String> findListedPhones(long companyId, Collection<String> phones);

    List<DoNotCall> findAll(long companyId, DoNotCallFilter filter);

    long count(long companyId, DoNotCallFilter filter);

    boolean remove(long companyId, String phone, String removedBy);
}
