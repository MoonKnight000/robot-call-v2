package uz.murodjon.robotcallv2.pack.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.pack.domain.entity.VerticalPack;

/**
 * A pack that is not in the catalog — the generated draft, once a person has read it
 * (UNIVERSAL-OPERATOR-PLAN E.1).
 *
 * <p>The pack comes back from the client rather than being held on the server between the two
 * calls: a draft is edited before it is installed, and what is installed has to be what the
 * person saw, not what the model first wrote.
 *
 * @param catalogUrl the company's product feed, as in a catalog pack's install
 */
public record InstallDraftRequest(
        @NotNull VerticalPack pack,
        @Size(max = 2048) String catalogUrl,
        @Size(max = 200) String catalogItemsPath
) {
}
