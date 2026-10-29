package uz.murodjon.robotcallv2.user.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.role.application.dto.RoleRow;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserRequest;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserResponse;
import uz.murodjon.robotcallv2.user.application.dto.UpdateUserRoleRequest;
import uz.murodjon.robotcallv2.user.application.dto.UserRow;
import uz.murodjon.robotcallv2.user.application.port.input.PlatformUserUseCase;

import java.util.List;

@RestController
public class PlatformUserControllerImpl implements PlatformUserController {

    private final PlatformUserUseCase platformUserUseCase;

    public PlatformUserControllerImpl(PlatformUserUseCase platformUserUseCase) {
        this.platformUserUseCase = platformUserUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<UserRow>>> list(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(platformUserUseCase.list(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<UserRow>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(platformUserUseCase.requireUserRow(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<List<RoleRow>>> findRoles(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(platformUserUseCase.findRoles(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<InviteUserResponse>> invite(long companyId, InviteUserRequest request) {
        return ResponseEntity.ok(ResponseData.ok(platformUserUseCase.invite(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<UserRow>> changeRole(long companyId, long id, UpdateUserRoleRequest request) {
        return ResponseEntity.ok(ResponseData.ok(platformUserUseCase.changeRole(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<UserRow>> block(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(platformUserUseCase.block(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<UserRow>> unblock(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(platformUserUseCase.unblock(companyId, id)));
    }
}
