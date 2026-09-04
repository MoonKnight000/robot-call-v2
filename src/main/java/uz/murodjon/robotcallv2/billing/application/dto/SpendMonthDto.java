package uz.murodjon.robotcallv2.billing.application.dto;

public record SpendMonthDto(
        String month,
        long totalSpendUzs,
        int callMinutes
) {
}
