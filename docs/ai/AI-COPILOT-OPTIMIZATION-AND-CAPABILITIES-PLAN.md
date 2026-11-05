# AI Copilot (Platforma Yordamchisi): Optimizatsiya, Imkoniyatlar va Token Tejamkorligi Rejasi

> **Hujjat maqsadi:** Ushbu hujjat platformamizning boshqaruv panelidagi AI Copilot tizimini (`uz.murodjon.robotcallv2.copilot`) optimallashtirish, token xarajatlarini **70–85% ga qisqartirish**, 20–30 soniyalik kutish o'rniga **real vaqtda SSE oqimli (streaming) javob** berish va uni platformaning barcha amallarini mukammal bajaruvchi **"Avtonom Administrator"** ga aylantirish bo'yicha to'liq tadqiqot va amaliy rejadir.
> 
> Tahlil dunyodagi eng ilg'or B2B Copilotlar (**Cursor Agent**, **Supabase Studio AI**, **Retool AI**, **GitHub Copilot Workspace**, **Decagon**) arxitekturasi asosida tayyorlangan.

---

## 1. MAVJUD HOLAT VA TO'SIQLAR TAHLILI

Hozirgi `copilot` moduli (`docs/api/copilot.md`, `CopilotService.java`) juda yaxshi poydevorga ega (xavfsiz drafts-only rejim, proposal tizimi, huquqlar tekshiruvi mavjud). Biroq foydalanuvchi tajribasi va token sarfida 4 ta jiddiy to'siq bor:

```
                  ┌────────────────────────────────────────────────────────┐
                  │                 COPILOT ASOSIY TO'SIQLARI              │
                  └───────────────────────────┬────────────────────────────┘
                                              │
         ┌────────────────────────────────────┼────────────────────────────────────┐
         ▼                                    ▼                                    ▼
1. TOOL BLOAT (ORTIQCHA TOKEN)        2. BLOKLANUVCHI KUTISH               3. XOM JSON TARIXI
Har bir savolda barcha 40 ta MCP      Javob faqat hamma tool tugagach      Eski savollardagi 12,000 belgilik
tool schemalari yuboriladi            qaytadi (15-30 soniya o'lik          xom JSON natijalar tarixda qayta-qayta
(15,000 - 18,000 kirish tokeni!).     spinner aylanib turadi).             yuborilib, kontekstni to'ldiradi.
```

### Biz erishishimiz kerak bo'lgan maqsadli ko'rsatkichlar:
* **Kiruvchi tokenlar soni:** Bir so'rovda 18,000 tokendan **2,000–3,500 tokengacha qisqarishi** (80% tejamkorlik!).
* **Foydalanuvchi birinchi belgini ko'rish vaqti (TTFT):** 20 soniyadan **800–1,200 ms** ga tushishi (SSE streaming hisobiga).
* **Amallarni bajarish qobiliyati:** Murakkab agent va kampaniyalarni bitta buyruq bilan to'liq qoralama qilib berish.
* **Tushuntirish sifati:** Shunchaki xom matn emas, balki **Diff (oldin/keyin)**, jadvallar va aniq qo'ng'iroq ID'lari bilan vizual asoslash.

---

## 2. DUNYO BOZORIDAGI YETAKCHI COPILOTLAR TAHLILI (BENCHMARK)

| Platforma | Asosiy Arxitektura Usuli | Biz Loyihamizga Nimasini Olishimiz Kerak? |
|---|---|---|
| **Cursor / Copilot Workspace** | **Dynamic Tool Pruning & State Streaming:** Modelga barcha 100 ta funksiya berilmaydi. Fayl/sahifa kontekstiga qarab faqat 3-5 ta eng kerakli tool beriladi. Javob harfi-harfiga oqib keladi. | `context.page` ga qarab faqat shu sahifaga tegishli tool'larni yuborish. |
| **Supabase Studio AI** | **Schema & Entity Minification:** SQL yoki JSON natijalari modelga yuborilishidan oldin siqiladi (ortiqcha metama'lumotlar o'chiriladi). | Tool javobidagi 50 ta ustun o'rniga faqat kerakli 4-5 ta maydonni qoldirish. |
| **Retool AI** | **Human-in-the-loop Proposals:** Har qanday o'zgartirish UI kartochka sifatida chiqadi va "Apply" tugmasi bosiladi (bizdagi proposalStatus bilan bir xil). | Foydalanuvchiga taklif qilingan o'zgarishlarni chiroyli vizual kartochkada ko'rsatish. |
| **Decagon** | **Deep Attribution & Call Insights:** "Nega bunday bo'ldi?" savoliga aniq 3-4 ta transkript parchasini keltirib isbotlaydi. | Tahlillarda aniq qo'ng'iroqlar havolasini (`/calls/{id}`) keltirish. |

