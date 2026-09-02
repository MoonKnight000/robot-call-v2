package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * In-memory fast Knowledge Base (RAG) for handling frequent legal, financial,
 * and operational objections during debtor calls.
 */
@Service
public class KnowledgeBaseService {

    public record KnowledgeItem(String key, String topic, String answerUz, String answerRu, String answerEn) {
    }

    private static final List<KnowledgeItem> KNOWLEDGE_BASE = List.of(
            new KnowledgeItem(
                    "payment_methods",
                    "payment",
                    "To'lovni Click, Payme, Uzum Bank ilovalari orqali yoki bank kassalarida shartnoma raqamingizni ko'rsatib amalga oshirishingiz mumkin.",
                    "Оплату можно произвести через приложения Click, Payme, Uzum Bank или в кассах банков, указав номер договора.",
                    "You can make payments via Click, Payme, Uzum Bank mobile apps or at bank branches using your contract number."
            ),
            new KnowledgeItem(
                    "court_mib",
                    "legal",
                    "To'lov kechiktirilsa, qonunchilikka asosan ish Majburiy ijro byurosiga (MIB) yoki sudga oshirilishi va hisob raqamlarga taqiq qo'yilishi mumkin.",
                    "В случае задержки оплаты дело в соответствии с законом может быть передано в БПИ или суд с наложением ареста на счета.",
                    "In case of prolonged non-payment, the case may be escalated to the enforcement bureau or court with account freezes."
            ),
            new KnowledgeItem(
                    "restructuring",
                    "terms",
                    "Agar moliyaviy qiyinchilik bo'lsa, bank filialiga ariza bilan murojaat qilib, to'lov muddatini uzaytirish yoki qayta ko'rib chiqishni so'rashingiz mumkin.",
                    "При финансовых трудностях вы можете обратиться в филиал банка с заявлением о реструктуризации или продлении срока долга.",
                    "If experiencing financial distress, you can visit a branch to request loan restructuring or installment adjustments."
            ),
            new KnowledgeItem(
                    "branch_locations",
                    "locations",
                    "Barcha filiallar dushanbadan jumagacha soat 9:00 dan 18:00 gacha ishlaydi. Eng yaqin filialni rasmiy veb-saytdan topishingiz mumkin.",
                    "Все филиалы работают с понедельника по пятницу с 9:00 до 18:00. Ближайший филиал можно найти на официальном сайте.",
                    "All branches operate Monday through Friday from 9:00 to 18:00. The nearest branch can be found on our official website."
            )
    );

    /**
     * Finds the most relevant knowledge snippet for the given caller question or objection.
     */
    public String findRelevantKnowledge(String query, String language) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        boolean isRu = language != null && language.startsWith("ru");
        boolean isEn = language != null && language.startsWith("en");

        for (KnowledgeItem item : KNOWLEDGE_BASE) {
            if (matchesTopic(lower, item.key())) {
                if (isEn) return item.answerEn();
                if (isRu) return item.answerRu();
                return item.answerUz();
            }
        }
        return null;
    }

    private static boolean matchesTopic(String query, String key) {
        return switch (key) {
            case "payment_methods" -> query.contains("click") || query.contains("payme") || query.contains("to'lash")
                    || query.contains("qayerga") || query.contains("qanday to'layman") || query.contains("оплатить")
                    || query.contains("как оплатить") || query.contains("how to pay");
            case "court_mib" -> query.contains("mib") || query.contains("sud") || query.contains("qonun")
                    || query.contains("sudga") || query.contains("бпи") || query.contains("суд") || query.contains("court");
            case "restructuring" -> query.contains("bo'lib to'lash") || query.contains("imtiyoz") || query.contains("sharoit")
                    || query.contains("qiyin") || query.contains("рассрочка") || query.contains("реструктуризация");
            case "branch_locations" -> query.contains("filial") || query.contains("manzil") || query.contains("ofis")
                    || query.contains("филиал") || query.contains("адрес") || query.contains("branch") || query.contains("office");
            default -> false;
        };
    }
}
