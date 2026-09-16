package uz.murodjon.robotcallv2.operator.application.port.input;

import uz.murodjon.robotcallv2.operator.domain.enums.ContactCenterEventType;
import uz.murodjon.robotcallv2.shared.stream.EventSink;
import uz.murodjon.robotcallv2.shared.stream.EventSubscription;

import java.util.Set;

/**
 * Push events for the operator panel and the queue wallboard. Publishing never blocks: the
 * call-queue dispatcher and ARI threads call it.
 */
public interface ContactCenterEventUseCase {

    /** One operator's panel: events for this user, and everything published to the company. */
    EventSubscription subscribeUser(long companyId, long userId, EventSink sink);

    /** A queue wallboard: everything published to the company, and its wallboard events. */
    EventSubscription subscribeWallboard(long companyId, EventSink sink);

    /** To one operator's panel(s) — every tab they have open. */
    void publishToUser(long userId, ContactCenterEventType type, Object payload);

    /** To every panel and wallboard of the company. */
    void publishToCompany(long companyId, ContactCenterEventType type, Object payload);

    /** To the company's wallboards only. */
    void publishToWallboard(long companyId, ContactCenterEventType type, Object payload);

    /** Every company with a wallboard open right now. */
    Set<Long> findWallboardCompanyIds();
}
