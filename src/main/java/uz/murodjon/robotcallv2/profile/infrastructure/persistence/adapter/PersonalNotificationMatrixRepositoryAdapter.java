package uz.murodjon.robotcallv2.profile.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
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

    private final PersonalNotificationMatrixJpaRepository jpa;
    private final UserJpaRepository userJpa;
    private final CompanyJpaRepository companyJpa;
    private final CurrentCompany company;

    public PersonalNotificationMatrixRepositoryAdapter(PersonalNotificationMatrixJpaRepository jpa,
                                                       UserJpaRepository userJpa,
                                                       CompanyJpaRepository companyJpa,
                                                       CurrentCompany company) {
        this.jpa = jpa;
        this.userJpa = userJpa;
        this.companyJpa = companyJpa;
        this.company = company;
    }

    @Override
    public List<PersonalNotificationMatrixEntry> find(long userId) {
        return jpa.findByUserId(userId).stream()
                .map(e -> new PersonalNotificationMatrixEntry(e.getType(), e.getChannel(), e.isEnabled()))
                .toList();
    }

    @Override
    @Transactional
    public List<PersonalNotificationMatrixEntry> save(long userId, List<PersonalNotificationMatrixEntry> rows) {
        jpa.deleteByUserId(userId);
        UserEntity user = userJpa.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, userId));
        CompanyEntity comp = companyJpa.findById(company.id())
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, company.id()));

        for (PersonalNotificationMatrixEntry row : rows) {
            PersonalNotificationMatrixEntity entity = new PersonalNotificationMatrixEntity();
            entity.setCompany(comp);
            entity.setUser(user);
            entity.setType(row.type());
            entity.setChannel(row.channel());
            entity.setEnabled(row.enabled());
            jpa.save(entity);
        }
        return find(userId);
    }
}
