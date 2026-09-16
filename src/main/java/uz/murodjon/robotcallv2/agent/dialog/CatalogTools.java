package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.CatalogSearchUseCase;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.CatalogMatch;

import java.util.List;

/**
 * The company's product catalog, reachable during a call (UNIVERSAL-OPERATOR-PLAN B.4).
 *
 * <p>A tool rather than prompt context: a catalog is thousands of rows, and which one the
 * caller means is known only once they have said it. The model searches with the caller's
 * words and gets the best few rows back, key first, so an order tool is handed a SKU the
 * catalog holds rather than one the model made up.
 *
 * <p>Offered only to an agent that has an indexed catalog, on both pipelines. Every figure
 * in the answer — a price, a stock count — becomes one the agent may say ({@link FactGuard}),
 * and a cascade call keeps the answer in its turn annex until the next search, because a
 * cascade turn does not see its own tool results afterwards.
 */
public class CatalogTools {

    private static final Logger log = LoggerFactory.getLogger(CatalogTools.class);

    /** Explicit on purpose: the model must ask again, not fill the gap with a product and a SKU of its own. */
    private static final String NOTHING_FOUND = "KATALOGDA TOPILMADI — mahsulot, SKU yoki narxni o'zingizdan "
            + "to'qimang. Mijozdan aniqroq so'rang (nomi, rangi, o'lchami) yoki bunday mahsulot yo'qligini ayting.";

    private static final int MAX_MATCHES = 5;
    private static final int MAX_SKU_CHARS = 100;
    private static final int MAX_ROW_CHARS = 300;

    private final DialogOutcomeSink session;
    private final long companyId;
    private final Long agentId;
    private final CatalogSearchUseCase catalogSearch;

    public CatalogTools(DialogOutcomeSink session, long companyId, Long agentId, CatalogSearchUseCase catalogSearch) {
        this.session = session;
        this.companyId = companyId;
        this.agentId = agentId;
        this.catalogSearch = catalogSearch;
    }

    @Tool(description = "Kompaniya katalogidan mahsulot yoki xizmatni qidiradi (nomi, rangi, o'lchami, narxi, "
            + "qoldig'i). Mijoz mahsulot haqida so'raganda yoki buyurtma berayotganda chaqiring. Buyurtma "
            + "tool'iga FAQAT shu yerdan qaytgan SKU ni bering — SKU, narx yoki qoldiqni o'zingizdan to'qimang.")
    public String searchCatalog(@ToolParam(description = "mijoz aytgan mahsulot: nomi va belgilari, "
            + "masalan \"qora 42 razmer krossovka\"") String query) {
        try {
            List<CatalogMatch> matches = catalogSearch.findCatalogMatches(companyId, agentId, query, MAX_MATCHES);
            if (matches.isEmpty()) {
                log.debug("[{}] catalog search for '{}' found nothing", session.channelId(), query);
                return NOTHING_FOUND;
            }
            StringBuilder answer = new StringBuilder();
            for (CatalogMatch match : matches) {
                answer.append("SKU ").append(PromptSafeText.sanitize(match.sku(), MAX_SKU_CHARS)).append(": ")
                        .append(PromptSafeText.sanitize(match.content(), MAX_ROW_CHARS)).append('\n');
            }
            String result = answer.toString();
            session.recordToolFigures(FactGuard.figuresIn(result));
            session.rememberCatalogResult(result);
            log.debug("[{}] catalog search for '{}' returned {} rows", session.channelId(), query, matches.size());
            return result;
        } catch (Exception e) {
            // A failed search must read as "nothing found", never as an error the model relays.
            log.warn("[{}] catalog search failed: {}", session.channelId(), e.getMessage());
            return NOTHING_FOUND;
        }
    }
}
