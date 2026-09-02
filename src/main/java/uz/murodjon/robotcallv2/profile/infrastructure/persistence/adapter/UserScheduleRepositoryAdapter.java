package uz.murodjon.robotcallv2.profile.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.profile.application.port.output.UserScheduleRepository;
import uz.murodjon.robotcallv2.profile.domain.entity.ScheduleSlot;
import uz.murodjon.robotcallv2.profile.infrastructure.persistence.entity.UserScheduleSlotEntity;
import uz.murodjon.robotcallv2.profile.infrastructure.persistence.repository.UserScheduleJpaRepository;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.repository.UserJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class UserScheduleRepositoryAdapter implements UserScheduleRepository {

    private final UserScheduleJpaRepository jpa;
    private final UserJpaRepository userJpa;
    private final CompanyJpaRepository companyJpa;
    private final CurrentCompany company;

    public UserScheduleRepositoryAdapter(UserScheduleJpaRepository jpa,
                                         UserJpaRepository userJpa,
                                         CompanyJpaRepository companyJpa,
                                         CurrentCompany company) {
        this.jpa = jpa;
        this.userJpa = userJpa;
        this.companyJpa = companyJpa;
        this.company = company;
    }

    @Override
    public List<ScheduleSlot> find(long userId) {
        return jpa.findByUserIdOrderByDayOfWeekAscStartTimeAsc(userId).stream()
                .map(e -> new ScheduleSlot(e.getDayOfWeek(), e.getStartTime(), e.getEndTime()))
                .toList();
    }

    @Override
    @Transactional
    public List<ScheduleSlot> save(long userId, List<ScheduleSlot> slots) {
        jpa.deleteByUserId(userId);
        UserEntity user = userJpa.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, userId));
        CompanyEntity comp = companyJpa.findById(company.id())
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, company.id()));

        Instant now = Instant.now();
        for (ScheduleSlot slot : slots) {
            UserScheduleSlotEntity entity = new UserScheduleSlotEntity();
            entity.setCompany(comp);
            entity.setUser(user);
            entity.setDayOfWeek(slot.dayOfWeek());
            entity.setStartTime(slot.startTime());
            entity.setEndTime(slot.endTime());
            entity.setCreatedAt(now);
            jpa.save(entity);
        }
        return find(userId);
    }
}
