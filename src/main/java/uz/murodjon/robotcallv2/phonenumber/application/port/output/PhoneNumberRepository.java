package uz.murodjon.robotcallv2.phonenumber.application.port.output;

import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumber;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumberFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PhoneNumberRepository {

    PhoneNumber save(PhoneNumber phoneNumber);

    Optional<PhoneNumber> findById(long id);

    boolean existsByPhoneNumber(String phoneNumber);

    Optional<PhoneNumber> findByPhoneNumber(String phoneNumber);

    /** Which of {@code phoneNumbers} are already stored. */
    Set<String> findExistingPhoneNumbers(Collection<String> phoneNumbers);

    /** Inserts new pool numbers (no company yet) in one batch. */
    void createAll(List<PhoneNumber> phoneNumbers);

    /** Every number, pool and assigned alike — the platform's view. */
    PageableData<PhoneNumber> findPage(PhoneNumberFilter filter);

    PageableData<PhoneNumber> findPageByCompanyId(long companyId, PhoneNumberFilter filter);

    /** Pool numbers still for sale, narrowed further by the filter. */
    PageableData<PhoneNumber> findAvailablePage(PhoneNumberFilter filter);

    void deleteById(long id);
}
