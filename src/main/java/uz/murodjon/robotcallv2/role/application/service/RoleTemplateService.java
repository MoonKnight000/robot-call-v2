package uz.murodjon.robotcallv2.role.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.role.application.dto.RoleTemplateRow;
import uz.murodjon.robotcallv2.role.application.dto.SaveRoleTemplateRequest;
import uz.murodjon.robotcallv2.role.application.port.input.RoleTemplateUseCase;
import uz.murodjon.robotcallv2.role.application.port.output.RoleTemplateRepository;
import uz.murodjon.robotcallv2.role.domain.entity.RoleTemplate;
import uz.murodjon.robotcallv2.role.domain.enums.SystemRole;
import uz.murodjon.robotcallv2.role.domain.service.RoleValidator;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.util.List;

@Service
public class RoleTemplateService implements RoleTemplateUseCase {

    private final RoleTemplateRepository repository;
    private final AuditUseCase audit;

    public RoleTemplateService(RoleTemplateRepository repository, AuditUseCase audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Override
    public List<RoleTemplateRow> findAll() {
        return repository.findAll().stream().map(RoleTemplateRow::of).toList();
    }

    @Override
    public RoleTemplateRow requireRow(long id) {
        return RoleTemplateRow.of(requireTemplate(id));
    }

    @Override
    @Transactional
    public RoleTemplateRow create(long companyId, SaveRoleTemplateRequest request) {
        // A new template never carries a code: codes mark the seeded roles code still looks up.
        RoleValidator.validateTemplatePermissions(null, request.permissions());
        requireNameFree(request.name(), null);
        RoleTemplate created = repository.create(RoleTemplate.custom(request.name(), request.description(),
                isAssignableByCompany(request), request.permissions()));
        audit.record(companyId, "ROLE_TEMPLATE_CREATE", "role_template", String.valueOf(created.id()), created.name());
        return RoleTemplateRow.of(created);
    }

    @Override
    @Transactional
    public RoleTemplateRow update(long companyId, long id, SaveRoleTemplateRequest request) {
        RoleTemplate existing = requireTemplate(id);
        RoleValidator.validateTemplatePermissions(existing.code(), request.permissions());
        requireNameFree(request.name(), id);
        // Existing companies keep their copies: a template only shapes companies created after this.
        RoleTemplate updated = repository.update(new RoleTemplate(id, existing.code(), request.name(),
                request.description(), isAssignableByCompany(request), request.permissions(), existing.createdAt()));
        audit.record(companyId, "ROLE_TEMPLATE_UPDATE", "role_template", String.valueOf(id), updated.name());
        return RoleTemplateRow.of(updated);
    }

    @Override
    @Transactional
    public void delete(long companyId, long id) {
        RoleTemplate existing = requireTemplate(id);
        RoleValidator.validateTemplateDeletable(existing);
        repository.delete(id);
        audit.record(companyId, "ROLE_TEMPLATE_DELETE", "role_template", String.valueOf(id), existing.name());
    }

    @Override
    public List<RoleTemplate> findTemplates() {
        return repository.findAll();
    }

    @Override
    @Transactional
    public void createDefaultTemplates() {
        // Only an empty table is seeded. The ADMIN template cannot be deleted, so once seeded the
        // table never empties again and a template the platform removed does not come back.
        if (repository.count() > 0) {
            return;
        }
        for (SystemRole systemRole : SystemRole.values()) {
            repository.create(RoleTemplate.fromSystemRole(systemRole));
        }
    }

    private RoleTemplate requireTemplate(long id) {
        RoleTemplate template = repository.find(id);
        if (template == null) {
            throw new NotFoundException(ErrorCode.ROLE_TEMPLATE_NOT_FOUND, id);
        }
        return template;
    }

    private void requireNameFree(String name, Long excludeId) {
        if (repository.existsByName(name, excludeId)) {
            throw new ConflictException(ErrorCode.ROLE_TEMPLATE_NAME_TAKEN, name);
        }
    }

    private static boolean isAssignableByCompany(SaveRoleTemplateRequest request) {
        return request.assignableByCompany() == null || request.assignableByCompany();
    }
}
