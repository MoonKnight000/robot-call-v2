package uz.murodjon.robotcallv2.search.application.port.input;

import uz.murodjon.robotcallv2.search.application.dto.SearchResult;

/** Inbound UseCase port for Command Palette searching (§11.9). */
public interface SearchUseCase {

    SearchResult search(long companyId, String q);
}
