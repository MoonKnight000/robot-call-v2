package uz.murodjon.robotcallv2.user.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserRequest;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserResponse;
import uz.murodjon.robotcallv2.user.application.dto.UpdateUserRoleRequest;
import uz.murodjon.robotcallv2.user.application.dto.UserRow;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.util.List;

@RestController
public class UserControllerImpl implements UserController {

    private final UserUseCase userUseCase;

    public UserControllerImpl(UserUseCase userUseCase) {
        this.userUseCase = userUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<UserRow>>> list() {
        return ResponseEntity.ok(ResponseData.ok(userUseCase.list()));
    }

    @Override
    public ResponseEntity<ResponseData<UserRow>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(userUseCase.get(id)));
    }

    @Override
    public ResponseEntity<ResponseData<InviteUserResponse>> invite(InviteUserRequest r) {
        return ResponseEntity.ok(ResponseData.ok(userUseCase.invite(r)));
    }

    @Override
    public ResponseEntity<ResponseData<UserRow>> changeRole(long id, UpdateUserRoleRequest r) {
        return ResponseEntity.ok(ResponseData.ok(userUseCase.changeRole(id, r)));
    }

    @Override
    public ResponseEntity<ResponseData<UserRow>> block(long id) {
        return ResponseEntity.ok(ResponseData.ok(userUseCase.setStatus(id, UserStatus.BLOCKED)));
    }

    @Override
    public ResponseEntity<ResponseData<UserRow>> unblock(long id) {
        return ResponseEntity.ok(ResponseData.ok(userUseCase.setStatus(id, UserStatus.ACTIVE)));
    }
}
