package uz.murodjon.robotcallv2.role.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.role.application.dto.PermissionGroupRow;
import uz.murodjon.robotcallv2.role.application.dto.RoleTemplateRow;
import uz.murodjon.robotcallv2.role.application.dto.SaveRoleTemplateRequest;
import uz.murodjon.robotcallv2.role.application.port.input.RoleTemplateUseCase;
import uz.murodjon.robotcallv2.role.application.port.input.RoleUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class RoleTemplateControllerImpl implements RoleTemplateController {

    private final RoleTemplateUseCase roleTemplateUseCase;
    private final RoleUseCase roleUseCase;

    public RoleTemplateControllerImpl(RoleTemplateUseCase roleTemplateUseCase, RoleUseCase roleUseCase) {
        this.roleTemplateUseCase = roleTemplateUseCase;
        this.roleUseCase = roleUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<RoleTemplateRow>>> findAll() {
        return ResponseEntity.ok(ResponseData.ok(roleTemplateUseCase.findAll()));
    }

    @Override
    public ResponseEntity<ResponseData<List<PermissionGroupRow>>> listPermissions() {
        return ResponseEntity.ok(ResponseData.ok(roleUseCase.listPermissions()));
    }

    @Override
    public ResponseEntity<ResponseData<RoleTemplateRow>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(roleTemplateUseCase.requireRow(id)));
    }

    @Override
    public ResponseEntity<ResponseData<RoleTemplateRow>> create(long companyId, SaveRoleTemplateRequest request) {
        return ResponseEntity.ok(ResponseData.ok(roleTemplateUseCase.create(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<RoleTemplateRow>> update(long companyId, long id, SaveRoleTemplateRequest request) {
        return ResponseEntity.ok(ResponseData.ok(roleTemplateUseCase.update(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        roleTemplateUseCase.delete(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
