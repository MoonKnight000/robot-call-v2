package uz.murodjon.robotcallv2.knowledgebase.application.port.output;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

/** Reads a catalog feed as rows of column → value (UNIVERSAL-OPERATOR-PLAN B.4). */
public interface CatalogFeedReaderPort {

    /** An uploaded feed: XLSX/XLS or JSON by its file name, CSV otherwise. The first row names the columns. */
    List<Map<String, String>> parseRows(InputStream content, String fileName);

    /**
     * A feed fetched from {@code uri}: a JSON array of objects — at {@code itemsPath}, a
     * JSONPath such as {@code $.data.items}; null for the whole answer — or a CSV.
     */
    List<Map<String, String>> fetchRows(URI uri, String itemsPath);
}
