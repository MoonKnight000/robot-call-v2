package uz.murodjon.robotcallv2.crm.domain.entity;

/** One CRM employee — who a task, a lead or a transfer can go to. */
public record CrmEmployee(long id, String fullName, String phone, String departmentName) {
}
