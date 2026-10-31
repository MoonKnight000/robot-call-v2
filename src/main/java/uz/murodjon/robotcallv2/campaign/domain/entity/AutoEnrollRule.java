package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.AutoEnrollField;
import uz.murodjon.robotcallv2.campaign.domain.enums.AutoEnrollOperator;

/**
 * One condition a contact must meet to be enrolled, e.g. TAG EQUALS "qarzdor".
 */
public record AutoEnrollRule(AutoEnrollField field, AutoEnrollOperator operator, String value) {
}
