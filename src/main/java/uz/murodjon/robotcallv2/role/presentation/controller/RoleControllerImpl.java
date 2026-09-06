package uz.murodjon.robotcallv2.role.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.role.application.dto.CreateRoleRequest;
import uz.murodjon.robotcallv2.role.application.dto.PermissionGroupRow;
import uz.murodjon.robotcallv2.role.application.dto.RoleRow;
import uz.murodjon.robotcallv2.role.application.dto.UpdateRoleRequest;
import uz.murodjon.robotcallv2.role.application.port.input.RoleUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class RoleControllerImpl implements RoleController {

    private final RoleUseCase roleUseCase;

    public RoleControllerImpl(RoleUseCase roleUseCase) {
        this.roleUseCase = roleUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<RoleRow>>> list() {
        return ResponseEntity.ok(ResponseData.ok(roleUseCase.listForCurrentCompany()));
    }

    @Override
    public ResponseEntity<ResponseData<List<PermissionGroupRow>>> listPermissions() {
        return ResponseEntity.ok(ResponseData.ok(roleUseCase.listPermissions()));
    }

    @Override
    public ResponseEntity<ResponseData<RoleRow>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(roleUseCase.get(id)));
    }

    @Override
    public ResponseEntity<ResponseData<RoleRow>> create(CreateRoleRequest request) {
        return ResponseEntity.ok(ResponseData.ok(roleUseCase.create(request)));
    }

    @Override
    public ResponseEntity<ResponseData<RoleRow>> update(long id, UpdateRoleRequest request) {
        return ResponseEntity.ok(ResponseData.ok(roleUseCase.update(id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long id) {
        roleUseCase.delete(id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
