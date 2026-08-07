package uz.murodjon.uysotvoice.user.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.uysotvoice.shared.api.ResponseData;
import uz.murodjon.uysotvoice.user.dto.InviteUserRequest;
import uz.murodjon.uysotvoice.user.dto.InviteUserResponse;
import uz.murodjon.uysotvoice.user.dto.UpdateUserRoleRequest;
import uz.murodjon.uysotvoice.user.dto.UserRow;

import java.util.List;

/** Foydalanuvchi boshqaruvi (ROADMAP E.1, UI-DESIGN §10.12) — faqat ADMIN. */
@RequestMapping("/api/users")
public interface UserController {

    @GetMapping
    ResponseEntity<ResponseData<List<UserRow>>> list();

    @PostMapping("/invite")
    ResponseEntity<ResponseData<InviteUserResponse>> invite(@Valid @RequestBody InviteUserRequest r);

    @PutMapping("/{id}/role")
    ResponseEntity<ResponseData<UserRow>> changeRole(@PathVariable long id, @Valid @RequestBody UpdateUserRoleRequest r);

    @PostMapping("/{id}/block")
    ResponseEntity<ResponseData<UserRow>> block(@PathVariable long id);

    @PostMapping("/{id}/unblock")
    ResponseEntity<ResponseData<UserRow>> unblock(@PathVariable long id);
}
