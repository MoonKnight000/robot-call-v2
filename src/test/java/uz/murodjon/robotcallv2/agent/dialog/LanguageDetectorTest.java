package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LanguageDetectorTest {

    private final LanguageDetector detector = new LanguageDetector();
    private final List<String> candidates = List.of("uz", "ru");

    @Test
    void detectsUzbekLatin() {
        assertEquals("uz", detector.detect("Assalomu alaykum, eshityapman", candidates));
        assertEquals("uz", detector.detect("Ertaga to'layman, kartaga tashlang", candidates));
        assertEquals("uz", detector.detect("Ha, men Murodjonman", candidates));
    }

    @Test
    void detectsUzbekCyrillicWithSpecialChars() {
        assertEquals("uz", detector.detect("Ҳа, мен Муроджонман", candidates));
        assertEquals("uz", detector.detect("Эртага тўлайман, раҳмат", candidates));
        assertEquals("uz", detector.detect("Қарзни тўлайман", candidates));
    }

    @Test
    void detectsUzbekCyrillicWithoutSpecialChars() {
        assertEquals("uz", detector.detect("Эртага толайман, болади", candidates));
        assertEquals("uz", detector.detect("Ха тушундим, майли", candidates));
    }

    @Test
    void detectsRussianCyrillic() {
        assertEquals("ru", detector.detect("Здравствуйте, я слушаю", candidates));
        assertEquals("ru", detector.detect("Да, я вас понял, перезвоните завтра", candidates));
        assertEquals("ru", detector.detect("Сколько денег я должен оплатить?", candidates));
        assertEquals("ru", detector.detect("Нет, я не хочу платить", candidates));
    }

    @Test
    void ignoresTooShortOrAmbiguousNoise() {
        assertNull(detector.detect("alo", candidates));
        assertNull(detector.detect("ha", candidates));
        assertNull(detector.detect("да", candidates));
        assertNull(detector.detect("bank", candidates));
        assertNull(detector.detect("karta", candidates));
    }
}
