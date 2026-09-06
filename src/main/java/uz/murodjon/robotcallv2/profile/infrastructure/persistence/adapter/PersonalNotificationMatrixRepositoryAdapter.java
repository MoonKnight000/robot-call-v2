package uz.murodjon.robotcallv2.profile.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.profile.application.port.output.PersonalNotificationMatrixRepository;
import uz.murodjon.robotcallv2.profile.domain.entity.PersonalNotificationMatrixEntry;
import uz.murodjon.robotcallv2.profile.infrastructure.persistence.entity.PersonalNotificationMatrixEntity;
import uz.murodjon.robotcallv2.profile.infrastructure.persistence.repository.PersonalNotificationMatrixJpaRepository;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.repository.UserJpaRepository;

import java.util.List;

@Component
public class PersonalNotificationMatrixRepositoryAdapter implements PersonalNotificationMatrixRepository {

    private final PersonalNotificationMatrixJpaRepository personalNotificationMatrixJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;

    public PersonalNotificationMatrixRepositoryAdapter(PersonalNotificationMatrixJpaRepository personalNotificationMatrixJpaRepository,
                                                       UserJpaRepository userJpaRepository,
                                                       CompanyJpaRepository companyJpaRepository) {
        this.personalNotificationMatrixJpaRepository = personalNotificationMatrixJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.companyJpaRepository = companyJpaRepository;
    }

    @Override
    public List<PersonalNotificationMatrixEntry> find(long userId) {
        return personalNotificationMatrixJpaRepository.findByUserId(userId).stream()
                .map(e -> new PersonalNotificationMatrixEntry(e.getType(), e.getChannel(), e.isEnabled()))
                .toList();
    }

    @Override
    @Transactional
    public List<PersonalNotificationMatrixEntry> save(long companyId, long userId,
                                                     List<PersonalNotificationMatrixEntry> rows) {
        personalNotificationMatrixJpaRepository.deleteByUserId(userId);
        UserEntity user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, userId));
        CompanyEntity comp = companyJpaRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, companyId));

        for (PersonalNotificationMatrixEntry row : rows) {
            PersonalNotificationMatrixEntity entity = new PersonalNotificationMatrixEntity();
            entity.setCompany(comp);
            entity.setUser(user);
            entity.setType(row.type());
            entity.setChannel(row.channel());
            entity.setEnabled(row.enabled());
            personalNotificationMatrixJpaRepository.save(entity);
        }
        return find(userId);
    }
}
