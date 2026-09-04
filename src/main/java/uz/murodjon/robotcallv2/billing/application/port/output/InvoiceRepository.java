package uz.murodjon.robotcallv2.billing.application.port.output;

import uz.murodjon.robotcallv2.billing.domain.entity.Invoice;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.Optional;

public interface InvoiceRepository {

    Optional<Invoice> findById(String id);

    PageableData<Invoice> findAllByCompanyId(long companyId, int page, int size);

    Invoice save(Invoice invoice);
}
