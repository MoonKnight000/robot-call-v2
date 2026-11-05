# AI Ovozli Assistent: Imkoniyatlar, Arxitektura va Token Optimizatsiyasi Rejasi

> **Hujjat maqsadi:** Ushbu hujjat platformamizdagi AI ovozli assistentini (AI Voice Agent) oddiy "savol-javob qiluvchi bot" darajasidan xalqaro darajadagi **"Avtonom Ovozli Xodim" (Autonomous Voice Employee)** darajasiga ko'tarish bo'yicha to'liq strategiya, bozor tahlili, funksional qobiliyatlar matritsasi va texnik token optimizatsiyasini taqdim etadi.
> 
> Hujjat **Sierra AI** (Bret Taylor kompaniyasi, $4.5B), **Retell AI**, **Vapi**, **Bland AI** va **Decagon** arxitekturalari tahlili asosida shakllantirilgan.

---

## 1. ZAMONAVIY AI ASSISTENT QANDAY BO'LISHI KERAK?

Birgina ssenariy bo'yicha qotib qolgan gaplarni o'qiydigan botlar davri o'tdi. Zamonaviy ovozli agent quyidagi **6 ta asosiy qobiliyatga** ega bo'lishi shart:

```
┌────────────────────────────────────────────────────────────────────────┐
│                  ZAMONAVIY AI ASSISTENTNING 6 QOBILIYATI               │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
   ┌───────────────┬───────────────┼───────────────┬─────────────────┐
   ▼               ▼               ▼               ▼                 ▼
1. HARAKAT     2. E'TIROZLAR    3. JONLI TIL    4. TO'LIQ         5. AQLLI
QILISH         BILAN ISHLASH    (TABIIYLIK)     DUPLEKS           UZATISH
(SMS, CRM,     (Objection       (O'zbek/Rus     (Barge-in         (Summary bilan
To'lov)        Handling)        jonli nutq)     tabiiy pauza)     operatorga)
```

### 1.1. Tranzaksion Harakatlar (Action-Oriented) — Faqat Gap Sotmaslik!
Assistent suhbat davomida real amallarni bajara olishi kerak:
* **Suhbat paytida SMS yuborish:** *"Ali aka, hozir siz gapirib turgan paytingizda telefoningizga to'lov havolasini (yoki filial lokatsiyasini) SMS qilib yubordim, keldimi?"*
* **Tizimda qayd qilish:** Kelishilgan to'lov vaqti, summa va sabablarni darhol CRM yoki ma'lumotlar bazasiga yozish.
* **Taqvim integratsiyasi:** Mijoz bilan uchrashuv yoki qayta qo'ng'iroq vaqtini belgilash (`scheduleCallback`).
* **Verbal Tasdiq (Confirmation Guardrail):** Muhim moliyaviy qarorlar yoki harakatlardan oldin mijozdan aniq og'zaki tasdiq olish (`"500 ming so'm to'lovga rozimisiz?"`).

### 1.2. E'tirozlar bilan Psixologik Ishlash (Objection Handling)
Mijozlarning 80%i birinchi gapdayoq rozi bo'lmaydi: *"Pulim yo'q"*, *"Keyinroq qo'ng'iroq qiling"*, *"Siz kimsiz o'zi, menga robot kerakmas"*.
* **Yomon bot:** Bitta gapni qaytaradi: *"Iltimos, to'lang"*.
* **Bizning assistent:** Vazmin, hamdardlik bilan e'tirozni qabul qiladi va muqobil yo'l taklif etadi:
  > *"Tushunaman, Ali aka, hozir qiyin vaziyat bo'lishi mumkin. Keling, unda summani ikkiga bo'lib to'lash variantini ko'rib chiqamiz, maoshingiz qachon chiqadi?"*

### 1.3. Jonli va Samimiy Til (Byurokratiyasiz O'zbek va Rus tili)
* **Taqiqlangan so'zlar:** *"Sizga ko'maklashishdan bag'oyat mamnunman"*, *"Mablag'larni o'tkazishni amalga oshirsangiz"*.
* **Jonli so'zlar:** *"Assalomu alaykum! Xo'p bo'ladi, tushundim"*, *"Albatta, hozir tekshirib beraman"*, *"Rahmat, sog' bo'ling"*.
* **Avtomatik til almashtirish:** Agar mijoz gap orasida ruscha gapirsa, agent darhol ruschaga, o'zbekcha desa — o'zbekchaga silliq o'tishi kerak.

