package uz.murodjon.robotcallv2.webhook.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.webhook.application.dto.WebhookDeliveryRow;
import uz.murodjon.robotcallv2.webhook.application.port.input.WebhookDeliveryUseCase;
import uz.murodjon.robotcallv2.webhook.domain.entity.WebhookDeliveryFilter;

@RestController
public class WebhookDeliveryControllerImpl implements WebhookDeliveryController {

    private final WebhookDeliveryUseCase webhookDeliveryUseCase;

    public WebhookDeliveryControllerImpl(WebhookDeliveryUseCase webhookDeliveryUseCase) {
        this.webhookDeliveryUseCase = webhookDeliveryUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<WebhookDeliveryRow>>> filter(long companyId,
                                                                               WebhookDeliveryFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(webhookDeliveryUseCase.filterDeliveries(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<WebhookDeliveryRow>> resend(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(webhookDeliveryUseCase.retryDelivery(companyId, id)));
    }
}
