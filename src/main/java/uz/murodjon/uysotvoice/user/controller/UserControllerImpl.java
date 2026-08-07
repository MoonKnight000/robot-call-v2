package uz.murodjon.uysotvoice.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.shared.api.ResponseData;
import uz.murodjon.uysotvoice.user.dto.InviteUserRequest;
import uz.murodjon.uysotvoice.user.dto.InviteUserResponse;
import uz.murodjon.uysotvoice.user.dto.UpdateUserRoleRequest;
import uz.murodjon.uysotvoice.user.dto.UserRow;
import uz.murodjon.uysotvoice.user.service.UserService;

import java.util.List;

@RestController
public class UserControllerImpl implements UserController {

    private final UserService service;

    public UserControllerImpl(UserService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<List<UserRow>>> list() {
        return ResponseEntity.ok(ResponseData.ok(service.list()));
    }

    @Override
    public ResponseEntity<ResponseData<InviteUserResponse>> invite(InviteUserRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.invite(r)));
    }

    @Override
    public ResponseEntity<ResponseData<UserRow>> changeRole(long id, UpdateUserRoleRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.changeRole(id, r.role())));
    }

    @Override
    public ResponseEntity<ResponseData<UserRow>> block(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.block(id)));
    }

    @Override
    public ResponseEntity<ResponseData<UserRow>> unblock(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.unblock(id)));
    }
}
