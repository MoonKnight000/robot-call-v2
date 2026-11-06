package uz.murodjon.robotcallv2.role.application.port.input;

import uz.murodjon.robotcallv2.role.application.dto.RoleTemplateRow;
import uz.murodjon.robotcallv2.role.application.dto.SaveRoleTemplateRequest;
import uz.murodjon.robotcallv2.role.domain.entity.RoleTemplate;

import java.util.List;

/** The platform's default roles, copied into every new company (docs/api/platform-role-templates.md). */
public interface RoleTemplateUseCase {

    List<RoleTemplateRow> findAll();

    RoleTemplateRow requireRow(long id);

    /** {@code companyId} is the caller's, for the audit trail only. */
    RoleTemplateRow create(long companyId, SaveRoleTemplateRequest request);

    RoleTemplateRow update(long companyId, long id, SaveRoleTemplateRequest request);

    void delete(long companyId, long id);

    /** What a new company is seeded with. */
    List<RoleTemplate> findTemplates();

    /** First run only: turns today's code-defined roles into templates when the table is empty. */
    void createDefaultTemplates();
}
