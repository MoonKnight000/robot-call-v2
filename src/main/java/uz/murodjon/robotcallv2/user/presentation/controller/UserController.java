package uz.murodjon.robotcallv2.user.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserRequest;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserResponse;
import uz.murodjon.robotcallv2.user.application.dto.UpdateUserRoleRequest;
import uz.murodjon.robotcallv2.user.application.dto.UserRow;

import java.util.List;

/**
 * User management API (ROADMAP E.1).
 */
@RequestMapping("/api/users")
public interface UserController {

    @GetMapping
    ResponseEntity<ResponseData<List<UserRow>>> list();

    @GetMapping("/{id}")
    ResponseEntity<ResponseData<UserRow>> get(@PathVariable long id);

    @PostMapping("/invite")
    ResponseEntity<ResponseData<InviteUserResponse>> invite(@Valid @RequestBody InviteUserRequest r);

    @PutMapping("/{id}/role")
    ResponseEntity<ResponseData<UserRow>> changeRole(@PathVariable long id,
                                                     @Valid @RequestBody UpdateUserRoleRequest r);

    @PutMapping("/{id}/block")
    ResponseEntity<ResponseData<UserRow>> block(@PathVariable long id);

    @PutMapping("/{id}/unblock")
    ResponseEntity<ResponseData<UserRow>> unblock(@PathVariable long id);
}
