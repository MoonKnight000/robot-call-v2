package uz.murodjon.robotcallv2.alert.application.port.input;

import uz.murodjon.robotcallv2.alert.application.dto.AlertRulePreviewResponse;
import uz.murodjon.robotcallv2.alert.application.dto.SaveAlertRuleRequest;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRule;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRuleFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

/** The thresholds a company sets on its own calls ({@link AlertRule}). */
public interface AlertRuleUseCase {

    AlertRule createRule(long companyId, SaveAlertRuleRequest request);

    /** Replaces the settings; the evaluation state (last value, last fired) is kept. */
    AlertRule updateRule(long companyId, long id, SaveAlertRuleRequest request);

    void deleteRule(long companyId, long id);

    AlertRule findRule(long companyId, long id);

    PageableData<AlertRule> filterRules(long companyId, AlertRuleFilter filter);

    /** Evaluates the rule now without sending anything or touching its state. */
    AlertRulePreviewResponse previewRule(long companyId, long id);
}
