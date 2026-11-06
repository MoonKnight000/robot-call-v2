package uz.murodjon.robotcallv2.contact.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactCrmLink;
import uz.murodjon.robotcallv2.contact.domain.entity.PendingCrmPush;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.entity.ContactCrmLinkEntity;

@Component
public class ContactCrmLinkEntityMapper {

    public ContactCrmLink toContactCrmLink(ContactCrmLinkEntity entity) {
        return new ContactCrmLink(entity.getContactId(), entity.getProvider(), entity.getExternalId(),
                entity.getSyncedAt());
    }

    /** The owed write without the contact's values, which the service reads from the contact. */
    public PendingCrmPush toPendingCrmPush(ContactCrmLinkEntity entity) {
        return new PendingCrmPush(entity.getCompanyId(), entity.getContactId(), entity.getProvider(),
                entity.getExternalId(), entity.getPushRequestedAt(), entity.getPushAttempts(), null);
    }
}
