package uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.wrapup.domain.entity.WrapUpCode;
import uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.entity.WrapUpCodeEntity;

@Component
public class WrapUpCodeEntityMapper {

    public WrapUpCode toWrapUpCode(WrapUpCodeEntity entity) {
        if (entity == null) {
            return null;
        }
        return new WrapUpCode(entity.getId(), entity.getCompanyId(), entity.getName(), entity.getOutcome(),
                entity.isRequiresCallbackAt(), entity.isEnabled(), entity.getCreatedAt());
    }

    /** Everything an edit may change. */
    public void applyEditableFields(WrapUpCodeEntity entity, WrapUpCode code) {
        entity.setName(code.name());
        entity.setOutcome(code.outcome());
        entity.setRequiresCallbackAt(code.requiresCallbackAt());
        entity.setEnabled(code.enabled());
    }
}
