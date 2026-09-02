package uz.murodjon.robotcallv2.donotcall.application.dto;

/** Response for {@code POST /api/do-not-call/{phone}/remove} (§10.8). */
public record DoNotCallRemoveResponse(String phone, boolean removed) {
}
