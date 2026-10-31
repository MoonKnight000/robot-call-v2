package uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPool;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPoolNumber;
import uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.entity.CallerIdPoolEntity;
import uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.entity.CallerIdPoolNumberEntity;

import java.util.List;

@Component
public class CallerIdPoolEntityMapper {

    public CallerIdPool toCallerIdPool(CallerIdPoolEntity entity) {
        if (entity == null) {
            return null;
        }
        List<CallerIdPoolNumber> numbers = entity.getNumbers() != null
                ? entity.getNumbers().stream().map(this::toCallerIdPoolNumber).toList()
                : List.of();
        return new CallerIdPool(
                entity.getId(),
                entity.getCompanyId() != null ? entity.getCompanyId() : 0L,
                entity.getName(),
                entity.getStrategy(),
                entity.getDescription(),
                numbers,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public CallerIdPoolNumber toCallerIdPoolNumber(CallerIdPoolNumberEntity entity) {
        if (entity == null) {
            return null;
        }
        return new CallerIdPoolNumber(
                entity.getId(),
                entity.getPoolId() != null ? entity.getPoolId() : 0L,
                entity.getPhone(),
                entity.getLabel(),
                entity.getUsageCount(),
                entity.getAnswerCount(),
                entity.getStatus(),
                entity.getCoolingUntil(),
                entity.getLastUsedAt(),
                entity.getCreatedAt(),
                entity.getPhoneNumber() != null ? entity.getPhoneNumber().getProviderTrunkId() : null
        );
    }
}
