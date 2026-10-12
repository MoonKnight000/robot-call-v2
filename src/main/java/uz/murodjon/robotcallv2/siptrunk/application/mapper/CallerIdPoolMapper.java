package uz.murodjon.robotcallv2.siptrunk.application.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.siptrunk.application.dto.CallerIdNumberRow;
import uz.murodjon.robotcallv2.siptrunk.application.dto.CallerIdPoolDetail;
import uz.murodjon.robotcallv2.siptrunk.application.dto.CallerIdPoolRow;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPool;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPoolNumber;

import java.time.Instant;
import java.util.List;

@Component
public class CallerIdPoolMapper {

    public CallerIdPoolRow toRow(CallerIdPool pool) {
        Instant now = Instant.now();
        int total = pool.numbers().size();
        int active = (int) pool.numbers().stream().filter(n -> n.isEligible(now)).count();
        return new CallerIdPoolRow(
                pool.id(),
                pool.name(),
                pool.strategy().name(),
                pool.description(),
                total,
                active,
                pool.createdAt(),
                pool.updatedAt()
        );
    }

    public CallerIdPoolDetail toDetail(CallerIdPool pool) {
        List<CallerIdNumberRow> rows = pool.numbers().stream()
                .map(this::toNumberRow)
                .toList();
        return new CallerIdPoolDetail(
                pool.id(),
                pool.name(),
                pool.strategy().name(),
                pool.description(),
                rows,
                pool.createdAt(),
                pool.updatedAt()
        );
    }

    public CallerIdNumberRow toNumberRow(CallerIdPoolNumber number) {
        return new CallerIdNumberRow(
                number.id(),
                number.phone(),
                number.label(),
                number.usageCount(),
                number.answerCount(),
                number.answerRate(),
                number.status().name(),
                number.coolingUntil(),
                number.lastUsedAt(),
                number.createdAt()
        );
    }
}
