package uz.murodjon.robotcallv2.callrecord.application.port.input;

import java.util.Collection;

/** The writes finished calls still owe another system, as other features need to see them. */
public interface OutboxMessageUseCase {

    /**
     * Empties the payload of every PENDING or DEAD message about a call to one of
     * {@code phones} — a data-subject erasure. A PENDING message is closed as DEAD, since
     * there is nothing left to deliver; a SENT message keeps no payload to begin with. Must
     * run before the calls' numbers are anonymized, which is what the messages are found by.
     *
     * @return how many messages were emptied
     */
    int eraseMessagesOfPhones(long companyId, Collection<String> phones);
}
