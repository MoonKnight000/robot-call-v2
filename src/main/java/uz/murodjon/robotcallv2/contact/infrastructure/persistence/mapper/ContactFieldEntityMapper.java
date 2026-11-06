package uz.murodjon.robotcallv2.contact.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactField;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.entity.ContactFieldEntity;

@Component
public class ContactFieldEntityMapper {

    public ContactField toContactField(ContactFieldEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ContactField(entity.getId(), entity.getKey(), entity.getLabel(), entity.getType(),
                entity.getOptions(), entity.getDescription(), entity.getExtractionKey(), entity.getUpdateMode(),
                entity.getCreatedAt());
    }

    /** Copies what an update may change; the key and the type stay as created. */
    public void copyEditable(ContactField field, ContactFieldEntity entity) {
        entity.setLabel(field.label());
        entity.setOptions(field.options());
        entity.setDescription(field.description());
        entity.setExtractionKey(field.extractionKey());
        entity.setUpdateMode(field.updateMode());
    }
}
