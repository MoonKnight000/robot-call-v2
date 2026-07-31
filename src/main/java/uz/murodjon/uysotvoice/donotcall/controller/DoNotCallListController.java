package uz.murodjon.uysotvoice.donotcall.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallFilter;
import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallRemoveResponse;
import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallRow;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

/** The "Qo'ng'iroq qilinmasin (DNC)" tab (§10.8) — not campaign-scoped, so it is its own API. */
@RequestMapping("/api")
public interface DoNotCallListController {

    @PostMapping("/do-not-call/list")
    ResponseEntity<ResponseData<PageableData<DoNotCallRow>>> list(@Valid @RequestBody DoNotCallFilter filter);

    @PostMapping("/do-not-call/{phone}/remove")
    ResponseEntity<ResponseData<DoNotCallRemoveResponse>> remove(@PathVariable String phone);
}
