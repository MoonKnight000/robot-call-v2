package uz.murodjon.robotcallv2.phonenumber.domain.entity;

import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberCategory;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberStatus;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberTableField;
import uz.murodjon.robotcallv2.phonenumber.domain.enums.PhoneNumberType;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.util.LinkedHashMap;

public record PhoneNumberFilter(
        Integer page,
        Integer size,
        LinkedHashMap<PhoneNumberTableField, Sort.Direction> orders,
        String search,
        PhoneNumberCategory category,
        PhoneNumberStatus status,
        PhoneNumberType type
) implements FilterInterface<PhoneNumberTableField> {

    public PhoneNumberFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(PhoneNumberTableField.CREATED_AT, Sort.Direction.DESC);
            orders.put(PhoneNumberTableField.ID, Sort.Direction.DESC);
        } else if (!orders.containsKey(PhoneNumberTableField.ID)) {
            LinkedHashMap<PhoneNumberTableField, Sort.Direction> normalized = new LinkedHashMap<>(orders);
            normalized.put(PhoneNumberTableField.ID, Sort.Direction.DESC);
            orders = normalized;
        }
    }
}
