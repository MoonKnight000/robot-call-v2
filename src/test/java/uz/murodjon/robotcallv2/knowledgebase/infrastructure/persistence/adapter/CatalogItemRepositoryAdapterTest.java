package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.adapter;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The two translations the catalog store makes before a query reaches Postgres — no database. */
class CatalogItemRepositoryAdapterTest {

    @Test
    void wordsGatherByTheirFirstFiveLettersAndNumbersWhole() {
        assertThat(CatalogItemRepositoryAdapter.toTsQuery(List.of("qora", "42", "krossovkalar")))
                .isEqualTo("qora:* | 42 | kross:*");
    }

    @Test
    void aLongerVectorIsCutToTheColumnAndNormalised() {
        float[] vector = new float[3072];
        Arrays.fill(vector, 1f);

        String literal = CatalogItemRepositoryAdapter.toVectorLiteral(vector);

        String[] values = literal.substring(1, literal.length() - 1).split(",");
        assertThat(values).hasSize(CatalogItemRepositoryAdapter.DIMENSIONS);
        assertThat(Float.parseFloat(values[0])).isCloseTo((float) (1 / Math.sqrt(768)), within(1e-6f));
    }

    @Test
    void aShorterOrMissingVectorIsNotStored() {
        assertThat(CatalogItemRepositoryAdapter.toVectorLiteral(new float[256])).isNull();
        assertThat(CatalogItemRepositoryAdapter.toVectorLiteral(null)).isNull();
    }
}
