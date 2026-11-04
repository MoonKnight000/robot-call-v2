# AI Voice Agent Tizim Promptlari Tahlili: Takrorlanish (Repetition Loop) va Noodatiy So'zlar Muammosining Yechimi

> **Hujjat maqsadi:** Ushbu hujjat ovozli AI agentlarda uchraydigan ikkita eng katta muammo — **bitta gap yoki qolipni tinimsiz takrorlash (repetition loops)** hamda **noodatiy, sun'iy, kitobiy so'zlarni ishlatish (unnatural/robotic vocabulary)** sabablarini tahlil qiladi. Shuningdek, sanoatdagi yetakchi tizimlar (**Retell AI**, **Vapi**, **Pipecat**, **TEN Framework**, **LiveKit Agents**) ushbu muammolarni qanday hal qilganini ko'rsatib, `robot-call-v2` loyihasi uchun tayyor yechimlar, parametrlar va prompt shablonlarini taqdim etadi.

---

## 1. MUAMMO TAHLILI: BU HOLATLAR QAYERDAN KELIB CHIQADI?

Ovozli muloqot paytida LLM yozma chatdagidan tubdan farq qiladi. Matnli chatda chiroyli, uzun va muloyim jumlalar tabiiy ko'rinsa, telefonda ular **"robotdek qotib qolgan", "qulog'iga gap kirmaydigan"** va g'alati taassurot qoldiradi.

---

### 1.1. Takrorlanish (Repetition & Looping) Sabablari:

Siz aytgan *"meniki hozir bitta gapni ko'p takrorlaydi"* muammosining 5 ta aniq texnik sababi bor:

1. **In-Context Learning (Chat Tarixidagi Kalka Effekti):**
   * LLM keyingi so'zni generatsiya qilayotganda o'zidan oldingi chat tarixiga qaraydi.
   * Agar 1-turnda bot *"Tushunarli, ... sizga ma'lumot beraman"* deb javob bergan bo'lsa, 2-turn va 3-turnda ham uning diqqati (attention) o'sha *"Tushunarli"* so'ziga tushadi va har bir javobni aynan shu so'z bilan boshlash ehtimolligi 80–90% ga oshib ketadi.