### 1.4. Pre-Tool Fillers (Kutish Paytida Tirik Reaksiya)
Tool (API/baza) orqali ma'lumot tekshirilayotganda 2-3 soniya o'lik jimlik bo'lmasligi kerak. Bizdagi `PreToolPhrases` mexanizmini to'liq ishlatib: *"Hozir, bir soniya ma'lumotlaringizni tekshiryapman..."* deb audio berilishi shart.

### 1.5. Smart Escalation (Operatorga Uzatishda Aqlli Konspekt)
Agar mijoz qattiq asabiylashsa yoki masala murakkablashsa:
* Agent: *"Ali aka, tushundim. Hozir sizni katta mutaxassisimizga ulayman, bir daqiqa kuting"*.
* Shu paytda operator ekranida butun suhbat tarixi emas, **3 qatorlik konspekt** chiqadi:
  > **Mijoz:** Ali Valiyev  
  > **Muammo:** Qarzdorlik muddatini 5 kunga surishni so'ramoqda  
  > **Holati:** Oyligi kechikkan, qisman to'lashga rozi

---

## 2. DUNYO BOZORIDAGI YETAKCHILAR TAHLILI (BENCHMARK)

| Platforma | Bozor Qiymati | Arxitektura Siri | Biz Loyihamizga Nimasini Olishimiz Kerak? |
|---|---|---|---|
| **Sierra AI** (Bret Taylor) | $4.5B (Enterprise gigant) | **Constellation of Models:** Bitta katta promptga hamma narsani yuklamaydi. LLM faqat til uchun, biznes mantiq esa qat'iy FSM va Agent OS da boshqariladi. | Biznes qoidalarni (qarz, muddat, limit) promptga ishonib qolmasdan, kod darajasida (FSM) qat'iy nazorat qilish. |
| **Retell AI** | Eng ommabop Voice AI | **Micro-prompts & Prefix Caching:** Har bir bosqich uchun alohida 50 tokenlik mikro-ko'rsatma. | Prompt hajmini 1,500 tokendan oshirmaslik. |
| **Vapi AI** | Developer platformasi | **Request-Start Tool Messages:** Har bir tool chaqirilganda 0ms kechikish bilan tayyor audio iborani ijro etish. | Tool ishlayotganda sukunatni (dead air) yo'qotish. |
| **Bland AI** | "Pathways" daraxti | **Dynamic Webhooks & SMS:** Qo'ng'iroq paytida tashqi dunyo bilan integratsiya. | Qo'ng'iroq paytida SMS yuborish va CRM ga yozish. |
| **Decagon** | $1B+ (Customer AI) | **Action Verification:** Moliyaviy harakatlardan oldin mijozdan aniq verbal tasdiq olish. | Xavfli amallarni ikki marta tasdiqlatish (`ToolConfirmation`). |

---

## 3. TEXNIK TOKEN OPTIMIZATSIYASI

Mijoz bilan 20 daqiqa gaplashganda ham xarajatlarni eng minimal darajada ushlab turish usullari:

```
┌─────────────────────────────────────────────────────────────┐
│ 1. STABLE PREFIX (1,500 token) — 100% KESHLANADI            │
│    Persona, ohang, umumiy qoidalar, xavfsizlik cheklovlari. │
│    Har bir turn'da keshdan olinadi (80% arzon).             │
├─────────────────────────────────────────────────────────────┤
│ 2. STRUCTURED SLOTS (Faktlar — 30-40 token)                 │
│    {ism: "Ali", sana: "10-noyabr", summa: 500000}           │
├─────────────────────────────────────────────────────────────┤
│ 3. ROLLING SUMMARY (Eski turnlar konspekti — 30 token)      │
│    "Mijoz boshida to'lamoqchi emasdi, bo'lib to'lashga rozi"│
├─────────────────────────────────────────────────────────────┤
│ 4. SLIDING WINDOW (Faqat oxirgi 2-3 turn — 150 token)       │
├─────────────────────────────────────────────────────────────┤
│ 5. CHIQISH CHEKLOVI (max_tokens: 60)                        │
│    Har turn'da atigi 25-35 ta so'z (1-2 gap).               │
└─────────────────────────────────────────────────────────────┘
```

