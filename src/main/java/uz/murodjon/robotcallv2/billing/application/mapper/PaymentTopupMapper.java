package uz.murodjon.robotcallv2.billing.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.billing.domain.entity.PaymentTopup;
import uz.murodjon.robotcallv2.billing.domain.enums.TopupStatus;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.PaymentTopupEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;

@Component
public class PaymentTopupMapper {

    public PaymentTopup toPaymentTopup(PaymentTopupEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PaymentTopup(
                entity.getId(),
                entity.getPaymentId(),
                entity.getCompanyId(),
                entity.getUserId(),
                entity.getAmountUzs() != null ? entity.getAmountUzs() : 0L,
                entity.getPaymentMethod(),
                entity.getStatus() != null ? entity.getStatus() : TopupStatus.PENDING,
                entity.getCheckoutUrl(),
                entity.getCreatedAt(),
                entity.getPaidAt());
    }

    public PaymentTopupEntity toEntity(PaymentTopup topup, CompanyEntity company, UserEntity user) {
        if (topup == null) {
            return null;
        }
        PaymentTopupEntity entity = new PaymentTopupEntity();
        entity.setId(topup.id());
        entity.setPaymentId(topup.paymentId());
        entity.setCompany(company);
        entity.setUser(user);
        entity.setAmountUzs(topup.amountUzs());
        entity.setPaymentMethod(topup.paymentMethod());
        entity.setStatus(topup.status());
        entity.setCheckoutUrl(topup.checkoutUrl());
        entity.setCreatedAt(topup.createdAt());
        entity.setPaidAt(topup.paidAt());
        return entity;
    }
}