---

## 3. 4 TA ASOSIY ARXITEKTURA USTUNI

```
┌────────────────────────────────────────────────────────────────────────┐
│             USTUN 1: KONTEKSTGA QARAB DINAMIK TOOL FILTRLASH           │
│  Sahifa: agent-editor -> Faqat Agent & Scenario Toollar (5 ta)        │
│  Sahifa: reports      -> Faqat Analytics & Call Toollar (4 ta)        │
│  (15,000 tokenlik tool schemasi -> 2,500 tokenga tushadi!)             │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│             USTUN 2: TARIX VA TOOL NATIJALARINI SIQISH                 │
│  Eski turnlardagi xom JSON javoblar o'chiriladi, faqat xulosa qoladi.   │
│  Yangi tool natijalari esa faqat kerakli maydonlar bilan siqiladi.     │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│             USTUN 3: REAL-TIME SSE STREAMING (FIKR OQIMI)              │
│  Foydalanuvchi kutmaydi:                                               │
│  [Qidirilmoqda...] -> [Tool: get_call_stats] -> [Tahlil matni oqadi...] │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│             USTUN 4: AVTONOM QORALAMA VA DIFF VIZUALIZATSIYASI         │
│  - "Menga yangi qarz agentini yarat" -> Barcha bosqichlar qoralamasi.  │
│  - Prompt o'zgarganda -> Aniq Diff (qizil: o'chgan, yashil: qo'shilgan)│
└────────────────────────────────────────────────────────────────────────┘
```

---

### USTUN 1: Kontekstga Qarab Dinamik Tool Filtrlash (Context-Aware Tool Selection)

#### Muammo:
Hozir `CopilotService` har bir savolda `mcpServerUseCase.findServerTools()` orqali barcha 40 ta tool'ni yuklaydi.
Agar foydalanuvchi *"Agent promptini muloyimroq qil"* desa, unga SIP trunk, analitika, kampaniya kontaktlari tool'larining keragi yo'q!

#### Yechim:
Bizning API ga allaqachon `context: { page: "agent-editor", agentId: 4 }` keladi. Shundan foydalanib, tool'larni domenlar bo'yicha guruhlaymiz:

```java
public enum CopilotToolDomain {
    AGENT_BUILDER(Set.of("get_agent", "update_agent_draft", "create_agent", "list_scenarios", "get_scenario", "run_evaluation")),
    ANALYTICS_CALLS(Set.of("get_call_stats", "compare_periods", "list_calls", "get_call", "get_call_topics", "get_stage_funnel")),
    CAMPAIGN_OPS(Set.of("get_campaign", "update_campaign", "create_campaign", "list_campaigns", "import_contacts")),
    KNOWLEDGE_BASE(Set.of("search_knowledge", "get_knowledge_article", "create_knowledge_article")),
    GENERAL_HELP(Set.of("get_platform_info", "list_agents", "list_campaigns"));
}
```

* **Qoida:**
  * Agar `page == "agent-editor"` bo'lsa: Faqat `AGENT_BUILDER` + `KNOWLEDGE_BASE` toollari (jami 7-8 ta) yuboriladi.
  * Agar `page == "reports"` yoki `"calls"` bo'lsa: Faqat `ANALYTICS_CALLS` toollari (6 ta) yuboriladi.
  * Agar aniq bo'lmasa yoki umumiy savol bo'lsa: Kichik Semantic Intent Matcher orqali kerakli 1-2 ta guruh tanlanadi.
* **Natija:** Tool ta'riflari uchun ketadigan tokenlar soni **15,000 tokendan 2,500 tokenga tushadi (85% tejamkorlik!)**.

---

### USTUN 2: Tarixni Tozalash va Xom Tool Chiqishlarini Siqish (Tool Result Minification)

