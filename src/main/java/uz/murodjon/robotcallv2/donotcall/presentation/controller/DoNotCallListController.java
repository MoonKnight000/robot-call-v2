package uz.murodjon.robotcallv2.donotcall.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallFilter;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallRemoveResponse;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallRow;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/** The "Qo'ng'iroq qilinmasin (DNC)" tab (§10.8) — not campaign-scoped, so it is its own API. */
@RequestMapping("/api")
@PreAuthorize("hasAuthority('ADMIN')")
public interface DoNotCallListController {

    @PostMapping("/do-not-call/list")
    ResponseEntity<ResponseData<PageableData<DoNotCallRow>>> list(@Valid @RequestBody DoNotCallFilter filter);

    @PostMapping("/do-not-call/{phone}/remove")
    ResponseEntity<ResponseData<DoNotCallRemoveResponse>> remove(@PathVariable String phone);
}
