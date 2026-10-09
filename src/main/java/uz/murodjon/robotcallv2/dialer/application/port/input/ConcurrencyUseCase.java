package uz.murodjon.robotcallv2.dialer.application.port.input;

import uz.murodjon.robotcallv2.dialer.application.dto.ConcurrencyResponse;

/** How many dialer calls a company may hold at once, and how many it holds now. */
public interface ConcurrencyUseCase {

    ConcurrencyResponse findConcurrency(long companyId);
}