#### A. O'tgan Suhbat Tarixidan Xom JSON'larni Tozalash
Suhbat davom etar ekan, oldingi savollarda chaqirilgan tool'larning 10,000 belgilik xom JSON natijalari chat tarixida qolib ketadi:
* Keyingi savolda modelga o'sha xom JSON larni qayta yuborish mutlaqo shart emas! Chunki o'tgan savol javobida (`ASSISTANT` xabarida) ularning xulosasi allaqachon yozilgan.
* **Yechim:** Tarixni (`history`) modelga yuborishdan oldin, faqat oxirgi turndagi tool javobini qoldirib, undan oldingi `TOOL` rolli xabarlarning `content` ini `"[Ma'lumotlar o'rganildi va yuqoridagi javobda aks ettirildi]"` deb almashtiramiz.
* **Bu har bir qo'shimcha savolda 8,000–12,000 tokenni tejaydi!**

#### B. Tool Natijalarini Loyihalash (Projection / Stripping)
Masalan, `list_calls` chaqirilganda, bazadan 50 ta qo'ng'iroqning audio byte offsetlari, SIP ID lari, webhook urinishlari kabi ulkan texnik maydonlar qaytadi.
Copilot tahlili uchun esa faqat quyidagilar kerak:
```json
// Tozalangan ixcham format:
[
  {"id": 8812, "disposition": "TRANSFERRED", "duration": 45, "stage": "PAYMENT_AGREE", "reason": "operator_talab_qildi"},
  {"id": 8830, "disposition": "TRANSFERRED", "duration": 32, "stage": "OBJECTION", "reason": "narx_bilmadi"}
]
```
Xom JSON hajmi 12,000 belgidan **800 belgiga (15 baravar!)** qisqaradi.

---

### USTUN 3: Real-Time SSE Streaming (Fikr Oqimi va Tezlik)

Hozirgi 25 soniyalik o'lik kutish (loading spinner) o'rniga foydalanuvchiga real vaqtda nima sodir bo'layotgani ko'rsatiladi:

```
Vaqt:
0.5s  ──► [SSE: status] "Savolingiz tahlil qilinmoqda..."
1.2s  ──► [SSE: tool_start] "Oxirgi 7 kunlik qo'ng'iroqlar solishtirilmoqda (compare_periods)..."
2.5s  ──► [SSE: tool_done] "Qo'ng'iroqlar statistikasi olindi."
2.8s  ──► [SSE: text_chunk] "Sotuv"
2.9s  ──► [SSE: text_chunk] " agentida (ID 4)"
3.0s  ──► [SSE: text_chunk] " transferlar soni 9% dan 21% ga oshgan..."
```

* **Yutuq:**
  * Foydalanuvchi 20 soniya bo'sh ekranga qarab turmaydi, birinchi so'zlarni **2-3 soniyada** ko'rishni boshlaydi.
  * Tizim qotib qolmagani, qaysi tool'lar ishlayotgani shaffof ko'rinadi.

---

### USTUN 4: Avtonom Qoralamalar va Chuqur Tushuntirish (Explainability & Agency)

Bizning Copilot shunchaki maslahat beruvchi bo'lib qolmasdan, haqiqiy yordamchi bo'lishi kerak:

