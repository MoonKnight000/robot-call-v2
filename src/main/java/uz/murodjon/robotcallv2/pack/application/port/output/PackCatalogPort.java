package uz.murodjon.robotcallv2.pack.application.port.output;

import uz.murodjon.robotcallv2.pack.domain.entity.VerticalPack;

import java.util.List;

/** The packs this build ships with. Read-only: a pack is edited in its file, not through the API. */
public interface PackCatalogPort {

    List<VerticalPack> findAll();

    /** The pack under {@code key}, or null when there is none. */
    VerticalPack findByKey(String key);
}
