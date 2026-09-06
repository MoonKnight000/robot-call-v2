package uz.murodjon.robotcallv2.user.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PreAuthorize("hasAuthority('USER_READ')")
    @GetMapping
    ResponseEntity<ResponseData<List<UserRow>>> list();

    @PreAuthorize("hasAuthority('USER_READ')")
    @GetMapping("/{id}")
    ResponseEntity<ResponseData<UserRow>> get(@PathVariable long id);

    @PreAuthorize("hasAuthority('USER_EDIT')")
    @PostMapping("/invite")
    ResponseEntity<ResponseData<InviteUserResponse>> invite(@Valid @RequestBody InviteUserRequest r);

    @PreAuthorize("hasAuthority('USER_EDIT')")
    @PutMapping("/{id}/role")
    ResponseEntity<ResponseData<UserRow>> changeRole(@PathVariable long id,
                                                     @Valid @RequestBody UpdateUserRoleRequest r);

    @PreAuthorize("hasAuthority('USER_EDIT')")
    @PutMapping("/{id}/block")
    ResponseEntity<ResponseData<UserRow>> block(@PathVariable long id);

    @PreAuthorize("hasAuthority('USER_EDIT')")
    @PutMapping("/{id}/unblock")
    ResponseEntity<ResponseData<UserRow>> unblock(@PathVariable long id);
}