### Amaliy Sozlamalar:
1. **Frequency & Presence Penalty:**
   * `frequency_penalty = 0.5` — Bir xil so'zlarni (*"Tushunarli"*, *"Xo'p"*) qayta-qayta takrorlashni taqiqlaydi.
   * `presence_penalty = 0.3` — Yangi mavzu va jonli so'zlarni qo'shishga undaydi.
2. **Kaskadli Model Tanlash (Smart Model Routing):**
   * Salomlashish, tasdiqlash (*"ha"*, *"yo'q"*): **Gemini Flash-Lite** yoki **Llama-3.3-8B** (narxi 10 barobar arzon, TTFT 80ms).
   * Murakkab e'tirozlar va hisob-kitoblar: **Gemini Flash/Pro**.
3. **Chiqish Tokenlarini Qat'iy Cheklash:**
   * `max_tokens = 60` — Ovozli assistent kitobiy monolog o'qishini to'xtatadi. 1-2 gap bilan javob berishini kafolatlaydi.

---

## 4. FOYDALANUVCHI QULAYLIGI (ERGONOMIKA & UX)

Platforma 3 toifa foydalanuvchi uchun juda qulay bo'lishi kerak:

### 4.1. Qo'ng'iroq Qiluvchi Mijoz Uchun (End-User UX):
* **Tabiiy Ohang va Emotsiya:** Monoton robot ovozi emas, mijozning kayfiyatiga qarab moslashuvchan intonatsiya (muloyim, xotirjam, qat'iy).
* **Bir Vaqtda Bitta Savol:** Bot bir vaqtda 3 ta savol bermaydi. Har bir replikada faqat bitta aniq masala ko'tariladi.
* **Barge-in (Gapni bemalol bo'la olish):** Mijoz xohlagan soniyada gapirsa, bot darhol to'xtab, mijozni tinglaydi.

### 4.2. Biznes Egasi va Supervayzer Uchun (Business UX):
* **No-code Visual Editor:** Ssenariy yaratishda dasturchiga muhtoj bo'lmasdan, qulay vizual bloklar orqali yangi bosqichlar qo'sha olish.
* **Jonli Tinglash va Aralashish (Barge-in & Whisper):**
  * `LISTEN`: Supervayzer qo'ng'iroqni bildirmasdan tinglaydi.
  * `WHISPER`: Faqat agentga pichirlab ko'rsatma beradi (mijoz eshitmaydi).
  * `BARGE`: O'zi suhbatga kirib, gaplashadi.
* **Avtomatik QA (Quality Assurance) Baholash:** Har bir qo'ng'iroq tugagach, orqa fonda arzon model butun suhbatni 100 ballik tizimda baholaydi:
  * *Maqsadga erishildimi? (Ha/Yo'q)*
  * *Mijoz noroziligi bo'ldimi?*
  * *Agent xushmuomala gapirdimi?*

### 4.3. Operator Uchun (Human-in-the-loop):
* Qo'ng'iroq uzatilganda ekranda bir soniyada yig'ilgan ma'lumotlar kartochkasi chiqadi, operator mijozdan *"Ismingiz nima? Nima bo'ldi?"* deb qayta so'rab uning g'ashiga tegmaydi.

---

## 5. BOSQICHMA-BOSQICH AMALGA OSHIRISH YO'L XARITASI

1. **1-bosqich (Tezkor natija — 1-2 kun):** `frequency_penalty`, `max_tokens(60)` parametrlarini ulash va tizim promptlariga jonli o'zbekcha dialog qoidalarini kiritish.
2. **2-bosqich (Funksional — 3-4 kun):** Qo'ng'iroq paytida SMS yuborish (`sendSmsPaymentLink`), e'tirozlar bilan ishlash modullarini kengaytirish.
3. **3-bosqich (Platforma — 1 hafta):** Post-call QA baholash va operatorga uzatishda 3 qatorlik Smart Summary chiqarish.
