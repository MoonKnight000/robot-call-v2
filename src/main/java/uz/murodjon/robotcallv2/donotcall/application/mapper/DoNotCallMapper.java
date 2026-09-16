package uz.murodjon.robotcallv2.donotcall.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallRow;
import uz.murodjon.robotcallv2.donotcall.domain.entity.DoNotCall;

/** Domain → DTO for the do-not-call list; entity mapping lives in {@code DoNotCallEntityMapper}. */
@Component
public class DoNotCallMapper {

    public DoNotCallRow toRow(DoNotCall domain, String contactName) {
        return DoNotCallRow.of(domain, contactName);
    }
}
