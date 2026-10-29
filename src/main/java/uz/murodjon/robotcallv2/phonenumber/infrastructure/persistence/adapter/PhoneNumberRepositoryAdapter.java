package uz.murodjon.robotcallv2.phonenumber.infrastructure.persistence.adapter;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.phonenumber.application.port.output.PhoneNumberRepository;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumber;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumberFilter;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberStatus;
import uz.murodjon.robotcallv2.phonenumber.infrastructure.persistence.entity.PhoneNumberEntity;
import uz.murodjon.robotcallv2.phonenumber.infrastructure.persistence.mapper.PhoneNumberEntityMapper;
import uz.murodjon.robotcallv2.phonenumber.infrastructure.persistence.repository.PhoneNumberJpaRepository;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.util.LikePatterns;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class PhoneNumberRepositoryAdapter implements PhoneNumberRepository {

    private final PhoneNumberJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final PhoneNumberEntityMapper phoneNumberEntityMapper;

    public PhoneNumberRepositoryAdapter(PhoneNumberJpaRepository jpaRepository,
                                        CompanyJpaRepository companyJpaRepository,
                                        PhoneNumberEntityMapper phoneNumberEntityMapper) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.phoneNumberEntityMapper = phoneNumberEntityMapper;
    }

    @Override
    public PhoneNumber save(PhoneNumber phoneNumber) {
        PhoneNumberEntity entity = phoneNumber.id() != null
                ? jpaRepository.findById(phoneNumber.id()).orElseGet(PhoneNumberEntity::new)
                : new PhoneNumberEntity();
        phoneNumberEntityMapper.copyToEntity(phoneNumber,
                phoneNumber.companyId() != null ? companyJpaRepository.getReferenceById(phoneNumber.companyId()) : null,
                entity);
        return phoneNumberEntityMapper.toPhoneNumber(jpaRepository.save(entity));
    }

    @Override
    public Optional<PhoneNumber> findById(long id) {
        return jpaRepository.findById(id).map(phoneNumberEntityMapper::toPhoneNumber);
    }

    @Override
    public boolean existsByPhoneNumber(String phoneNumber) {
        return jpaRepository.existsByPhoneNumber(phoneNumber);
    }

    @Override
    public Set<String> findExistingPhoneNumbers(Collection<String> phoneNumbers) {
        return phoneNumbers.isEmpty() ? Set.of() : Set.copyOf(jpaRepository.findPhoneNumbersIn(phoneNumbers));
    }

    @Override
    public void createAll(List<PhoneNumber> phoneNumbers) {
        jpaRepository.saveAll(phoneNumbers.stream().map(phoneNumber -> {
            PhoneNumberEntity entity = new PhoneNumberEntity();
            phoneNumberEntityMapper.copyToEntity(phoneNumber, null, entity);
            return entity;
        }).toList());
    }

    @Override
    public PageableData<PhoneNumber> findPage(PhoneNumberFilter filter) {
        return findPage(filterSpecification(filter), filter);
    }

    @Override
    public PageableData<PhoneNumber> findPageByCompanyId(long companyId, PhoneNumberFilter filter) {
        Specification<PhoneNumberEntity> ofCompany = (root, query, cb) -> cb.equal(root.get("companyId"), companyId);
        return findPage(ofCompany.and(filterSpecification(filter)), filter);
    }

    @Override
    public PageableData<PhoneNumber> findAvailablePage(PhoneNumberFilter filter) {
        Specification<PhoneNumberEntity> available =
                (root, query, cb) -> cb.equal(root.get("status"), PhoneNumberStatus.AVAILABLE);
        return findPage(available.and(filterSpecification(filter)), filter);
    }

    @Override
    public void deleteById(long id) {
        jpaRepository.deleteById(id);
    }

    private PageableData<PhoneNumber> findPage(Specification<PhoneNumberEntity> specification, PhoneNumberFilter filter) {
        Page<PhoneNumberEntity> page = jpaRepository.findAll(specification, filter.pageable());
        return PageableData.of(page.getContent().stream().map(phoneNumberEntityMapper::toPhoneNumber).toList(),
                filter.pageOrDefault(), filter.sizeOrDefault(), page.getTotalElements());
    }

    /** Search over the number and nickname, plus each enum the filter names; a null field does not narrow. */
    private static Specification<PhoneNumberEntity> filterSpecification(PhoneNumberFilter filter) {
        String pattern = filter.search() == null || filter.search().isBlank()
                ? null : LikePatterns.buildContainsPattern(filter.search().toLowerCase());
        return (root, query, cb) -> cb.and(
                pattern == null ? cb.conjunction() : cb.or(
                        cb.like(cb.lower(root.get("phoneNumber")), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(root.get("nickname")), pattern, LikePatterns.ESCAPE)),
                filter.category() == null ? cb.conjunction() : cb.equal(root.get("category"), filter.category()),
                filter.status() == null ? cb.conjunction() : cb.equal(root.get("status"), filter.status()),
                filter.type() == null ? cb.conjunction() : cb.equal(root.get("type"), filter.type()));
    }
}
