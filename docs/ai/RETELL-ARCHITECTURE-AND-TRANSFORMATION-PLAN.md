# Retell AI Arxitekturasi, Shablonlari va Robot-Call-v2 Transformatsiya Rejasi

> **Hujjat maqsadi:** Ushbu hujjat Retell AI (`retellai.com`) platformasining arxitekturasi, agent turlari (Single Prompt vs Conversational Flow), aloqa kanallari (Voice vs Chat), 8 ta asosiy default shablonlarining to'liq tahlili hamda `robot-call-v2` loyihasini Retell darajasiga olib chiqish bo'yicha to'liq refaktoring va rivojlantirish rejasini (Plan) o'z ichiga oladi.

---

# 1-QISM: RETELL AI QANDAY ISHLAYDI? (TO'LIQ DOKUMENTATSIYA TAHLILI)

Retell AI — bugungi kunda dunyodagi eng ilg'or Conversational Voice & Chat AI platformasi hisoblanadi. Uning rasmiy hujjatlari (`docs.retellai.com`) asosida quyida uning to'liq ichki arxitekturasi va barcha imkoniyatlari bayon etiladi:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                RETELL AI TO'LIQ ARXITEKTURASI                          │
├────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                        │
│   [ 1. AGENT TURLARI (AGENT ARCHITECTURE) ]                                            │
│   • Single-Prompt Agent: Bitta yaxlit prompt, moslashuvchan, tavsiya etilgan boshlang'ich│
│   • Conversation Flow Agent: Vizual Graf, qat'iy FSM, Node'lar, Edge'lar, Subflow'lar  │
│   • Multi-Prompt (Legacy): Eskirgan (Deprecated) va yangi oqimlarga ko'chirilgan       │
│                                                                                        │
│   [ 2. BASIC SETTINGS & SPEECH SYNTHESIS ]                                             │
│   • Language Model: LLM provider, temperature, suggested fast-latency models           │
│   • Voice & Speed: 0.5x - 2.0x, Dynamic Speed Adjustment (mijoz sur'atiga moslashish)  │
│   • Welcome Message: AI speaks first (Static/Dynamic) YOKI User speaks first           │
│   • Pause Before Speaking & Responsiveness: 0 dan 1 gacha kutilma sozlamasi            │
│   • Ambient Sound: "coffee-shop", "call-center", "office" fon shovqinlari              │
│   • Backchanneling: "aha", "tushundim", "ha" qisqa oraliq tasdiqlar                    │
│   • Silence Timeout: Mijoz jim qolsa eslatma jumlalari aytish                          │
│                                                                                        │
│   [ 3. FUNCTIONS & TOOL CALLING ]                                                      │
│   • Transfer Call: E.164, SIP URI, runtime variables, extension (123#)                 │
│   • Agent Transfer: Bir AI agentdan ikkinchisiga to'liq suhbat tarixi bilan uzatish   │
│   • End Call: Qat'iy shartlar va xayrlashuvda go'shakni qo'yish                        │
│   • Press Digit: Tashqi IVR robot menyularini yorib o'tish (DTMF tones)                │
│   • Custom Functions: Tashqi REST API Webhook'lar (JSON Schema)                        │
│                                                                                        │
│   [ 4. GUARDRAILS & COMPLIANCE ]                                                       │
│   • Real-time Guardrails: Taqiqlangan mavzularda suhbatni uzmasdan Safe Message berish │
│   • Do-Not-Call (DNC): "Boshqa qo'ng'iroq qilmang" deganda avtomatik ro'yxatga olish   │
│                                                                                        │
│   [ 5. TELEFON RAQAMLARI & ELASTIC SIP TRUNKING ]                                      │
│   • Marketplace: Platformadan tayyor raqam va parallellik sotib olish                  │
│   • Bring Your Own Carrier (BYOC): Elastic SIP Trunking (Origination & Termination)   │
│   • "Test SIP Connection": 1 klikda SIP OPTIONS orqali ulanishni tekshirish           │
│                                                                                        │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

```
┌────────────────────────────────────────────────────────────────────────┐
│                          RETELL AI PLATFORMASI                         │
│                                                                        │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │                 AGENT LOGIC LAYER (Mantiq Qatlami)              │   │
│   │                                                                │   │
│   │   [ SINGLE-PROMPT AGENT ]       [ CONVERSATIONAL FLOW (GRAPH) ]│   │
│   │   • Global System Prompt        • Multi-Prompt Node'lar        │   │
│   │   • Context & Guidelines        • Deterministic Branches       │   │
│   │   • Dynamic Tools               • Tool & Action Node'lar       │   │
│   │   • Fast LLM Reasoning          • Global (Any-time) Intents    │   │
│   └──────────────────────┬─────────────────────────┬───────────────┘   │
│                          │                         │                   │
│   ┌──────────────────────▼─────────────────────────▼───────────────┐   │
│   │            SHARED SUBSYSTEMS (Umumiy Quyi Tizimlar)            │   │
│   │   • Dynamic Variables ({{name}}, {{amount}}, etc.)             │   │
│   │   • Tools / Function Calling (REST Webhooks)                   │   │
│   │   • Knowledge Base / RAG Documents                             │   │
│   │   • Post-Conversation Data Extraction & Evaluation             │   │
│   └──────────────────────┬─────────────────────────┬───────────────┘   │
│                          │                         │                   │
│         ┌────────────────▼────────┐       ┌────────▼───────────────┐   │
│         │   VOICE RUNTIME ENGINE  │       │  CHAT RUNTIME ENGINE   │   │
│         │   • Ultra-low Latency   │       │  • Text-based Stream   │   │
│         │   • VAD & Turn Taking   │       │  • Markdown & Links    │   │
│         │   • Barge-in (Cutoff)   │       │  • Typing Indicator    │   │
│         │   • Ambient Noise       │       │  • Quick Replies       │   │
│         │   • Voicemail AMD       │       │  • Multi-turn Session  │   │
│         │   • Backchanneling      │       │                        │   │
│         └─────────────────────────┘       └────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 1.1. Single-Prompt Agent (SP Agent)

### Mohiyati
Single-Prompt agent — bu butun suhbat mantiqi bitta keng qamrovli, strukturalangan **System Prompt** orqali boshqariladigan agent turi. LLM o'zining tabiiy fikrlash va kontekstni saqlash qobiliyati orqali suhbatni olib boradi.

### Qanday ishlaydi?
1. **Boshlang'ich Salomi (`begin_message` / `first_message`):**
   - Agent qo'ng'iroq ulanganda yoki chat ochilganda aytadigan birinchi gap.
   - Bu gap zudlik bilan aytiladi (LLM chaqiruvisiz, 0ms latency).
   - Dinamik o'zgaruvchilarni qo'llab-quvvatlaydi: `"Assalomu alaykum, {{client_name}}! Sizga qanday yordam bera olaman?"`.
2. **Strukturalangan Tizim Prompti (`general_prompt`):**
   Retell barcha Single-Prompt agentlar uchun eng samarali arxitekturani tavsiya qiladi:
   - **Identity & Role:** Agent kim, qaysi kompaniyada ishlaydi, maqsadi nima.
   - **Tone & Persona:** Qanday uslubda gapiradi (xushmuomala, lo'nda, professional, 1-2 gapdan oshmagan qisqa javoblar).
   - **Context & Rules:** Kompaniya haqidagi faktlar, narxlar, shartlar.
   - **Conversation Steps (Bosqichlar yo'riqnomasi):** Suhbat qanday tartibda borishi kerakligi:
     - 1-qadam: Salomlashish va mijoz muammosini tinglash.
     - 2-qadam: Mijozning hisob raqamini yoki buyurtma kodini so'rash.
     - 3-qadam: `check_order` tool'ini chaqirish.
     - 4-qadam: Yechim taklif qilish.
   - **Guardrails (Qat'iy cheklovlar):** Nimalarni aytish mumkin emas, siyosat, noaniq holatlarda nima qilish.
3. **Tools (Funksiyalar):**
   - LLM suhbat davomida kerak bo'lganda tashqi API'larni chaqiradi (`end_call`, `transfer_call`, `book_appointment`, `check_balance`).
4. **Qachon ishlatiladi?**
   - Mijozlarga xizmat ko'rsatish (Customer Support).
   - Savol-javob (FAQ) va maslahat berish.
   - Kiruvchi umumiy qo'ng'iroqlar.
   - Suhbat tartibi qat'iy va qotib qolmagan, mijoz har xil mavzularga sakrashi mumkin bo'lgan holatlar.

---

## 1.2. Conversational Flow (Multi-Prompt / Graph-based Agent)

### Mohiyati
Conversational Flow — bu **Vizual Graf (DAG / FSM)** asosida qurilgan agent. Bu yerda suhbat bitta promptga emas, balki bir nechta mustaqil **Node (Tugunlar)** va ular orasidagi **Edge (Bog'lamlar / O'tish shartlari)**ga bo'linadi.

### Tugunlar (Nodes) Turlari
Retell'dagi Conversational Flow quyidagi asosiy tugun turlaridan iborat:

1. **Conversation Node (Suhbat holati):**
   - Har bir holatning o'zining **lokal prompti** bo'ladi. Masalan:
     - `node_greeting`: "Foydalanuvchi bilan salomlash va undan shaxsini tasdiqlashini so'ra."
     - `node_collect_date`: "Foydalanuvchidan qaysi kunga uchrashuv belgilamoqchi ekanini so'ra."
   - Agent bu holatda faqat shu vazifani bajarishga diqqat qaratadi. Boshqa bosqichlarning ortiqcha konteksti uning diqqatini chalg'itmaydi.
2. **Tool / Action Node (Amal tuguni):**
   - LLM'siz, avtomatik bajariladigan amallar:
     - API chaqirish (`fetch_user_data`, `check_slots`).
     - SMS jo'natish (`send_confirmation_sms`).
     - Tizim o'zgaruvchisini yangilash (`set variable`).
   - Bu tugun foydalanuvchiga hech narsa demasdan (yoki ixtiyoriy kutish xabari aytib: *"Bir soniya, bazadan tekshiryapman..."*) ishlaydi va natijaga qarab keyingi tugunga o'tadi.
3. **Logic / Branch Node (Mantiqiy tarmoqlanish):**
   - Deterministik shartlar (IF/ELSE):
     - `if (user.balance > 0) -> goto Node_Pay; else -> goto Node_Clear;`
     - `if (is_business_hours == true) -> goto Node_Live; else -> goto Node_Voicemail;`
4. **Transfer Node (Qo'ng'iroqni yo'naltirish):**
   - Tirik operatorga yoki tashqi telefon raqamiga o'tkazish.
   - **Cold Transfer:** Darhol ulab yuborish.
   - **Warm Transfer:** Operator go'shakni ko'targanda, AI unga suhbat xulosasini "shivirlaydi" (Whisper message: *"Mijoz Aziz, qarz to'lash bo'yicha bog'lanmoqda"*), keyin mijoz bilan ulaydi.
5. **End Call Node (Yakunlash):**
   - Xayrlashuv jumlasi aytiladi va aloqa uziladi.

### Bog'lamlar (Edges / Transitions) Turlari
Tugunlar bir-biriga ikki xil usul bilan bog'lanadi:
1. **Semantic / Intent Edge (Natural Language Transition):**
   - Tabiiy tildagi shart:
     - *"Mijoz taklifga rozi bo'lsa"* -> `Node_Accept`
     - *"Mijoz narx qimmat desa"* -> `Node_Discount`
     - *"Mijoz vaqtim yo'q desa"* -> `Node_Callback`
   - LLM suhbat davomida mijoz javobini tahlil qiladi va tegishli shart bo'yicha keyingi tugunga o'tadi.
2. **Rule / Variable Edge:**
   - Qat'iy qiymat tengligi bo'yicha o'tish (Tool qaytargan javob: `status == "SUCCESS"`).

### Global Nodes / Any-time Intents (Har doim ochiq savollar)
Grafdagi eng katta muammo: agar mijoz qadamdan chetga chiqsa nima bo'ladi? Retell buni **Global Nodes** orqali hal qilgan:
- Grafning qaysi joyida bo'lishidan qat'i nazar, agar mijoz quyidagilarni so'rasa:
  - *"Siz kimsiz o'zi? Robotmisiz?"*
  - *"Ofisingiz qayerda joylashgan?"*
  - *"Meni tirik odam bilan ulang!"*
  - *"To'xtating, boshqa qo'ng'iroq qilmang!"*
- Tizim joriy tugunni vaqtincha to'xtatadi, **Global Node** ishga tushadi, savolga javob beradi va:
  - Yoki **avvalgi tugunga qaytadi** (Resume context: *"Savolingizga javob berdim, endi qaysi sanaga yozamiz?"*);
  - Yoki boshqa tarmoqqa yo'naltiradi (masalan, Transfer yoki DNC).

---

## 1.3. Voice AI Agent vs Chat AI Agent: O'xshashlik va Farqlar

Retell AI ovozli va matnli agentlarni bitta umumiy yadrodan boshqaradi, ammo ularning transport qatlamida jiddiy farqlar mavjud:

| Xususiyat | Voice AI Agent | Chat AI Agent |
|---|---|---|
| **Kirish / Chiqish** | Audio stream (PCM/Opus, 8kHz/16kHz) | Matn (JSON / Markdown / Plain text) |
| **Kechikish (Latency)** | Qat'iy < 600-800ms (Odam kutib turolmaydi) | 1-2 soniya qabul qilinadi (Stream muhim) |
| **Turn Taking & VAD** | Silero VAD / WebRTC VAD (Odam jim bo'lganini bilish) | Foydalanuvchi "Send" tugmasini bosishi |
| **Barge-in (Interruption)** | Foydalanuvchi gap boshlashi bilan TTS to'xtatiladi | Yangi xabar kelsa generatsiya bekor qilinadi |
| **Backchanneling** | "Aha", "tushundim", "eshityapman" kabi qisqa tasdiqlar | "Typing..." ko'rsatkichi |
| **Javob formati** | Faqat talaffuzbop so'zlar (raqamlar so'z bilan, qisqartmalarsiz) | Markdown, jadvallar, bullet pointlar, URL linklar |
| **Fon shovqini (Ambient Sound)** | Ofis, kafe, call-center fon shovqini miksi | Mavjud emas |
| **AMD (Voicemail Detection)** | Avtojavobgichni (beep) aniqlash va xabar qoldirish | Mavjud emas |
| **Talaffuz lug'ati** | Pronunciation Dictionary (IPA / Fonetik) | Kerak emas |
| **Mantiqiy Yadro** | **Bir xil:** Single Prompt yoki Conversational Flow | **Bir xil:** Single Prompt yoki Conversational Flow |
| **Tools & Extraction** | **Bir xil:** API tools va Post-call tahlil | **Bir xil:** API tools va Post-chat tahlil |
| **Mijoz Xotirasi (Memory)** | **Bir xil:** Ovozli suhbatdan olingan xotirani eslaydi | **Bir xil:** Chatda olingan xotirani eslaydi |

---

## 1.4. Retell AI va Zamonaviy AI Agentlarda "Memory" (Mijoz Xotirasi) Qanday Ishlaydi?

Conversational AI platformalarida eng katta muammolardan biri — bu **"Amneziya" (esdan chiqarish)**: agar mijoz 2 kun oldin qo'ng'iroq qilib, o'zining mashina modelini, muammosini aytgan va *"Menga faqat ruscha gapiring, 18:00 dan keyin qulay"* degan bo'lsa, keyingi safar agent yana *"Siz kimsiz, qaysi tilda gaplashasiz, mashinangiz nima?"* desa, mijoz darhol robotdan ko'ngli qoladi.

Retell AI va ilg'or arxitekturalarda **Memory (Mijoz Xotirasi)** quyidagi qat'iy mexanizmlar asosida ishlaydi:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CROSS-SESSION CLIENT MEMORY PIPELINE                     │
│                                                                             │
│  [ QAYERDAN OLINADI? ]                                                      │
│  1. Suhbat transkripti (Audio STT yoki Chat matni)                         │
│  2. Muvaffaqiyatli Tool chaqiruvlari (booking_id, order_id)                 │
│  3. Agent dataNeeded / outcomeSchema faktlari                               │
│  4. Operator / CRM eslatmalari (preferredName, notes)                       │
│                                                                             │
│                            │                                                │
│                            ▼                                                │
│  [ QACHON YANGILANADI? ]                                                    │
│  • Post-Call Asinxron: Qo'ng'iroq / chat tugashi bilan LLM Extractor        │
│  • In-Call Real-Time: Maxsus tool yoki Action Node chaqirilganda            │
│  • CRM Webhook / Operator PUT: Qo'lda o'zgartirilganda                      │
│                                                                             │
│                            │                                                │
│                            ▼                                                │
│  [ QAYERGA SAQLANADI? ]                                                     │
│  • ClientMemory (Kompaniya ID + E.164 Telefon raqami / Mijoz ID)           │
│    ├── preferredName ("Anvar aka")                                          │
│    ├── preferredLanguage ("uz-UZ" yoki "ru-RU")                              │
│    ├── operatorNotes ("Narx bo'yicha qattiq savdolashadi")                  │
│    ├── recentCalls[] (oxirgi 3-5 ta suhbat xulosasi, sana, natija)          │
│    └── facts (key-value: car_model="Malibu 2", budget="1500$")              │
│                                                                             │
│                            │                                                │
│                            ▼                                                │
│  [ QACHON VA QAYERDA ISHLATILADI? ]                                         │
│  1. Session Boshlanishida: Telefon raqamidan xotira o'qiladi                │
│  2. Tilni tanlashda: Xotiradagi preferredLanguage birinchi soniyadan        │
│  3. First Message shaxsiylashadi: "Assalomu alaykum, Anvar aka!"           │
│  4. System Prompt Injection: ### CLIENT MEMORY bloki qo'shiladi             │
│  5. Dynamic Variables: {{memory.facts.car_model}} ineksiya qilinadi         │
│  6. Conversational Flow: Logic Node tarmoqlanishida (IF previousClient)     │
│  7. Omnichannel Continuity: Ovozda aytganini kechqurun Chatda eslaydi       │
│  8. Human Transfer: Operatorga suhbatni uzatganda ekranida ko'rsatiladi     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### A. Memory QACHON Yangilanadi? (Update Triggers)
1. **Post-Call / Post-Session Asinxron Yangilanish (Asosiy trigger):**
   - Qo'ng'iroq yoki chat tugashi bilanoq, fon rejimida `CallMemoryWriter` va `LLMFactExtractorJob` ishga tushadi.
   - U suhbatning to'liq transkriptini tahlil qiladi va:
     - Oxirgi suhbat xulosasini (`RecentCallSummary: "Mijoz 10-noyabrda Malibu 2 moyini almashtirishga kelishga va'da berdi"`);
     - Yangi aniqlangan faktlarni (`facts: {car_model: "Malibu 2", oil_brand: "Castrol 5W-30"}`);
     - Mijoz xulq-atvori va kayfiyatini (`sentiment: SATISFIED`);
     - Yangi faktlar eskisini ehtiyotkorlik bilan yangilaydi (merge/upsert).
2. **In-Call Real-Time Yangilanish (Suhbatning o'zida darhol):**
   - Agar suhbat davomida agent yoki ssenariy maxsus `remember_fact` tool'ini chaqirsa;
   - Yoki Conversational Flow'dagi `Tool/Action Node` orqali: masalan, mijoz *"Menga faqat ruscha gapiring"* desa, zudlik bilan `preferredLanguage = ru-RU` qilinadi va o'sha qo'ng'iroqning o'zidayoq agent tilini almashtiradi.
3. **Operator / CRM Sinxronizatsiyasi:**
   - Operator CRM yoki boshqaruv panelidan mijoz kartasini tahrirlaganda (`PUT /api/memory/{phone}`).
   - Masalan: `preferredName: "Akmal Shavkatovich"`, `operatorNotes: "Direktor, faqat kotibasi orqali bog'lanish kerak"`.

### B. Memory QAYERDAN Olib Yoziladi? (Extraction Sources)
1. **To'liq Transkript (Audio STT / Chat matni):**
   - LLM (Fast model) transkriptdan Entity va Fact Extraction qiladi.
2. **Muvaffaqiyatli Tool Chaqiruvlari Natijalari (Tool Call Payloads):**
   - Masalan, agent `book_appointment` yoki `record_promise_to_pay` chaqirsa, qaytgan `booking_id`, `date`, `amount` to'g'ridan-to'g'ri xotiraga 100% aniqlik bilan yoziladi (LLM taxminiga tayanilmaydi).
3. **Ssenariy yoki Agentning `outcomeSchema` / `dataNeeded` qoidalari:**
   - Biznes talab qilgan aniq maydonlar: `debt_amount`, `promised_date`, `interested_product`.
4. **Tizimli Metadatalar:**
   - Qo'ng'iroq vaqti, davomiyligi, yakuniy holati (`disposition`: HUNG_UP, PROMISE_TO_PAY, WRONG_NUMBER).

### C. Memory QACHON va QAYERLARDA Ishlatiladi? (Usage & Injection)
1. **Qo'ng'iroq yoki Chat Boshlanishida (Session Initialization):**
   - Chaqiruvchi telefon raqami (`phone`) bo'yicha `ClientMemory` xotiradan zudlik bilan (Redis / DB orqali < 10ms) yuklanadi.
2. **Birinchi Xabarni (First Message) Shaxsiylashtirish:**
   - Agar mijoz yangi bo'lsa: *"Assalomu alaykum! Kompaniyamizga xush kelibsiz..."*
   - Agar mijoz avval gaplashgan bo'lsa: *"Assalomu alaykum, Anvar aka! Kechagi avtoservis buyurtmangiz bo'yicha bog'lanmoqdamiz..."*
3. **Avtomatik Til Tanlash (Zero-latency Language Selection):**
   - Outbound va Inbound qo'ng'iroqda birinchi navbatda xotiradagi `preferredLanguage` tanlanadi. Natijada agent boshidanoq mijoz gapiradigan tilda so'z boshlaydi.
4. **Tizim Promptiga Ineksiya (`### CLIENT MEMORY`):**
   - LLM promptining eng yuqori qismiga maxsus blok qo'shiladi:
     ```markdown
     ### CLIENT MEMORY (MIJOZ TARIXI VA FAKTLAR):
     - Murojaat: Anvar aka
     - Til: uz-UZ
     - Operator eslatmasi: Narx bo'yicha qattiq savdolashadi, chegirma taklif qiling.
     - Oxirgi suhbatlar:
       * 2026-10-25 (Servis): Malibu 2 mashinasini ta'mirlashga qiziqqan, narx so'ragan.
     - Ma'lum faktlar: {car_model: "Malibu 2", engine: "2.0 Turbo"}
     
     MUHIM QOIDA: Mijozdan allaqachon ma'lum bo'lgan faktlarni (masalan, mashina modelini) qayta so'rama! Buni tabiiy hisobga ol, lekin "bazamizda shunday yozilgan" deb aytma.
     ```
5. **Conversational Flow Mantiqiy Tarmoqlanishida (Branching Logic):**
   - Logic Node shartlarida xotira ishlatiladi:
     - `if (memory.recentCalls.length > 0) -> goto Node_Returning_Customer; else -> goto Node_New_Customer;`
     - `if (memory.facts.debt_amount > 0) -> goto Node_Debt_Collection;`
6. **Dinamik Prompt Shablonlarida (Double Curly Braces):**
   - Prompt matnlarida: `{{memory.preferredName | "Hurmatli mijoz"}}`, `{{memory.facts.car_model}}`.
7. **Omnichannel Bog'liqlik (Voice ↔ Chat Continuity):**
   - Mijoz kunduzi telefon orqali gaplashib, kechqurun veb-saytdagi Chat vidjetiga yozsa, Chat agenti avvalgi telefon suhbati xulosasini biladi va davom ettiradi.
8. **Operatorga Uzatishda (Human Agent Screen-Pop & Whisper):**
   - Qo'ng'iroq tirik operatorga o'tkazilganda (Transfer Node), operator ekranida `ClientMemory` to'liq ochiladi va AI unga qisqacha xulosani pichirlaydi (whisper).

---

## 1.5. Retell AI Telefon Raqamlari (Phone Numbers) va Raqamlar Markazi (Marketplace & BYO)

Retell AI platformasida har bir telefon raqami mustaqil, birinchi darajali ob'ekt (First-class citizen) hisoblanadi. Mijozlarga ikkita asosiy yo'l taqdim etiladi:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PHONE NUMBER MANAGEMENT ARCHITECTURE                     │
│                                                                             │
│   [ 1-USUL: RAQAM SOTIB OLISH (POOL / MARKETPLACE) ]                        │
│   • Platform Admin 10-100+ ta raqamlarni omborga kiritib qo'yadi            │
│   • Raqamlar toifalari: Standard, Silver, Gold, Platinum                    │
│   • Mijoz o'ziga yoqqan raqamni va PARALLELLIK (liniya) sonini tanlaydi    │
│   • Billing balansidan oylik ijara to'lovi yechiladi                        │
│   • Raqam bir zumda kompaniyaga biriktiriladi (Instant Provisioning)        │
│                                                                             │
│   [ 2-USUL: ELASTIC SIP TRUNKING (RETELL RECOMMENDED BYOC) ]                │
│   • Origination (Kiruvchi): Provayder qo'ng'iroqni bizga yo'naltiradi       │
│     URI: sip:sip.platform.uz (DID orqali)                                   │
│   • Termination (Chiquvchi): Biz provayderning SIP URI'siga chiqamiz        │
│     URI: sip.provider.uz (Digest Auth yoki IP Whitelist)                    │
│   • "Test SIP Connection": 1 klikda SIP OPTIONS orqali ulanishni tekshirish │
│                                                                             │
│                                      │                                      │
│                                      ▼                                      │
│   [ RAQAM SOZLAMALARI VA BIRIKTIRILISHI (INBOUND & OUTBOUND MAPPING) ]      │
│   1. Inbound Routing: Raqamga kimdir qo'ng'iroq qilsa:                      │
│      • Qaysi AI Agent javob beradi? (Dropdown'dan tanlanadi)                │
│      • Yoki qaysi Call Flow (IVR / Ish vaqti menyusi) ishga tushadi?        │
│   2. Outbound Caller ID:                                                    │
│      • Kampaniyalar va qo'lda test qo'ng'iroqlari shu raqam orqali chiqadi  │
│   3. Concurrency Limit (Kanal soni):                                        │
│      • Raqam bo'yicha ruxsat etilgan parallel qo'ng'iroqlar chegarasi       │
│   4. Subscription & Auto-renew:                                             │
│      • Har oy balansdan avtomatik uzaytirish yoki istalgan payt bekor qilish│
└─────────────────────────────────────────────────────────────────────────────┘
```

### Retell Elastic SIP Trunking Qanday Ishlaydi? (Rasmiy Hujjat Tahlili)
Retell AI (`docs.retellai.com/deploy/custom-telephony#method-1-elastic-sip-trunking-recommended`) o'z telekomunikatsiyasini ulash uchun **Elastic SIP Trunking**ni eng asosiy va tavsiya etilgan usul deb e'lon qilgan. U quyidagi 2 ta yo'nalishdan iborat:

1. **Termination (Chiquvchi qo'ng'iroqlar — Outbound):**
   - Retell orqali tashqariga qo'ng'iroq qilinganda, audio oqimi foydalanuvchining telekom provayderi (Twilio, Telnyx, Plivo yoki mahalliy O'zbekiston operatorlari: Uztelecom, Beeline, Ucell, Perfectum PBX) orqali chiqadi.
   - **Sozlamalar:**
     - `Termination SIP URI`: Provayderning chiquvchi domeni (masalan: `sip.carrier.uz` yoki `{trunk-name}.pstn.twilio.com`);
     - `Transport`: UDP, TCP yoki TLS (SRTP shifrlash bilan);
     - `Auth Type`: IP Whitelisting (provayderda Retell IP bloki oq ro'yxatga olinadi) YOKI SIP Username & Password (Digest Authentication).
2. **Origination (Kiruvchi qo'ng'iroqlar — Inbound):**
   - Mijozlar raqamga qo'ng'iroq qilganda, provayder qo'ng'iroqni platforma SIP serveriga yo'naltiradi:
     $$\text{sip:sip.retellai.com (yoki bizda: sip:sip.robotcall.uz:5060)}$$
   - Provayderda sotib olingan telefon raqamlari (DID) aynan shu Elastic SIP Trunkka biriktirilgan bo'ladi.
3. **Retell Dashboard'da Raqamni Qo'shish Jarayoni:**
   - Foydalanuvchi `Phone Numbers` bo'limida **"Connect via SIP Trunking"** tugmasini bosadi;
   - `Phone Number` (E.164: `+998712001122`), `Termination URI`, `SIP Username/Password` kiritadi;
   - **"Test SIP Connection"** tugmasini bosadi:
     - Platforma darhol Asterisk / SIP orqali `SIP OPTIONS` ping so'rovini yuboradi. Agar provayder `200 OK` qaytarsa — yashil tasdiq belgisi chiqadi;
   - Va eng asosiysi: **"Inbound Agent"** maydonidan istalgan AI agent tanlanadi. Tamom! Qo'ng'iroqlar darhol agentga tusha boshlaydi.

---

## 1.5.1. Retell & Vapi Telephoniya va Media Arxitekturasi (LiveKit + LiveKit SIP)

Retell AI va Vapi o'zlarining mashhur ultra-past kechikishiga (sub-second latency: 500-700ms umumiy javob vaqti) qanday erishadi? 

Klassik ATS/PBX (masalan, eski usuldagi Asterisk) qo'ng'iroqni an'anaviy PBX o'rami, og'ir jitter buffer, kanal bridging va dialplan orqali boshqaradi. Retell va Vapi esa telekomunikatsiyani butunlay boshqacha — **WebRTC-native Media Gateway (LiveKit arxitekturasi)** asosida qurgan:

```
[Mijoz telefoni / Tashqi raqam]
            │ SIP (G.711 / Opus)
            ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. SBC / SIP PROXY (Kamailio / OpenSIPS / LiveKit SIP)      │
│ • SIP Trunklarni boshqarish (Origination / Termination)    │
│ • Digest Authentication, IP Whitelist, SIP OPTIONS ping     │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. LIVEKIT SIP GATEWAY & SFU (WebRTC Audio Bridge)          │
│ • SIP RTP oqimini bir zumda WebRTC Audio Track'ga aylantiradi│
│ • Har bir qo'ng'iroq uchun alohida WebRTC Room ochiladi     │
│ • Audio kechikish (transport latency) < 50-80ms!             │
└─────────────────────────────┬───────────────────────────────┘
                              │ WebRTC Audio In/Out
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. RETELL / VAPI REAL-TIME AI AGENT RUNTIME                 │
│ ┌──────────────────────────┐    ┌─────────────────────────┐ │
│ │ Silero VAD + Turn Model  │    │ Deepgram Streaming STT  │ │
│ └────────────┬─────────────┘    └────────────┬────────────┘ │
│              │                               │              │
│              ▼                               ▼              │
│ ┌──────────────────────────┐    ┌─────────────────────────┐ │
│ │ Cartesia / 11Labs TTS    │◀───│ Fast LLM (Claude / GPT) │ │
│ └──────────────────────────┘    └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Nega aynan LiveKit + LiveKit SIP?
1. **WebRTC ustunligi:** RTP paketlarining yo'qolishiga chidamlilik, adaptiv jitter-bufer, audio hajmining minimal bo'lishi va sub-100ms transport tezligi.
2. **Yagona media-yadro:** Veb-saytdagi WebRTC audio vidjeti va telefon tarmog'idagi SIP qo'ng'iroq LiveKit uchun mutlaqo bir xil WebRTC xonasi (Room) hisoblanadi. Agent kodi telefon va veb uchun ikkiga bo'linmaydi.
3. **SIP Participant API:** LiveKit SIP'ning `CreateSIPParticipant` API'si orqali birgina HTTP/gRPC so'rov bilan tashqariga qo'ng'iroq qilib, ko'tarilgan go'shakni darhol xonaga ulab berish mumkin.
4. **Resurs tejamkorligi:** Bitta serverda minglab parallel audio streamlarni yengil Go-based WebRTC SFU orqali ushlab turish mumkin.

---

## 1.6. Retell Audio va Nutq Sozlamalari (Speech & Audio Tuning)

Retell rasmiy hujjatlarida nutqning tabiiyligi va insondek jaranglashi uchun quyidagi aniq parametrlar berilgan:

1. **Responsiveness (Javob berish tezligi / Kutilma):**
   - Qiymati: `0` dan `1` gacha (default: `1`).
   - Bu parametr mijoz gapi to'xtaganidan keyin agent qancha kutishi kerakligini belgilaydi.
   - `1.0` — agent darhol (ultra-tez) javob beradi.
   - `0.8 - 0.9` — agent taxminan 0.8–1.2 soniya kutilma qiladi (sekin, o'ylab gapiradigan mijozlar va chuqur savollar uchun).
2. **Ambient Sound (Fon Shovqini):**
   - Telefon orqali gaplashganda mutlaq sukunat (dead silence) mijozda robot bilan gaplashayotganlik yoki aloqa uzilib qolganlik hissini uyg'otadi.
   - Retell fon tovushi turlari:
     - `"coffee-shop"` — orqa fonda kofe hidi, idish-tovoq va yengil odamlar ovozi;
     - `"call-center"` — orqa fonda boshqa operatorlarning past ovozda gapirayotgani;
     - `"office"` — odatiy tinch ofis muhiti;
     - `"none"` — mutlaq toza studiya ovozi.
3. **Backchanneling (Oraliq Tasdiqlar):**
   - Agar mijoz 3 soniyadan ortiq to'xtovsiz gapirsa, agent inson kabi oraliqda tasdiqlab turadi:
   - Parametrlari:
     - `enable_backchannel`: `true/false`;
     - `backchannel_frequency`: `0.0 - 1.0` (default: `0.8`);
     - `backchannel_words`: `["aha", "tushundim", "ha", "eshityapman"]`.
4. **Dynamic Speaking Speed (Dinamik Gapirish Tezligi):**
   - Baza tezligi: `0.5x` dan `2.0x` gacha (default: `1.0x`).
   - `dynamic_speed_adjustment`: Agar yoqilsa, agent mijozning gapirish tezligini o'lchaydi: agar mijoz shoshilib, tez gapirsa — agent ham tezlashadi; agar mijoz keksa yoki bosiq gapirsa — agent ham sekinroq va aniqroq talaffuz qiladi.
5. **Welcome Message & Turn Taking:**
   - `User speaks first`: Qo'ng'iroq ulanganda agent jim turadi va mijoz gapirishini kutadi (kiruvchi qo'llab-quvvatlash uchun).
   - `AI speaks first`: Qo'ng'iroq ulanganda agent darhol salom beradi:
     - *Static message:* Qotirilgan matn (0ms kechikish);
     - *Dynamic message:* LLM tomonidan har bir qo'ng'iroq ma'lumotlariga qarab generatsiya qilingan salom.
   - `pause_before_speaking`: AI mijozning birinchi so'zini bo'lib qo'ymasligi uchun ochilishdagi qisqa bufer (50-200ms).
6. **Silence Timeout & Reminders:**
   - Agar suhbat davomida mijoz uzoq vaqt jim qolsa (`silence_timeout_ms = 7000`), agent avtomatik eslatma aytadi: *"Eshityapsizmi? Alo, sizga qanday yordam bera olaman?"*.

---

## 1.7. Retell Functions & Tools (Funksiyalar va Tashqi Amallar)

Retell'da agent o'z xatti-harakatlarini boshqarish uchun quyidagi 4 toifadagi asboblardan foydalanadi:

1. **Transfer Call (`transfer_call`):**
   - Qo'ng'iroqni tirik operatorga yoki boshqa bo'limga uzatadi.
   - Manzil turlari:
     - Telefon raqami (E.164);
     - SIP URI (`sip:operator@domain.uz`);
     - Dinamik o'zgaruvchi (`{{support_number}}`);
     - Qo'shimcha ichki raqamlar (Extensions: `102#`).
   - **Agent Transfer:** Qo'ng'iroqni bir AI agentdan boshqa ixtisoslashgan AI agentga to'liq dialog tarixi bilan uzatish!
2. **End Call (`end_call`):**
   - LLM suhbat maqsadi yakunlanganda yoki xayrlashuv aytilganda qo'ng'iroqni dasturiy uzadi.
3. **Press Digit (`press_digit` / IVR Navigation):**
   - O'ziga xos va juda foydali funksiya! Outbound qo'ng'iroq qilinganda, qarshi tomonda robot (IVR menu) chiqsa (*"Operator bilan bog'lanish uchun 0 ni bosing"*), agent o'zi kerakli raqamni (DTMF signalini) bosib, operatorgacha yorib o'tadi.
4. **Custom Functions (Webhook Tools):**
   - Tashqi CRM, ma'lumotlar bazasi yoki kalendarlar bilan bog'lanish (HTTP Method, Endpoint URL, Headers, JSON Schema parameters).
   - LLM o'zi qachon qaysi argumentlar bilan API chaqirishni hal qiladi.
5. **Subflows / Components (Qayta Ishlatiluvchi Modullar):**
   - Bir nechta tugunlardan iborat mini-graf (masalan: *"Mijoz shaxsini tekshirish"*, *"To'lov sanasini kelishish"*, *"NPS so'rovnoma"*).
   - Bitta subflow'ni butun kompaniyadagi barcha agentlar ichida qayta-qayta ishlatish mumkin.

---

## 1.8. Retell Guardrails va Do-Not-Call (DNC) Qoidalari

1. **Real-time Guardrails (Xavfsizlik Cheklovlari):**
   - Retell kiruvchi va chiquvchi matnni real vaqtda tekshiradi.
   - Taqiqlangan mavzular: ruxsatsiz tibbiy maslahat, moliyaviy/yuridik kafolatlar, haqorat, noo'rin mavzular.
   - **Muhim jihati:** Agar mijoz taqiqlangan mavzuni so'rasa, Retell qo'ng'iroqni uzib yubormaydi! U flagged javobni avtomatik **Safe Placeholder Message** bilan almashtiradi: *"Kechirasiz, men faqat xizmatlarimiz bo'yicha ma'lumot bera olaman. Bu masalada mutaxassisimizga murojaat qilishingizni tavsiya qilaman"*.
2. **Do-Not-Call (DNC) Avtomatizatsiyasi:**
   - Agar mijoz suhbatda *"Menga boshqa qo'ng'iroq qilmang!"*, *"Raqamimni o'chirib tashlang!"* desa:
   - Agent buni darhol tushunadi, e'tiroz bildirmaydi, xushmuomala rozilik bildiradi (*"Tushundim, raqamingizni bazamizdan o'chirib qo'yaman. Bezovta qilganimiz uchun uzr so'raymiz"*);
   - Qo'ng'iroqni tugatadi va raqamni DNC ro'yxatiga qo'shadi;
   - Keyingi kampaniyalarda bu raqamga avtomatik qo'ng'iroq qilinmaydi.

---

# 2-QISM: RETELL AI DEFAULT SHABLONLARI (TEMPLATES) VA ULARDA NIMALAR TO'LDIRILGAN?

Retell platformasida biznesning eng mashhur 8 ta yo'nalishi bo'yicha tayyor shablonlar (Templates) mavjud. Har bir shablon o'z ichiga quyidagi to'liq parametrlarni oladi:
1. `name` & `description`
2. `persona` & `tone`
3. `first_message` (Dinamik o'zgaruvchilar bilan)
4. `system_prompt` (Yoki Flow Node'lari)
5. `dynamic_variables`
6. `tools` (Parametrlari bilan)
7. `post_call_analysis` (Yig'iladigan faktlar va baholash)
8. `voice_settings` (Latency, Ambient sound, Interruption sensitivity).

Quyida ushbu 8 ta shablonning har birining ichki to'liq tuzilishi keltirilgan:

---

### Shablon 1: Appointment Booking (Qabulga / Xizmatga yozilish)

- **Maqsad:** Mijoz bilan uchrashuv, tibbiy qabul, avtoservis yoki go'zallik saloni vaqtini kelishish va kalendarga bron qilish.
- **Agent Turi:** Conversational Flow yoki Structured Single Prompt.
- **Dinamik O'zgaruvchilar:**
  - `{{client_name}}` — Mijoz ismi;
  - `{{service_type}}` — So'ralayotgan xizmat turi (masalan, "Tish tekshiruvi");
  - `{{company_name}}` — Klinika yoki salon nomi;
  - `{{today_date}}` — Bugungi sana va hafta kuni.
- **First Message:**
  > "Assalomu alaykum! {{company_name}}ga xush kelibsiz. Mening ismim Dilorom. Sizni qaysi xizmatimizga yozib qo'yay?"
- **System Prompt / Bosqichlar:**
  1. **Aniqlash:** Qaysi xizmat turi kerakligini va qulay kun/vaqtni so'rash.
  2. **Tekshirish:** Mijoz aytgan vaqt bo'yicha `check_availability` tool'ini chaqirish.
  3. **Taklif:** Agar vaqt band bo'lsa, eng yaqin 2 ta bo'sh vaqtni taklif qilish.
  4. **Tasdiqlash:** Vaqt kelishilgach, mijozning to'liq ismi va telefonini tasdiqlab, `book_appointment` tool'ini chaqirish.
  5. **Yakun:** SMS tasdiqnoma yuborilganini aytish va yaxshi kun tilash.
- **Biriktirilgan Tools:**
  - `check_availability(service_name: string, date: string)` -> bo'sh vaqtlar ro'yxati.
  - `book_appointment(client_name: string, phone: string, date: string, time: string, service_name: string)` -> `booking_id`.
- **Post-Call Analysis (Ma'lumot yig'ish):**
  - `booking_successful`: boolean
  - `booked_service`: string
  - `appointment_datetime`: string (ISO 8601)
  - `customer_notes`: string
- **Ovoz sozlamalari:**
  - Ambient sound: `OFFICE_QUIET` (Tinch ofis muhiti)
  - Interruption sensitivity: `0.8` (Mijoz gapirishi bilan to'xtash).

---

### Shablon 2: Customer Support & Helpdesk (Texnik yordam va FAQ)

- **Maqsad:** Mijozlarning savollariga bilimlar bazasi (Knowledge Base) orqali javob berish, muammo yechilmasa ticket ochish yoki operatorga ulash.
- **Agent Turi:** Single-Prompt + RAG (Knowledge Base).
- **Dinamik O'zgaruvchilar:**
  - `{{client_name}}`
  - `{{account_id}}`
  - `{{company_name}}`
- **First Message:**
  > "Assalomu alaykum, {{client_name}}! {{company_name}} qo'llab-quvvatlash xizmati. Qanday savol yoki muammo bo'yicha yordam bera olaman?"
- **System Prompt / Qoidalar:**
  - "Siz texnik yordam ko'rsatuvchi do'stona va sabrli mutaxassissiz."
  - "Javoblaringizni faqat taqdim etilgan Bilimlar Bazasi (Knowledge Base) ma'lumotlariga asoslang. Bilmagan narsangizni to'qimang."
  - "Agar mijozning muammosi standart qo'llanmalar bilan hal bo'lmasa, `create_ticket` tool'i orqali ariza oching yoki `transfer_to_agent` bilan tirik mutaxassisga yo'naltiring."
  - "Har doim qisqa, aniq qadamlar bilan tushuntiring."
- **Biriktirilgan Tools:**
  - `search_knowledge_base(query: string)` -> maqola matnlari.
  - `create_ticket(category: string, description: string, priority: string)` -> `ticket_number`.
  - `transfer_to_agent(department: string)` -> SIP transfer.
- **Post-Call Analysis:**
  - `issue_resolved`: boolean
  - `issue_category`: string (Billing, Technical, Account, General)
  - `customer_sentiment`: string (POSITIVE, NEUTRAL, FRUSTRATED)
  - `ticket_created`: boolean

---

### Shablon 3: Outbound Lead Qualification (Lidlarni saralash / Cold Call)

- **Maqsad:** Yangi kelib tushgan arizalar yoki lidlar bazasiga qo'ng'iroq qilib, ularning qiziqishi, byudjeti va ehtiyojini aniqlash (BANT metodologiyasi), so'ng demo uchrashuvga yozish.
- **Agent Turi:** Conversational Flow (Qat'iy tarmoqlanish).
- **Dinamik O'zgaruvchilar:**
  - `{{client_name}}`
  - `{{source}}` (masalan: "Saytimizda ariza qoldirgan edingiz")
  - `{{product_name}}`
- **First Message:**
  > "Salom, {{client_name}}! Mening ismim Jasur, {{product_name}} kompaniyasidanman. Saytimizda qoldirgan arizangiz bo'yicha bog'lanyapman. 1 daqiqa gaplasha olasizmi?"
- **Flow Bosqichlari:**
  1. `Intro`: Shaxsni va qulay vaqtni tasdiqlash.
     - *Agar band bo'lsa:* Qayta qo'ng'iroq vaqtini belgilash -> `End`.
     - *Agar bekor qilmoqchi bo'lsa:* Sababini so'rash -> `End`.
     - *Agar rozi bo'lsa:* -> `Qualify_Needs`.
  2. `Qualify_Needs`: "Ayni paytda biznesingizda qanday qiyinchilik bor?"
  3. `Qualify_Budget`: "Buning uchun qancha byudjet ajratishni rejalashtiryapsiz?"
  4. `Decision`: Agar parametrlar mos kelsa -> `Book_Demo` tool'ini chaqirish; mos kelmasa xushmuomala rad etish.
- **Biriktirilgan Tools:**
  - `schedule_demo(slot: string, notes: string)`
  - `mark_lead_status(status: QUALIFIED | UNQUALIFIED | CALLBACK_REQUESTED)`
- **Post-Call Analysis:**
  - `lead_qualification_status`: enum (QUALIFIED, DISQUALIFIED, CALLBACK)
  - `estimated_budget`: string
  - `timeline`: string
  - `pain_points`: string

---

### Shablon 4: Debt Collection & Payment Reminder (Qarzni eslatish va to'lov va'dasi)

- **Maqsad:** Kechikkan qarzdorlik haqida ogohlantirish, to'lov imkoniyatlarini muhokama qilish va aniq to'lov va'dasi (Promise to Pay) olish.
- **Agent Turi:** Conversational Flow.
- **Dinamik O'zgaruvchilar:**
  - `{{client_name}}`
  - `{{debt_amount}}` (masalan: "450 000 so'm")
  - `{{due_date}}`
  - `{{contract_number}}`
- **First Message:**
  > "Assalomu alaykum, {{client_name}}! Mening ismim Shahlo, {{company_name}} moliyaviy bo'limidanman. Siz bilan {{contract_number}}-raqamli shartnoma bo'yicha bog'lanyapman."
- **Flow Bosqichlari:**
  1. `Identity_Check`: "Siz aynan {{client_name}}misiz?" (Xavfsizlik tekshiruvi).
  2. `Debt_Notice`: "Shartnomangiz bo'yicha {{due_date}} sanasiga {{debt_amount}} muddati o'tgan to'lovingiz mavjud."
  3. `Payment_Negotiation`:
     - *Agar to'lay olsa:* Qaysi sanagacha to'lay olishini so'rash -> SMS to'lov havolasini yuborish -> `record_ptp` tool'ini chaqirish.
     - *Agar hozir puli bo'lmasa:* Qisman to'lov yoki bo'lib to'lash variantini taklif qilish.
     - *Agar qat'iy rad etsa:* Rad sababini yozib olish va huquqiy oqibatlar haqida ogohlantirish.
- **Biriktirilgan Tools:**
  - `record_promise_to_pay(promised_date: string, promised_amount: number)`
  - `send_payment_link_sms(phone: string, amount: number)`
  - `escalate_to_recovery_agent()`
- **Post-Call Analysis:**
  - `promise_to_pay`: boolean
  - `promised_date`: string
  - `promised_amount`: number
  - `refusal_reason`: string (Moliyaviy qiyinchilik, E'tiroz, Unutgan, etc.)

---

### Shablon 5: Inbound Receptionist & Virtual Front Desk (Aqlli Kotiba)

- **Maqsad:** Tashkilotga tushgan kiruvchi qo'ng'iroqlarni kutib olish, murojaat maqsadini aniqlash va tegishli bo'lim yoki mas'ul shaxsga ulab berish.
- **Agent Turi:** Single-Prompt yoki Triage Flow.
- **Dinamik O'zgaruvchilar:**
  - `{{company_name}}`
  - `{{departments_list}}` (Buxgalteriya, Savdo, Rahbariyat, Texnik bo'lim).
- **First Message:**
  > "Assalomu alaykum! {{company_name}}ga xush kelibsiz. Men virtual yordamchiman. Sizni qaysi bo'lim yoki xodim bilan bog'lay?"
- **System Prompt / Qoidalar:**
  - Mijoz maqsadini eshitish (masalan: "Menga hisob-faktura bo'yicha gaplashish kerak").
  - Bo'limni aniqlash: "Buxgalteriya bo'limi bilan bog'layman, bir soniya kuting."
  - `transfer_call` tool'ini chaqirish.
  - Agar bo'lim band bo'lsa yoki ish vaqti bo'lmasa: `take_message` tool'i orqali xabar yozib olish.
- **Biriktirilgan Tools:**
  - `transfer_call(destination_number: string, department: string)`
  - `take_message(caller_name: string, caller_phone: string, message: string)`
- **Post-Call Analysis:**
  - `caller_intent`: string
  - `routed_to_department`: string
  - `transferred_successfully`: boolean
  - `message_taken`: string

---

### Shablon 6: Customer Satisfaction Survey (CSAT / NPS Baholash)

- **Maqsad:** Xarid yoki ko'rsatilgan xizmatdan so'ng mijozning qoniqish darajasini (1-10) aniqlash va qisqa sharh olish.
- **Agent Turi:** Conversational Flow (Qisqa, 3 ta savol).
- **First Message:**
  > "Salom, {{client_name}}! {{company_name}}dan qo'ng'iroq qilyapmiz. Xizmat ko'rsatish sifatini yaxshilash uchun atigi 1 daqiqa vaqtingizni ajrata olasizmi?"
- **Flow Bosqichlari:**
  1. `Question_Score`: "Oxirgi xizmatimizni 1 dan 10 gacha bo'lgan shkalada qanday baholaysiz?"
  2. `Question_Reason`: "Nega aynan shu bahoni berdingiz? Qaysi jihati sizga eng ko'p yoqdi yoki yoqmadi?"
  3. `Question_Suggestions`: "Kelgusida nimani yaxshilashimizni istardingiz?"
  4. `Thank_You`: "Katta rahmat! Fikrlaringiz biz uchun juda muhim."
- **Biriktirilgan Tools:**
  - `save_survey_result(score: integer, reason: string, suggestion: string)`
- **Post-Call Analysis:**
  - `nps_score`: integer (1-10)
  - `sentiment`: POSITIVE | NEUTRAL | DETRACTOR
  - `key_feedback`: string

---

### Shablon 7: Healthcare & Clinic Patient Intake (Bemor Qabuli)

- **Maqsad:** Bemorning shikoyatlarini birlamchi yozib olish, kerakli mutaxassisni tanlash, favqulodda holatni tekshirish (Triage).
- **Muhim Guardrail:** Agar bemor ko'krak qafasida o'tkir og'riq, nafas qisishi yoki qon ketishini aytsa — darhol 103 (Tez yordam) chaqirishni aytish va suhbatni to'xtatish!
- **First Message:**
  > "Assalomu alaykum! {{clinic_name}} klinikasining tibbiy maslahatchisiman. Salomatligingiz bo'yicha qanday shikoyatlar bezovta qilyapti?"
- **Biriktirilgan Tools:**
  - `match_specialty(symptoms: string)` -> tavsiya etilgan shifokor mutaxassisligi.
  - `book_clinic_visit(doctor_id: string, date: string, patient_name: string)`
- **Post-Call Analysis:**
  - `chief_complaint`: string
  - `urgency_level`: EMERGENCY | URGENT | ROUTINE
  - `recommended_specialty`: string

---

### Shablon 8: E-commerce Order Status & Returns (Buyurtma va Yetkazish)

- **Maqsad:** Buyurtmaning qayerdaligini aytish, manzilni o'zgartirish yoki qaytarish (Return) arizasini ochish.
- **Dinamik O'zgaruvchilar:**
  - `{{order_id}}`
  - `{{client_name}}`
- **First Message:**
  > "Assalomu alaykum, {{client_name}}! {{store_name}} internet-do'koni. Buyurtmangiz bo'yicha qanday yordam bera olaman?"
- **Biriktirilgan Tools:**
  - `get_order_tracking(order_id: string)` -> holat va kutilayotgan yetkazish vaqti.
  - `update_shipping_address(order_id: string, new_address: string)`
  - `initiate_return(order_id: string, reason: string)`
- **Post-Call Analysis:**
  - `order_id_verified`: boolean
  - `action_performed`: TRACKING_PROVIDED | ADDRESS_UPDATED | RETURN_INITIATED

---

# 3-QISM: ROBOT-CALL-V2 UCHUN RETELL DARAJASIDAGI TRANSFORMATSIYA VA REFAKTORING REJASI (PLAN)

Foydalanuvchi ta'kidlaganidek: **"sen o'ylama istaganimizcha refactor qilsak bo'ladi hozir bu reliz bo'lmagan"**.
Bu bizga loyihani bugungi jahon standartiga (Retell AI) to'liq moslab, eng toza, kengaytiriladigan va professional arxitekturani qurish imkonini beradi.

Quyida 6 ta bosqichdan iborat to'liq Yo'l Xaritasi (Roadmap) keltirilgan:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                 ROBOT-CALL-V2: RETELL ARXITEKTURA REJASI                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  [ BOSQICH 1: DOMEN VA MODELLAR REFAKTORIGI ]                              │
│  • AiAgentType: SINGLE_PROMPT vs CONVERSATIONAL_FLOW                       │
│  • ChannelType: VOICE, CHAT, OMNICHANNEL                                    │
│  • Agent konfiguratsiyasi (Prompt, Nodes, Edges, Global Intents)          │
│                                                                             │
│  [ BOSQICH 2: CONVERSATIONAL FLOW GRAF DVIGATELI (DAG / FSM) ]              │
│  • Node turlari: ConversationNode, ToolNode, BranchNode, Transfer, End      │
│  • Edge turlari: IntentEdge (Semantic LLM), ConditionEdge (Rule-based)      │
│  • Global Nodes (Any-time FAQs & Intent Interceptors)                       │
│                                                                             │
│  [ BOSQICH 3: CHAT VA VOICE NING BIRLASHGAN YADROSI (UNIFIED RUNTIME) ]     │
│  • DialogSession & DialogEngine umumlashtirilishi                          │
│  • Voice Channel Adapter (Asterisk, RTP, VAD, Low-latency TTS)              │
│  • Chat Channel Adapter (Web Widget, REST, SSE Streaming, Markdown)        │
│                                                                             │
│  [ BOSQICH 4: RETELL OVOZ XUSUSIYATLARI (VOICE ADVANCED FEATURES) ]         │
│  • Ambient Sound Generator (Ofis/Call-center shovqin miksi)                 │
│  • Zero-latency Barge-in (Interruption sensitivity)                        │
│  • Backchanneling Engine ("aha", "tushundim")                               │
│  • Answering Machine Detection (AMD / Voicemail handling)                   │
│                                                                             │
│  [ BOSQICH 5: RETELL POST-CONVERSATION ANALYTICS & EXTRACTION ]             │
│  • Avtomatik xulosa (Call Summary) va Sentiment (Ijobiy/Salbiy)             │
│  • Dinamik JSON schema bo'yicha faktlarni sug'urib olish (Data Extraction)  │
│  • Muvaffaqiyatli Tool natijalarini to'g'ridan-to'g'ri xotiraga yozish      │
│  • Muvaffaqiyat mezoni (Goal Evaluation) va Webhook dispatch               │
│                                                                             │
│  [ BOSQICH 6: RETELL OMNICHANNEL MEMORY DVIGATELI ]                         │
│  • ClientMemory: Phone/UserId bo'yicha tezkor yuklash (<10ms)               │
│  • Zero-latency Language Selection (Mijoz tilini birinchi soniyadan bilish) │
│  • First Message Shaxsiylashtirish (Qaytgan mijoz vs Yangi mijoz)           │
│  • System Prompt Injection (### CLIENT MEMORY & Do Not Re-Ask Guardrail)    │
│  • Cross-Channel Continuity (Voice suhbatini kechqurun Web Chatda eslash)   │
│                                                                             │
│  [ BOSQICH 7: TAYYOR 8 TA RETELL SHABLONLARI DVIGATELI ]                    │
│  • 8 ta built-in shablon (Appointment, Support, Lead, Debt, etc.)          │
│  • "Create from Template" — 1 klikda to'liq sozlamalar bilan agent yaratish │
│  • O'zbek va rus tillari uchun mahalliylashtirilgan promptlar va faktlar   │
│                                                                             │
│  [ BOSQICH 8: TELEFON RAQAMLARI MARKAZI (MARKETPLACE & BYON) ]              │
│  • Platform Admin Pool (10-100+ ta raqamlarni omborga kiritish)             │
│  • Raqam sotib olish (Parallellik/liniyalar soniga qarab oylik ijara)       │
│  • O'z raqamini / SIP trunkini ulash (BYON)                                 │
│  • Raqamni 1 klikda AI Agentga yoki Call Flow'ga biriktirish (Inbound Map)  │
│                                                                             │
│  [ BOSQICH 9: KO'P DVIGATELLI TELEFONIYA (MULTI-ENGINE) & LIVEKIT SIP ]     │
│  • TelephonyEngine: ASTERISK, LIVEKIT (Retell/Vapi standarti), FREESWITCH   │
│  • Kompaniya darajasida dvigatel tanlash (Admin boshqaruvi)                 │
│  • LiveKit Server + LiveKit SIP Gateway (WebRTC Room & Audio Tracks)        │
│  • TelephonyRouter orqali Asterisk va LiveKit o'rtasida shaffof boshqaruv   │
│  • FreeSWITCH ni kelajakdagi yirik Carrier softswitch sifatida rejalash     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3.1. Bosqich 1: Domen va Ma'lumotlar Modeli Refaktoringi

### Maqsad
`AiAgent` modelini Retell kabi aniq 2 ta rejimga ajratish:
1. `AgentMode`:
   - `SINGLE_PROMPT`: Bitta tizimli prompt, birinchi xabar va tool'lar.
   - `CONVERSATIONAL_FLOW`: To'liq graf, multi-prompt, deterministik tugunlar.
2. `AgentChannel`:
   - `VOICE`: Ovozli qo'ng'iroqlar uchun (SIP, Asterisk, VAD, STT/TTS).
   - `CHAT`: Matnli chatlar uchun (Web widget, Telegram, mobil ilova).
   - `OMNICHANNEL`: Ikkala kanalga ham xizmat qila oladi.

### Yangi Domen Strukturasi
```
uz.murodjon.robotcallv2.aiagent.domain
├── entity
│   ├── AiAgent.java                       — Asosiy domen entity
│   ├── SinglePromptConfig.java            — Single prompt sozlamalari
│   ├── ConversationalFlowConfig.java      — Graf sozlamalari (Nodes + Edges)
│   ├── VoiceEngineConfig.java             — Ovoz sozlamalari (Ambient, Latency, VAD)
│   ├── ChatEngineConfig.java              — Chat sozlamalari (Markdown, Buttons)
│   ├── DataExtractionConfig.java          — Suhbatdan sug'urib olinadigan maydonlar
│   └── EvaluationRule.java                — Sifat baholash mezonlari
└── enums
    ├── AiAgentMode.java                   — SINGLE_PROMPT, CONVERSATIONAL_FLOW
    ├── AiAgentChannel.java                — VOICE, CHAT, OMNICHANNEL
    ├── AmbientSoundType.java              — NONE, OFFICE_QUIET, CALL_CENTER, CAFE
    └── VoicemailAction.java               — HANGUP, LEAVE_MESSAGE, IGNORE
```

---

## 3.2. Bosqich 2: Conversational Flow Graf Dvigateli (Graph / FSM)

### Arxitektura
Retell'dagi graf dvigatelini `uz.murodjon.robotcallv2.scenario` va `agent/dialog` orqali to'liq yangilash:
1. **`FlowNode` turlari:**
   - `CONVERSATION`: Lokal prompt va kutilgan javoblar.
   - `TOOL`: Webhook/API chaqiruv.
   - `LOGIC_BRANCH`: `condition` asosida shartli sakrash.
   - `TRANSFER`: SIP orqali operatorga uzatish (Cold / Warm).
   - `END`: Xayrlashib tugatish.
2. **`FlowEdge` turlari:**
   - `INTENT`: LLM orqali tabiiy tildagi ma'noni tushunish ("agar mijoz rozi bo'lsa").
   - `EXPRESSION`: Faktlar va o'zgaruvchilar bo'yicha (`status == 200`).
3. **`GlobalNode` (Any-time Intent):**
   - Istalgan bosqichda tushgan savollarni interceptor sifatida tutib olib, javob berish va yana o'sha bosqichga qaytish.

---

## 3.3. Bosqich 3: Chat va Voice Uchun Birlashtirilgan Yadro (Unified Runtime)

Retell'ning eng katta ustunligi — mantiqiy yadroning kanaldan (Voice yoki Chat) mustaqilligi.
- **`DialogEngine`:**
  - `handleUserUtterance(sessionId, userMessage)` -> umumiy metod.
  - Bu metod foydalanuvchining gapini (STT'dan kelgan matn yoki Chatdan kelgan matn) qabul qiladi;
  - Agent Single-Prompt bo'lsa — LLM ga kontekst bilan uzatadi;
  - Agent Conversational Flow bo'lsa — joriy Node prompti va Edge transition mantiqini ishga tushiradi;
  - Natijada `AgentTurnResult` qaytaradi (`replyText`, `toolsToExecute`, `endConversation`, `transferTo`).
- **Voice Adapter:**
  - `replyText` ni olib, gapma-gap TTS ga stream qiladi, RTP orqali yuboradi.
- **Chat Adapter:**
  - `replyText` ni olib, WebSocket / SSE orqali brauzerga yoki Telegramga stream qiladi.

---

## 3.4. Bosqich 4: Retell Ovoz Xususiyatlari (Voice Advanced Features)

Retell ovozli agentlarining insondek jaranglashining sababi bo'lgan 4 ta xususiyatni `agent/` paketiga qo'shish:
1. **Ambient Noise Generator (`agent/audio/AmbientMixer`):**
   - Telefon orqali gaplashganda mutlaq sukunat (dead silence) bo'lsa, mijoz robot bilan gaplashayotganini sezadi yoki aloqa uzildi deb o'ylaydi.
   - Tizimga fon shovqinlari (Ofis, Kichik kafe, Call-markaz shovqini) mikserini qo'shish.
2. **Barge-in / Zero-latency Interruption (`agent/vad/BargeInController`):**
   - Mijoz gap boshlaganida VAD signal berishi bilanoq Asterisk kanali bo'yicha TTS audio oqimini 50ms ichida to'xtatish.
3. **Backchanneling (`agent/turn/BackchannelService`):**
   - Agar mijoz 3 soniyadan ortiq to'xtovsiz gapirsa, agent "Aha", "Tushundim", "Ha" kabi qisqa tovushlarni aralashtirishi.
4. **Answering Machine Detection (AMD / Voicemail):**
   - Gudokdan keyingi signal (Beep tone) yoki robot salomlashuvini aniqlash. Agar voicemail bo'lsa — oldindan belgilangan xabarni qoldirish yoki darhol go'shakni qo'yish.

---

## 3.5. Bosqich 5: Retell Post-Call/Chat Analytics & Extraction

Suhbat tugagandan keyin Retell kabi to'liq tahlil hisobotini avtomatik shakllantirish:
1. **Avtomatik Xulosa (LLM Summary):** Suhbatning 2-3 jumlalik lo'nda mazmuni.
2. **Sentiment Analysis:** Mijozning kayfiyati (`POSITIVE`, `NEUTRAL`, `NEGATIVE`).
3. **Structured Data Extraction:** Agentga berilgan `dataNeeded` sxemasi asosida (masalan, `promised_amount`, `appointment_date`, `delivery_address`) ma'lumotlarni matndan sug'urib olib, JSON formatida saqlash.
4. **Goal Evaluation:** Suhbat maqsadi bajarildimi yoki yo'qmi (`true/false`).
5. **Webhook Dispatch:** CRM tizimiga tayyor JSON xulosani yuborish.

---

## 3.6. Bosqich 6: Retell Darajasidagi Omnichannel Memory Dvigateli

Mijoz xotirasi tizimi (`uz.murodjon.robotcallv2.memory` va `agent/session`) Retell darajasiga olib chiqiladi:
1. **Tezkor Xotira Yuklash (Session Start):**
   - Telefon raqami (`phone`) yoki foydalanuvchi identifikatori bo'yicha `ClientMemory` Redis/DB'dan <10ms ichida o'qiladi.
2. **Zero-Latency Til Tanlash:**
   - Xotirada `preferredLanguage` bo'lsa, agent kiruvchi/chiquvchi qo'ng'iroqning birinchi soniyasidanoq shu tilda salom beradi.
3. **Dinamik Birinchi Xabar (First Message Personalization):**
   - Yangi mijoz uchun standart salom;
   - Avval gaplashgan mijoz uchun shaxsiy salom: *"Assalomu alaykum, {{memory.preferredName}}! Kechagi masalangiz bo'yicha bog'lanmoqdamiz..."*.
4. **Tizim Promptiga Aqlli Ineksiya (`### CLIENT MEMORY`):**
   - Avvalgi suhbatlar xulosasi va faktlar ro'yxati promptga tushadi;
   - Qat'iy Guardrail: *"Mijozdan allaqachon ma'lum faktlarni qayta so'rama, lekin 'bazamizda yozilgan' deb aytma"*.
5. **Conversational Flow va Double Curly Braces:**
   - Shartli o'tishlar: `if (memory.recentCalls.length > 0) -> goto Node_Existing_Client`;
   - Prompt o'zgaruvchilari: `{{memory.facts.carModel}}`, `{{memory.facts.debtAmount}}`.
6. **Cross-Channel Continuity (Voice ↔ Chat):**
   - Mijoz ovozli suhbatda aytgan gaplarini kechqurun Web Chat vidjetida ham tizim to'liq eslaydi.
7. **Post-Conversation Fact Consolidation:**
   - Suhbat yakunida `CallMemoryWriter` orqali yangi faktlar va xulosa avtomatik `client_memory` jadvaliga yangilanadi.

---

## 3.7. Bosqich 7: Tayyor 8 ta Retell Shablonini Joriy Qilish

Yuqorida 2-qismda keltirilgan 8 ta shablonni tizimga built-in qilib qo'shish:
1. `APPOINTMENT_BOOKING` (Qabulga yozilish)
2. `SUPPORT` (Mijozlarni qo'llab-quvvatlash va FAQ)
3. `LEAD_QUALIFICATION` (Lidlarni saralash / Cold Call)
4. `DEBT_REMINDER` (Qarz eslatma va to'lov va'dasi)
5. `INBOUND_RECEPTIONIST` (Aqlli kotiba / Qo'ng'iroqlarni yo'naltirish)
6. `SURVEY` (Xizmat sifatini baholash so'rovnomasi)
7. `MEDICAL` (Bemorlarni qabul qilish va shifokor tanlash)
8. `ORDER_TRACKING` (Buyurtma holati va qaytarish)

Foydalanuvchi UI'da "Yangi agent" tugmasini bosib, istalgan shablonni tanlaganida:
- O'zbek va rus tillaridagi tayyor mukammal promptlar;
- Barcha dinamik o'zgaruvchilar va tool schemalari;
- Kerakli ovoz, xotira va tahlil mezonlari avtomatik yuklanadi.

---

## 3.8. Bosqich 8: Telefon Raqamlari Markazi va Admin Pool / Marketplace (Sotib Olish & O'zinikini Qo'shish)

Kompaniyalar AI ovozli agentlarini zudlik bilan ishga tushirishlari uchun Retell uslubidagi telefon raqamlari xizmati yaratiladi:

1. **Platform Admin Raqamlar Ombori (Number Pool / Marketplace):**
   - Platforma administratori oldindan telekom provayderlaridan (Uztelecom, Beeline, Ucell, Perfectum yoki VoIP provayderlardan) 10–100+ ta raqamlarni platforma hovuziga kiritadi:
     - `phoneNumber`: Masalan `+998712001122`;
     - `category`: `STANDARD`, `SILVER`, `GOLD`, `PLATINUM`;
     - `providerTrunkId`: qaysi provayder orqali ulanganligi;
     - `monthlyFee`: oylik ijara narxi (masalan, $5 yoki 50 000 so'm);
     - `maxSupportedConcurrency`: raqam ko'tara oladigan maksimal liniyalar soni (masalan, 30 ta);
     - `pricePerConcurrencyChannel`: har bir qo'shimcha parallel kanal uchun oylik narx;
     - `status`: `AVAILABLE` (sotuvda), `RESERVED`, `ASSIGNED` (band).
2. **Kompaniya Tomonidan Raqam Sotib Olish (Tenant Purchase):**
   - Kompaniya sotuvdagi bo'sh raqamlarni ko'radi;
   - O'ziga yoqqan raqamni va **kerakli parallel liniyalar sonini** (masalan, 5 ta yoki 20 ta) tanlaydi;
   - Kompaniya hisobidan (Billing) mablag' yechiladi va raqam zudlik bilan kompaniyaga biriktiriladi (`assignedCompanyId = company.id`).
3. **O'z Raqamini Qo'shish (Bring Your Own Number / BYOT):**
   - Kompaniya o'zining mavjud SIP trunk login/parolini kiritadi (bizdagi `siptrunk` moduli orqali).
4. **Raqamni Boshqarish va Bog'lash (Inbound & Outbound Mapping):**
   - Har bir raqam uchun 1 klik bilan:
     - **Inbound AI Agent:** Ushbu raqamga kiruvchi qo'ng'iroq bo'lganda qaysi AI Agent yoki qaysi Call Flow (IVR) javob berishi tanlanadi (oddiy dropdown);
     - **Outbound Caller ID:** Chiquvchi kampaniyalar uchun Caller ID sifatida belgilash;
     - **Auto-renew & Cancel:** Oylik to'lovni avtomatik uzaytirish yoki istalgan payt bekor qilish.

---

# 3.9. Bosqich 9: Multi-Engine Telephoniya (LiveKit + LiveKit SIP, Asterisk, FreeSWITCH TODO) va Kompaniya Sozlamasi

### A. Strategik Maqsad
Klassik Asterisk PBX tizimi ofis ATS, operatorlar navbati (queue), IVR shoxlanishlari va ichki qo'ng'iroqlarni boshqarishda juda qulay. Ammo Retell va Vapi darajasidagi **sub-second latency** (ultra-past kechikishli AI ovoz suhbati) talab qilinganda, an'anaviy PBX ning kanallar buferi va bridging mexanizmlari ortiqcha kechikish olib keladi.

Shu sababli `robot-call-v2` platformasi **ko'p dvigatelli (Multi-Engine)** telekomunikatsiya arxitekturasiga o'tkaziladi:

1. **`LIVEKIT` (Retell & Vapi Standarti — Yangi Asosiy Dvigatel):**
   - WebRTC SFU va **LiveKit SIP Gateway** orqali tashqi SIP trunklar WebRTC audio xonalariga (Rooms) ulanadi;
   - Kechikish minimal (< 50–80ms transport kechikishi);
   - Veb-sayt vidjeti (brauzer) va telefon qo'ng'irog'i bir xil LiveKit Room va Audio Track orqali ishlaydi;
   - Real-time VAD, Turn-taking va streaming STT/LLM/TTS to'g'ridan-to'g'ri toza WebRTC audio oqimida bajariladi.
2. **`ASTERISK` (Mavjud PBX Dvigateli):**
   - Mavjud Asterisk 20 (ARI) + Netty RTP infratuzilmasi saqlanadi;
   - CallFlow (IVR), Ring Groups, Operator Queues, Agent-to-Human transfer va klassik ATS funksiyalariga muhtoj kompaniyalar uchun xizmat qiladi.
3. **`FREESWITCH` (TODO — Kelajakdagi Carrier-Grade Softswitch):**
   - Telekom operatorlari miqyosidagi yirik parallel qo'ng'iroqlar (10,000+ bir vaqtda) va SBC vazifasi uchun kelajak yo'l xaritasida (TODO) saqlanadi.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       MULTI-ENGINE TELEPHONY ARCHITECTURE                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   [ PLATFORMA ADMINISTRATORI (SUPER ADMIN) ]                                │
│   • Har bir kompaniyaga mos dvigatelni belgilaydi:                          │
│     CompanyConfig.telephonyEngine = ASTERISK | LIVEKIT | FREESWITCH         │
│                                                                             │
│                                      │                                      │
│                                      ▼                                      │
│   [ TELEPHONY ROUTER LAYER (`agent/telephony`) ]                            │
│   • Barcha chiquvchi/kiruvchi qo'ng'iroqlarni markazlashgan marshrutlash   │
│                                                                             │
│                 ┌────────────────────┴────────────────────┐                 │
│                 │                                         │                 │
│                 ▼                                         ▼                 │
│    [ LIVEKIT ADAPTER (`agent/livekit`) ]    [ ASTERISK ADAPTER (`agent/ari`)│
│    • LiveKit SIP Participant (Outbound)    • ARI Originate / Channel        │
│    • LiveKit SIP Dispatch (Inbound)        • ARI StasisStart / Bridge       │
│    • WebRTC Audio Tracks ──▶ AI Dialog     • Netty RTP Audio ──▶ AI Dialog  │
│    • Retell/Vapi darajasida tezlik (<80ms) • PBX, CallFlow, Operator Queues │
│                                                                             │
│                                      │ (TODO: Kelajakda)                    │
│                                      ▼                                      │
│                     [ FREESWITCH ADAPTER (TODO) ]                           │
│                     • Carrier-grade 10,000+ concurrency                     │
│                     • mod_audio_fork gRPC/WebSocket stream                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### B. Kompaniya (Tenant) Darajasida Dvigatel Tanlash

Platforma administratori har bir kompaniya qaysi telekomunikatsiya dvigatelidan foydalanishini erkin belgilaydi:

1. **Domen Modeli (`uz.murodjon.robotcallv2.company.domain.enums`):**
   - Yangi enum: `TelephonyEngine`:
     ```java
     public enum TelephonyEngine {
         ASTERISK,
         LIVEKIT,
         FREESWITCH
     }
     ```
2. **`CompanyConfig` Kengaytmasi:**
   - `telephonyEngine` maydoni qo'shiladi (default: `ASTERISK` — orqaga to'liq moslik uchun);
   - Ma'lumotlar bazasi Flyway migratsiyasi (`V...__company_telephony_engine.sql`):
     ```sql
     ALTER TABLE company_config 
     ADD COLUMN telephony_engine VARCHAR(32) NOT NULL DEFAULT 'ASTERISK';
     ```
3. **Platform Admin API & UI:**
   - `PATCH /api/companies/{id}/telephony-engine` (yoki mavjud `PUT /api/companies/{id}/config`) orqali platforma administratori 1 klikda kompaniyani `LIVEKIT` ga o'tkazadi.

---

### C. LiveKit + LiveKit SIP Qanday Mukammal Integratsiya Qilinadi?

1. **Infratuzilma (Docker & Kubernetes):**
   - `docker-compose.livekit.yml` orqali LiveKit Server + LiveKit SIP Gateway ishga tushiriladi;
   - Kompaniyaning SIP trunklari LiveKit SIP bilan sinxronlashtiriladi (`Outbound Trunk` va `Inbound Dispatch Rules`).
2. **Chiquvchi Qo'ng'iroq (Outbound Dialing Flow):**
   - `OutboundDialService` kompaniya sozlamasini tekshiradi (`companyConfig.telephonyEngine() == LIVEKIT`);
   - `LiveKitTelephonyService` orqali LiveKit Serverda maxsus xona ochiladi (`Room: call-{callAttemptId}`);
   - LiveKit SIP API orqali `CreateSIPParticipant` chaqiriladi (mijoz telefon raqami, SIP trunk ID, callerId);
   - LiveKit SIP darhol tashqi telefon tarmog'iga SIP INVITE yuboradi;
   - Mijoz go'shakni ko'targan lahzada u LiveKit xonasiga kiradi va audio stream ochiladi;
   - Backend `RealtimeDialogSession` xonaga bot ishtirokchi sifatida ulanib, ikki tomonlama WebRTC audio oqimini AI modellariga ulaydi.
3. **Kiruvchi Qo'ng'iroq (Inbound Call Flow):**
   - Mijoz kompaniya telefon raqamiga qo'ng'iroq qiladi;
   - LiveKit SIP raqamni aniqlab, `Dispatch Rule` asosida yangi xona yaratadi va Webhook orqali backendga (`/api/webhooks/livekit/sip`) xabar beradi;
   - Backend kompaniyaning `InboundRoute` sozlamasiga qarab tegishli `AiAgent` va `Scenario` ni xonaga ulaydi;
   - Natijada kechikish (latency) 0.5-0.7 soniyadan oshmaydi.

---

# 4-QISM: AMALGA OSHIRISH BOSQICHLARI VA HOLATI (STATUS)

Ushbu kengaytirilgan reja bo'yicha barcha asosiy arxitektura va komponentlar ishlab chiqildi:

- [x] **1-Qadam: Domen va DTO Refaktoringi (BAJARILDI)**
  - `AiAgentMode` (`SINGLE_PROMPT`, `CONVERSATIONAL_FLOW`) va `AiAgentChannel` (`VOICE`, `CHAT`, `OMNICHANNEL`) domen enumlari;
  - `AiAgent`, `AiAgentEntity`, DTO va Mapperlar yangilandi;
  - Flyway migratsiyasi: `V30__ai_agent_channel_and_mode.sql`.

- [x] **2-Qadam: Retell 8 ta Built-in Shablonlari Dvigateli (BAJARILDI)**
  - `AiAgentTemplates` (8 ta to'liq shablon: `APPOINTMENT_BOOKING`, `SUPPORT`, `LEAD_QUALIFICATION`, `DEBT_REMINDER`, `INBOUND_RECEPTIONIST`, `SURVEY`, `MEDICAL`, `ORDER_TRACKING`);
  - 3 ta tilda (UZ, RU, EN) tizim promptlari, birinchi xabar, `dataNeeded` va `dataEvaluation` mezonlari;
  - `AiAgentCatalogService.findTemplates()` integratsiyasi.

- [x] **3-Qadam: Omnichannel Memory Dvigateli (BAJARILDI)**
  - `ClientMemory` ni Voice qo'ng'iroq va Chat sessiyalari o'rtasida birlashtirish;
  - `ChatConversations` da `CallContext` ga mijoz xotirasini ineksiya qilish;
  - Chat va qo'ng'iroq yakunlanganda xotirani avtomatik saqlash (`rememberCall`).

- [x] **4-Qadam: Telefon Raqamlari Markazi (Marketplace & BYON) (BAJARILDI)**
  - `phonenumber` to'liq moduli (Hexagonal): `PhoneNumber`, Repository, Adapter;
  - Platforma admin hovuzi (`/api/platform/phone-numbers`);
  - Kompaniya marketplace'i, parallel kanallar (concurrency) narxlash va sotib olish (`/api/phone-numbers/purchase`);
  - Inbound AI Agent / CallFlow'ga 1 klikda bog'lash;
  - Retell uslubidagi "Test SIP Connection" (TCP/UDP socket reachability ping);
  - Flyway migratsiyasi: `V31__phone_number_marketplace.sql`.

- [x] **5-Qadam: Ko'p Dvigatelli Telephoniya va LiveKit SIP (BAJARILDI)**
  - `TelephonyEngine` (`ASTERISK`, `LIVEKIT`, `FREESWITCH`);
  - `CompanyConfig` sozlamasi va Flyway migratsiyasi `V32__company_telephony_engine.sql`;
  - `LiveKitProperties` va `LiveKitTelephonyService` xonalar va chiquvchi SIP participant dispatch;
  - `OutboundDialService` routeri (`ASTERISK` vs `LIVEKIT`);
  - Kiruvchi LiveKit SIP Webhook: `POST /api/webhooks/livekit/sip` (`LiveKitWebhookController` + UseCase + Service).

- [x] **6-Qadam: Conversational Flow Graf Dvigateli (BAJARILDI)**
  - `ScenarioDefinition`, `StageDef`, `GlobalNode`, `StageBranch` va `transitionConditions` orqali ko'p bosqichli FSM;
  - Har qanday bosqichda tushgan savollarni interceptor sifatida tutuvchi `GlobalNode`.

- [x] **7-Qadam: Birlashgan Chat va Voice Runtime (BAJARILDI)**
  - Yagona `DialogSession` va `TextTurnEngine` orqali ham telefon qo'ng'irog'i, ham Web/Telegram chatni boshqarish.

- [x] **8-Qadam: Voice Advanced Features (BAJARILDI)**
  - `AmbientSoundGenerator`, `RtpEndpoint` audio mikser (ofis, kafe, call-markaz foni);
  - Telephony Wiener shovqinni filtrlash, Silero VAD va past kechikishli gapni bo'lish (barge-in).

- [x] **9-Qadam: OpenAPI / Swagger Hujjatlari (BAJARILDI)**
  - `phoneNumber.yaml` va `platformPhoneNumber.yaml` spetsifikatsiyalari yaratilib, `openapi.yaml` ga ulandi.

---

Ushbu arxitektura natijasida `robot-call-v2` dunyodagi Retell AI, Vapi va Bland AI kabi eng ilg'or platformalar bilan bemalol raqobatlasha oladigan darajaga yetadi.
