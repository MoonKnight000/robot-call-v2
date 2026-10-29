package uz.murodjon.robotcallv2.crm.domain.entity;

/** One kind of CRM task ("Qo'ng'iroq", "Uchrashuv") — what {@code crm_settings.task_type_id} names. */
public record CrmTaskType(long id, String name) {
}