2. **Frequency Penalty va Presence Penalty ning Yo'qligi:**
   * LLM API larida ikkita eng muhim jazolash parametri mavjud:
     * `frequency_penalty`: Bir xil so'z yoki ibora kontekstda qancha ko'p uchrasa, uni qayta ishlatishni shuncha qattiq jazolaydi.
     * `presence_penalty`: Kontekstda allaqachon aytilgan mavzu/so'zlardan qochib, yangi so'zlarni kiritishga undaydi.
   * `TurnTools.java` va `LlmTurnClient.java` kodimizda bu parametrlar **0.0 (o'chiq)** turibdi! Natijada model o'zini o'zi takrorlashdan hech qanday cheklov his qilmaydi.
3. **Temperature Noto'g'ri Sozlanganligi:**
   * Agar `temperature = 0.0` yoki `0.2` bo'lsa (greedy decoding), model har doim matematik jihatdan eng yuqori ehtimolli bitta javobni tanlaydi. Masalan, mijoz *"ha"* desa, model har safar 100% bir xil *"Ajoyib, unda davom etamiz"* gapini aytadi.
4. **Tizim Promptidagi Majburiy Ko'rsatmalar:**
   * `SystemPromptFactory.java` ning 572-qatorida: `rules.add("... you may open your reply with a brief reaction (\"Xo'p\", \"Tushunarli\", \"Yaxshi\")");` degan qoida bor.
   * Garchi bu yerda *"you may"* (mumkin) deb yozilgan bo'lsa-da, LLM buni majburiyat deb qabul qiladi va har bir gapini *"Tushunarli"*, *"Xo'p"* bilan boshlashga odatlanib qoladi.
5. **Filler Echo (Tizim tovushini LLM ham aytishi):**
   * Agar tizim tool chaqirilganda mijozga *"Bir soniya, tekshiryapman..."* deb audio o'qisa, va bu matn LLM ga yetib borsa, LLM o'z javobida ham *"Bir soniya, tekshirib ko'rdim, mana..."* deb aytilgan narsani takrorlaydi.

---

### 1.2. Noodatiy va Sun'iy So'zlar Ishlatish Sabablari:

Nima uchun bot *"Sizga ko'maklashishdan mamnunman"*, *"Mablag'larni o'tkazishni amalga oshirsangiz"*, *"Tafsilotlariga oydinlik kiritsak"* kabi hayotda hech kim gapirmaydigan so'zlarni aytadi?

1. **O'quv Bazasining Kamligi (Data Imbalance):**
   * OpenAI (GPT-4o), Anthropic (Claude), Google (Gemini) va Meta (Llama) modellarining bazasida o'zbek tili umumiy hajmning **0.05%** dan ham kamrog'ini tashkil qiladi.
   * Bor o'zbekcha matnlar ham jonli telefon so'zlashuvlari emas, balki **Lex.uz (qonunlar), Vikipedia, rasmiy davlat gazetalari va tarjima kitoblardir**. Shu sababli model o'zbek tilini jonli insondek emas, **byurokrat yoki darslik muallifidek** biladi.
2. **Inglizcha Tushunchalardan To'g'ridan-to'g'ri Kalka:**
   * Promptlar ingliz tilida yozilgan: *"Be polite and assist the customer"*.
   * Model buni o'zbekchaga tarjima qilganda *"assist"* so'zini lug'atdagi rasmiy *"ko'maklashmoq"* deb, *"delighted"* so'zini *"bag'oyat mamnunman"* deb tarjima qiladi. Jonli tildagi *"yordam bermoq"* yoki *"jonim bilan"* degan tabiiy shakllarni bilmaydi.
3. **Qat'iy Lug'at Cheklovining (Blacklist / Whitelist) Yo'qligi:**
   * Promptda *"natural Uzbek"* deb yozish yetarli emas. Modelga qaysi so'zlarni **hech qachon aytmaslik** va ularning o'rniga qaysi jonli so'zlarni aytish kerakligini qat'iy jadval qilib berish lozim.

---

## 2. BOSHQALARDA BU QANDAY HAL QILINGAN? (Dunyo Tajribasi)

Sanoatdagi eng ilg'or ovozli agent platformalari (**Retell AI**, **Vapi**, **Pipecat**, **LiveKit**, **TEN**) bu ikki muammoni quyidagi 4 ta usul bilan butunlay yo'qotgan:

---

### 2.1. Retell AI va Vapi Arxitekturasi: "Spoken Phone Register" Qoidasi
Retell AI va Vapi o'z tizim promptlarining eng yuqorisiga quyidagi asosiy tamoyilni kiritadi:

> **"SPOKEN PHONE CONVERSATION RULE (STRICT):**
> You are speaking OUT LOUD over a poor quality mobile phone connection. You are NOT writing an email, a blog post, a legal contract, or an essay.
> * Speak like a real 25-year-old friendly local office employee sitting at their desk.
> * Maximum 1–2 short sentences per turn.
> * Never use flowery, academic, or bureaucratic words."

Shuningdek, ular **Taqiqlangan va Ruxsat etilgan so'zlar lug'atini (Blacklist Table)** promptga majburiy qilib kiritadi.

---

### 2.2. Sampling Parametrlari (Sampling & Penalty Tuning)
Retell AI va Vapi ovozli suhbatlar uchun LLM parametrlarini quyidagicha qat'iy sozlaydi:

```json
{
  "temperature": 0.65,
  "frequency_penalty": 0.45,
  "presence_penalty": 0.35,
  "max_tokens": 100
}
```
* **`frequency_penalty: 0.45`:** Model oldingi turnda aytgan so'zlarini (masalan, "Tushunarli", "Albatta") qayta aytishini to'xtatadi.
* **`presence_penalty: 0.35`:** Modelni bir xil savolni qayta-qayta chaynashdan to'xtatib, suhbatni keyingi bosqichga olib o'tishga majbur qiladi.
* **`temperature: 0.65`:** Javoblarning jonli va rang-barang chiqishini ta'minlaydi (takroriy qoliplarga tushib qolmaydi).
* **`max_tokens: 80–120`:** Modelning doston yozib yuborishini jismonan cheklaydi.

---

### 2.3. Pipecat va LiveKit: Chat Tarixini Tozalash (History Sanitization)
Pipecat va LiveKit'da modelning o'zini o'zi takrorlashining oldini olish uchun chat tarixini LLM ga yuborishdan oldin maxsus tozalash algoritmi ishlaydi:
1. **Fillerlarni tarixga qo'shmaslik:** Tizim aytgan fon frazalari ("Bir daqiqa", "Hozir tekshiraman") LLM ning chat tarixiga yozilmaydi! Tarix faqat toza mijoz va bot mazmunidan iborat bo'ladi.
2. **Bir xil prefikslarni qirqish:** Agar LLM avvalgi javobida "Tushunarli..." deb boshlagan bo'lsa, chat tarixiga bu so'z saqlanmaydi yoki faqat asosiy fikr qoldiriladi.

---

### 2.4. Few-Shot Contrastive Examples (Qarama-qarshi Namunalar)
TEN Framework va Vapi tadqiqotlari shuni ko'rsatadiki, modelga shunchaki *"takrorlama"* yoki *"tabiiy gapir"* deb aytish foyda bermaydi. Unga **`✗ XATO -> ✓ TO'G'RI`** ko'rinishidagi aniq juftliklar berilishi shart.

---

## 3. ROBOT-CALL-V2 UCHUN TAYYOR YECHIM VA QOIDALAR

Ushbu muammolarni bartaraf qilish uchun loyihamizda zudlik bilan qo'llanishi lozim bo'lgan 4 ta amaliy qadam:

---

### 3.1. Taqiqlangan va Jonli Muqobil So'zlar Jadvali (Lug'at Filtrlari)

Quyidagi jadval to'g'ridan-to'g'ri tizim promptiga (`platform-guardrails.txt` va `SystemPromptFactory.java`) kiritiladi. Model bu so'zlarni ko'rganda ularni darhol jonli tildagi muqobillariga almashtiradi:

| ✗ Taqiqlangan (Sun'iy, Kitobiy, G'alati) | ✓ Majburiy Jonli Muqobili (O'zbekcha) | Nega taqiqlanadi? |
|---|---|---|
| **ko'maklashmoq / ko'maklashish** | **yordam bermoq / qarashib yuborish** | Kitobiy kalka. Telefonda hech kim "ko'maklashaman" demaydi. |
| **mablag' / mablag'lar** | **pul / to'lov** | Byurokratik rasmiyatchilik. Odamlar "pulni to'lash" deydi. |
| **amalga oshirmoq / amalga oshirish** | **qilmoq / to'lamoq / tashlamoq** | Rasmiy hujjat tili. "To'lovni amalga oshiring" emas, "To'lab yuboring". |
| **ushbu / mazkur** | **bu / shu** | "Ushbu xonadon" emas, "Shu uy" yoki "Bu kvartira". |
| **tafsilotlar / tafsilotlariga** | **ma'lumotlar / qanaqaligi** | Juda og'ir so'z. "Ma'lumotlarini aytib yuborsangiz" deyiladi. |
| **taqdim etmoq** | **bermoq / tashlab bermoq** | "Sizga ma'lumot taqdim etaman" emas, "Aytib beraman". |
| **bag'oyat mamnunman** | **jonim bilan / bajonidil / xursandman** | Sun'iy muloyimlik, soxta eshitiladi. |
| **qondirildi** | **hal bo'ldi / yechildi** | "Murojaatingiz qondirildi" sud qaroriga o'xshaydi. |
| **zahmat chekmang** | **xavotir olmang / hechqisi yo'q** | Qadimgi adabiy til. |
| **suhbatdoshim / mijoz / fuqaro** | **[Ism] aka / [Ism] opa** | Insonga "mijoz" deb murojaat qilish hurmatsizlik. |
| **qiyinchilik bo'ldimi?** | **nima sababdan to'lanmay qoldi?** | Qotib qolgan robot iborasi. |
| **Iltifot qiling / Marhamat qiling** | **Marhamat / Eshitaman** | Haddan tashqari balandparvoz. |
| **aniqlik kiritib olsak** | **bilib olaylik / aniqlashtiraylik** | Yig'ilish tili. |

---

### 3.2. Takrorlanishni To'xtatuvchi Anti-Repetition Prompt Bloklari

Ushbu blok tizim promptiga qo'shilganda model har bir gapini bir xil boshlashdan to'xtaydi:

```text
ANTI-REPETITION AND CONVERSATION FLOW (STRICT):
1. NEVER START CONSECUTIVE REPLIES WITH THE SAME WORD:
   - If your previous reply started with "Tushunarli", you are FORBIDDEN from starting the next reply with "Tushunarli"!
   - Never overuse fillers ("Tushunarli", "Xo'p", "Yaxshi", "Albatta"). Most of your replies should start IMMEDIATELY with the answer or the next question without any opening filler at all!
2. NO ECHOING (PARROTING):
   - Never repeat back what the client just clearly said! 
   - ✗ Wrong: Client: "Men 3 xonali uy qidiryapman." -> Bot: "3 xonali uy qidiryapsiz, tushunarli. Qaysi tumandan?"
   - ✓ Right: Client: "Men 3 xonali uy qidiryapman." -> Bot: "Qaysi tumandan bo'lsin, mo'ljalingiz bormi?"
3. NEVER RE-INTRODUCE YOURSELF:
   - You greet and state the company name ONLY ONCE at the start of the call. Never say "Salom", "Assalomu alaykum", or your name/company again during the conversation.
4. DO NOT REPEAT UNANSWERED QUESTIONS VERBATIM:
   - If the client did not answer your previous question or gave a vague answer, do NOT repeat the exact same sentence. Rephrase it in simpler, shorter spoken words like a human would.
```

---

### 3.3. Jonli Telefon Suhbatlari Uchun Few-Shot Dialoq Namunalari

Modelga haqiqiy jonli o'zbekcha telefon dialoglarini namunaviy juftliklar (`EXAMPLES`) sifatida berish orqali uning tilini 100% o'zgartirish mumkin:

```text
EXAMPLES OF NATURAL SPOKEN UZBEK (HOW TO TALK ON THE PHONE):

Example 1 (Customer confirms):
Client: "Ha, to'g'ri."
✗ Wrong: "Tushunarli, ma'lumotni tasdiqlaganingiz uchun minnatdorman. Ushbu to'lovni qachon amalga oshirasiz?"
✓ Right: "Yaxshi. Qachonga qilasiz to'lovni?"

Example 2 (Customer asks about price):
Client: "Narxi qancha ekan buni?"
✗ Wrong: "Ushbu xonadonning umumiy qiymati 600 million so'mni tashkil etadi, tafsilotlari bilan tanishasizmi?"
✓ Right: "600 million so'm. O'zingizga qarayapsizmi yoki investitsiyagami?"

Example 3 (Customer did not hear):
Client: "Eshitmadim, nima dedingiz?"
✗ Wrong: "Qaytadan ma'lumot beradigan bo'lsam, to'lov muddati kechikkan."
✓ Right: "Aka, to'lov vaqti o'tib ketibdi, shuni birga hal qilaylik degandim."

Example 4 (Customer is busy):
Client: "Hozir gaplasholmayman, mashinadaman."
✗ Wrong: "Tushundim, sizga noqulaylik tug'dirganimdan afsusdaman. Qachon bog'lansam bo'ladi?"
✓ Right: "Mayli, unda xalaqit bermay. Qachon telefon qilay qaytib?"
```

---

### 3.4. Java Kodiga Kiritilishi Kerak Bo'lgan Parametrlar (`TurnTools.java`)

Model bitta gapni takrorlamasligi uchun `TurnTools.java` dagi `buildOptions` metodiga quyidagi parametrlarni qo'shish tavsiya etiladi:

```java
// TurnTools.java ichida ChatOptions qurish paytida:
if (aiModel.temperature() != null) {
    builder.temperature(aiModel.temperature());
} else {
    builder.temperature(0.65); // Ovozli agent uchun optimal jonli harorat
}

// Takrorlanishni to'xtatuvchi jazolash koeffitsiyentlari:
builder.frequencyPenalty(0.40); // Bir xil so'zlarni qayta ishlatishni to'xtatadi
builder.presencePenalty(0.30);  // Yangi mavzu va jumlalarga undaydi
```

---

## 4. UNIVERSAL TIZIM PROMPTI SHABLONI (TAYYOR NUSXA OLISH UCHUN)

Quyidagi prompt shablonini istalgan ssenariy yoki agentning `systemPrompt` qismiga qo'yish orqali takrorlanish va noodatiy so'zlarni darhol yo'qotish mumkin:

```markdown
SIZNING ROLINGIZ:
Siz kompaniyaning samimiy, tajribali va jonli telefon operatorisiz. Mijoz bilan oddiy mobil telefon orqali gaplashyapsiz.

ASOSIY QOIDALAR (QAT'IY):
1. JONLI SO'ZLASHUV TILI:
   - Hech qachon kitobiy, byurokratik yoki qog'ozdagi rasmiy tilda gapirmang!
   - 25 yoshli Toshkentdagi samimiy, odobli xodim qanday gapirsa, shunday gapiring.
   - Har bir javobingiz MAKSIMAL 1–2 ta qisqa jumlalardan iborat bo'lsin. Uzun nutq so'zlash TAQIQLANADI.

2. TAQIQLANGAN SO'ZLAR (ULARNI HECH QACHON AYTMANG):
   - "ko'maklashmoq" EMAS -> "yordam bermoq" deb ayting.
   - "mablag'" EMAS -> "pul" yoki "to'lov" deb ayting.
   - "amalga oshirmoq" EMAS -> "qilmoq" yoki "to'lamoq" deb ayting.
   - "ushbu", "mazkur" EMAS -> "bu", "shu" deb ayting.
   - "tafsilotlar" EMAS -> "ma'lumotlar" deb ayting.
   - "bag'oyat mamnunman" EMAS -> "jonim bilan" yoki "albatta" deb ayting.
   - "suhbatdosh", "mijoz", "fuqaro" EMAS -> "[Ism] aka" yoki "[Ism] opa" deb murojaat qiling.

3. BIR XIL GAP VA SO'ZLARNI TAKRORLASH TAQIQLANADI:
   - Ketma-ket javoblarni bir xil so'z bilan boshlash QAT'IYAN MAN ETILADI! 
   - Har bir gapni "Tushunarli", "Xo'p" deb boshlashni to'xtating. Ko'p hollarda gapni TO'G'RIDAN-TO'G'RI javob yoki savol bilan boshlang.
   - Mijoz aytgan gapni to'tiqushdek qaytarmang (Echoing taqiqlanadi).
   - Salomlashish va kompaniya nomini faqat suhbat boshida BIR MARTA ayting, suhbat davomida qayta takrorlamang.

4. HAR BIR JAVOBINGIZ ANIQ SAVOL BILAN TUGASIN:
   - Mijozga ma'lumot berib, jim bo'lib qolmang. Suhbat to'xtab qolmasligi uchun jumlani doim mantiqiy savol bilan yakunlang.
```

---

## 5. XULOSA VA AMALIY TAVSIYALAR

1. **Bugunoq qilish kerak bo'lgan ish:**
   * `platform-guardrails.txt` va ssenariylarga §3.1 dagi **Taqiqlangan so'zlar jadvali** hamda §3.2 dagi **Anti-Repetition blokini** joylashtirish.
2. **Keyingi texnik qadam:**
   * `TurnTools.java` da `OpenAiChatOptions` va boshqa modellarga `frequencyPenalty(0.40)` hamda `presencePenalty(0.30)` parametrlarini integratsiya qilish.
   * `temperature` ni 0.6–0.7 oralig'ida belgilash.

Bu o'zgarishlar kiritilishi bilanoq botingiz o'zini takrorlashdan to'xtaydi, soxta robotik iboralar yo'qoladi va suhbat xuddi jonli inson bilan gaplashayotgandek tabiiy bo'ladi.
