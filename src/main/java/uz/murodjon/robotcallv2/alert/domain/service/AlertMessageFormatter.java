package uz.murodjon.robotcallv2.alert.domain.service;

import uz.murodjon.robotcallv2.alert.domain.entity.AlertMetricValue;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRule;
import uz.murodjon.robotcallv2.alert.domain.enums.AlertComparator;
import uz.murodjon.robotcallv2.alert.domain.enums.AlertMetric;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The notification a fired rule sends, in the panel's language. */
public final class AlertMessageFormatter {

    private AlertMessageFormatter() {
    }

    /** E.g. "Muvaffaqiyat darajasi: 32% (oxirgi 60 daqiqada, 54 ta qo'ng'iroq). Chegara: 40% dan past." */
    public static String formatMessage(AlertRule rule, AlertMetricValue value) {
        return labelOf(rule.metric()) + ": " + amount(rule.metric(), value.value())
                + " (oxirgi " + rule.windowMinutes() + " daqiqada, " + value.sampleSize() + " ta "
                + sampleNoun(rule.metric()) + "). Chegara: " + amount(rule.metric(), rule.threshold())
                + (rule.comparator() == AlertComparator.ABOVE ? " dan yuqori." : " dan past.");
    }

    private static String labelOf(AlertMetric metric) {
        return switch (metric) {
            case SUCCESS_RATE -> "Muvaffaqiyat darajasi";
            case TRANSFER_RATE -> "Operatorga o'tkazish ulushi";
            case FAILED_CALL_RATE -> "Texnik xato bilan tugagan qo'ng'iroqlar ulushi";
            case NEGATIVE_SENTIMENT_RATE -> "Norozi mijozlar ulushi";
            case AVG_TURN_LATENCY_MS -> "Bot javobining o'rtacha kechikishi";
            case TOOL_ERROR_COUNT -> "Tool xatolari";
            case CALL_COUNT -> "Qo'ng'iroqlar soni";
        };
    }

    private static String sampleNoun(AlertMetric metric) {
        return metric == AlertMetric.TOOL_ERROR_COUNT ? "tool so'rovi" : "qo'ng'iroq";
    }

    private static String amount(AlertMetric metric, double value) {
        String number = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        if (metric.isPercentage()) {
            return number + "%";
        }
        return metric == AlertMetric.AVG_TURN_LATENCY_MS ? number + " ms" : number;
    }
}