#### A. Prompt O'zgarishlarini Diff Ko'rinishida Ko'rsatish
Foydalanuvchi *"Agent promptini yanada muloyim va qisqa qil"* deganida, Copilot yangi promptni oddiy matn qilib tashlamaydi. U Markdown Diff formatida ko'rsatadi:
```diff
- Siz qattiqqo'l bank xodimisiz. Mijozdan darhol to'lovni talab qiling.
+ Siz samimiy va xushmuomala maslahatchisiz. Mijozning holatini tushunib, bo'lib to'lashni taklif qiling.
```
Va ostida bitta tugma: **[Qoralamaga Qo'llash (Apply)]**.

#### B. Dalillarga Asoslangan Tahlil (Attribution Links)
Copilot hech qachon havodan olib gapirmaydi:
> *"Transferlar 12% ga oshgan. Asosiy sabab: mijozlar 'yetkazib berish narxi'ni so'raganida agent bilmay operatorga ulamoqda.  
> Dalil sifatida mana bu qo'ng'iroqlarni eshitib ko'rishingiz mumkin:  
> • [Qo'ng'iroq #8812](/calls/8812) — 00:45 da narx so'ralgan  
> • [Qo'ng'iroq #8830](/calls/8830) — 00:32 da operator talab qilingan  
> **Mening taklifim:** Bilimlar bazasiga 'Yetkazib berish narxlari' maqolasini qo'shish. [Yaratishni tasdiqlaysizmi?]"*

---

## 4. `robot-call-v2` UCHUN KOD DARAJASIDAGI O'ZGARISHLAR

Quyidagi komponentlar mavjud arxitekturaga mos ravishda joriy etiladi:

### 1. `CopilotToolSelector.java` (`application/service/`)
Mavjud `mcpServerUseCase.findServerTools()` natijasini `context.page()` ga qarab filtrlaydigan xizmat:
```java
@Component
public class CopilotToolSelector {
    public List<McpTool> selectToolsForContext(CopilotPageContext context, List<McpTool> allTools) {
        if (context == null || context.page() == null) {
            return filterGeneralTools(allTools);
        }
        return switch (context.page()) {
            case "agent-editor", "scenario-editor" -> filterForAgentBuilding(allTools);
            case "reports", "calls", "call-detail" -> filterForAnalytics(allTools);
            case "campaign", "campaigns" -> filterForCampaigns(allTools);
            default -> filterGeneralTools(allTools);
        };
    }
}
```

### 2. `CopilotHistoryOptimizer.java` (`application/service/`)
Tarixdagi eski va ulkan tool javoblarini siquvchi tozalagich:
```java
@Component
public class CopilotHistoryOptimizer {
    public List<CopilotMessage> pruneToolOutputs(List<CopilotMessage> fullHistory) {
        // Faqat eng oxirgi 1-2 ta tool natijasi qoldiriladi, qolgan eski tool chaqiruvlari siqiladi
    }
}
```

### 3. SSE Streaming Controller: `GET /api/copilot/ask/stream`
`SseEmitter` orqali frontendga progress va matn bo'laklarini jonli uzatish.

---

## 5. QIYOSIY TAQQOSLASH (BEFORE vs AFTER)

| Ko'rsatkich | Mavjud Copilot | Optimizatsiyadan Keyin | Tejamkorlik / Samaradorlik |
|---|---|---|---|
| **Kiruvchi Tokenlar (Bitta so'rovda)** | 15,000 – 19,000 token | **2,200 – 3,500 token** | **~82% tejamkorlik** |
| **5-turnlik Suhbatdagi Tokenlar** | 60,000 – 80,000 token | **12,000 – 15,000 token** | **4-5 baravar kam xarajat** |
| **Kutish Vaqti (Foydalanuvchi his qiladigan)** | 15 – 25 soniya (spinner) | **1 – 2 soniya** (SSE streaming boshlanishi) | **10x tezroq taassurot** |
| **Tool Natijasi JSON Hajmi** | 12,000 belgigacha xom matn | **500 – 1,000 belgi** (Minified Projection) | **Toza va xatosiz tahlil** |
| **Foydalanuvchi Qulayligi** | Faqat matnli javob | **Diff, jadvallar, bosiladigan linklar, 1-click Apply** | **Mukammal Admin Tajribasi** |

---

## 6. BOSQICHMA-BOSQICH AMALGA OSHIRISH REJASI

* [ ] **1-qadam (Eng katta token tejami — 1-kun):** `CopilotToolSelector` ni joriy qilish va `CopilotService` da `context.page` bo'yicha faqat tegishli tool'larni yuborish.
* [ ] **2-qadam (Tarixni tozalash — 1-kun):** `CopilotHistoryOptimizer` orqali eski so'rovlardagi ulkan xom JSON larni qisqartirish.
* [ ] **3-qadam (Streaming UX — 2-kun):** `CopilotController` ga SSE streaming (`/api/copilot/ask/stream`) endpointini qo'shish va oraliq holatlarni mijozga uzatish.
* [ ] **4-qadam (Vizual boyitish — 2-kun):** Prompt rewrite va tahlil javoblarida Markdown Diff va qo'ng'iroq ID lari bilan chuqur asoslash qoidalarini mustahkamlash.
