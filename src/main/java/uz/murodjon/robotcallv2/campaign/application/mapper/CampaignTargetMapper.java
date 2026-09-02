package uz.murodjon.robotcallv2.campaign.application.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignTargetEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.contact.infrastructure.persistence.entity.ContactEntity;

import java.time.Instant;

@Component
public class CampaignTargetMapper {

    public CampaignTarget toDomain(CampaignTargetEntity e) {
        if (e == null) return null;
        return new CampaignTarget(
                e.getId(),
                e.getCampaignId(),
                e.getClientId(),
                e.getPhone(),
                e.getLanguage(),
                e.getContextData(),
                e.getStatus(),
                e.getAttempts(),
                e.isDoNotCall()
        );
    }

    public CampaignTargetEntity toEntity(CampaignTarget d, CampaignEntity campaign, CompanyEntity company, ContactEntity contact) {
        if (d == null) return null;
        CampaignTargetEntity e = new CampaignTargetEntity();
        e.setId(d.id() > 0 ? d.id() : null);
        e.setCampaign(campaign);
        e.setCompany(company);
        e.setContact(contact);
        e.setPhone(d.phone());
        e.setLanguage(d.language());
        e.setContextData(d.contextData() == null ? "{}" : d.contextData());
        e.setStatus(d.status());
        e.setAttempts(d.attempts());
        e.setDoNotCall(d.doNotCall());
        e.setCreatedAt(Instant.now());
        return e;
    }
}
