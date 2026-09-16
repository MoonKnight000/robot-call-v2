package uz.murodjon.robotcallv2.contact.application.dto;

import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;

import java.util.List;

/**
 * Everything the company holds about one phone number ({@code GET /api/contacts/data}).
 *
 * @param contact null when the number is not in the contact book
 * @param memory  null when no call has been remembered for it
 */
public record ContactDataExport(
        String phone,
        Contact contact,
        ClientMemory memory,
        List<ContactDataExportCall> calls
) {
}
