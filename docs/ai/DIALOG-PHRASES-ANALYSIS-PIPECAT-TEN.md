# Voice Agent Frazalari va Statik Qoidalar Tahlili: Pipecat, TEN Framework va Robot-Call-v2 Bo'yicha Katta Solishtirma

> **Hujjat maqsadi:** Ushbu tahlil `robot-call-v2` arxitekturasidagi barcha statik (hardcoded) iboralar, regexlar, qoidalar va lug'atlarni to'liq xatlovdan o'tkazish, ularni kompyuterdagi **Pipecat** (`IdeaProjects/pipecat`) hamda **TEN Framework** (`IdeaProjects/ten-framework`) yondashuvlari bilan chuqur solishtirish va loyihamiz uchun eng samarali **Gibrid Arxitektura** rejasini belgilash uchun tayyorlandi.

---

## 1. MASALA QO'YILISHI: NEGA BU MASALA MUHIM?

Ovozli sun'iy intellekt agentlarida (AI Voice Agent) ikkita o'zaro qarama-qarshi talab mavjud:
1. **Ultra past kechikish (Low-latency, <500 ms):** Telefoniya (SIP/RTP) orqali gaplashganda, odam 500–800 ms dan ortiq jimlikni noqulaylik deb hisoblaydi, 1.5–2 soniyadan oshsa — go'shakni qo'yib qo'yadi. Shu bois tezkor javoblar, oraliq qistirmalar (fillers) va favqulodda xabarlar darhol yangrashi kerak.
2. **Tabiiylik va moslashuvchanlik (Human-like conversational flow):** Agar agent har bir holatda aynan bir xil quruq, oldindan yozilgan so'zlarni takrorlasa, u zamonaviy LLM agenti emas, oddiy IVR/robotga aylanib qoladi.

Bizning loyihamizda qator komponentlar (masalan `DialogPhrases`, `FastPathRouter`, `FloorRules`, `PreToolPhrases`) Java kodida statik qilib yozilgan. Savol tug'iladi: **Bularning qaysilari statik bo'lishi shart, qaysilari esa agentni sun'iy qilib qo'ymoqda va ularni qanday yangilash lozim?**

---

## 2. PIPECAT ARXITEKTURASI VA KOD TAHLILI (`IdeaProjects/pipecat`)

Pipecat — Daily.co tomonidan yaratilgan audio-video streaming reaktiv pipeline freymvorki. Uning ichki kodida frazalar va oraliq holatlar quyidagicha yechilgan:

### 2.1. Jimlik nazorati: Kontekstli LLM + Kaskadli eslatma (`UserIdleController`)
* **Fayllar:** `src/pipecat/turns/user_idle_controller.py`, `examples/turn-management/turn-management-detect-user-idle.py`
* Pipecat foydalanuvchi jim qolganda quruq statik gap bilan cheklanmaydi. U **ko'p bosqichli eskalatsiya (escalating prompts)** tizimini qo'llaydi:

```python
# 1-urinish: LLM kontekstiga "developer" xabari kiritiladi (suhbat mavzusiga moslab so'raydi)
if self._retry_count == 1:
    message = {
        "role": "developer",
        "content": "The user has been quiet. Politely and briefly ask if they're still there."
    }
    await aggregator.push_frame(LLMMessagesAppendFrame([message], run_llm=True))

# 2-urinish: Qat'iyroq LLM murojaati
elif self._retry_count == 2:
    message = {
        "role": "developer",
        "content": "The user is still inactive. Ask if they'd like to continue our conversation."
    }
    await aggregator.push_frame(LLMMessagesAppendFrame([message], run_llm=True))

# 3-urinish: Chiziq uzilgan deb topiladi va qat'iy STATIK TTS bilan xayrlashiladi
else:
    await aggregator.push_frame(
        TTSSpeakFrame("It seems like you're busy right now. Have a nice day!")
    )
    await aggregator.push_frame(EndWorkerFrame())
```

**Natija:** 1-urinishda agent shunchaki "Alo, eshityapsizmi?" demaydi, balki kontekstdan kelib chiqib tabiiy murojaat qiladi: *"Murod aka, 2-xonali xonadon narxi haqida nima deysiz, eshityapsizmi?"*. Faqat 3-marta jimlik bo'lgandagina statik audio bilan qo'ng'iroq uziladi.

### 2.2. Tinglash tovushlari: Maxsus kadr turi (`BotBackchannelFrame`)
* **Fayl:** `src/pipecat/frames/frames.py`
```python
@dataclass
class BotBackchannelFrame(TTSSpeakFrame):
    """A short acknowledgment for the bot to say while the user talks, such as 'Mm-hmm.'."""
    text_type: TextType | str = field(default=TextType.BACKCHANNEL, init=False)
```
Pipecat backchannel frazalarini (`"Aha"`, `"Mm-hmm"`, `"Right"`) maxsus `TextType.BACKCHANNEL` kadri orqali yuboradi. Agar bot bu tovushni chiqarayotgan paytda mijoz gapirishda davom etsa, Pipecat buni **Barge-in / Interruption deb hisoblamaydi** va bot nutqini uzmaydi.

### 2.3. Ssenariy bo'yicha deterministik iboralar (`pipecat/flows/actions.py`)
Pipecat Flows tizimida ssenariy tugunlari o'rtasida o'tishda aniq talab qilingan matnlar uchun `tts_say` action'i ishlatiladi:
```json
{
  "type": "tts_say",
  "text": "Bir daqiqa, ma'lumotlarni bazadan tekshirib beraman..."
}
```

---

## 3. TEN FRAMEWORK ARXITEKTURASI VA KOD TAHLILI (`IdeaProjects/ten-framework`)

TEN (Agora'ning ultra-past kechikishli C++ va Python platformasi) suhbatdagi iboralarni asosan mikro-modellar va promptlar orqali boshqaradi:

### 3.1. Turn Detector Klassifikatori (`ten_turn_detection`)
* **Fayl:** `ai_agents/agents/ten_packages/extension/ten_turn_detection/turn_detector.py`
TEN matnni qattiq regexlar bilan emas, tezkor classifier orqali 3 holatda baholaydi:
* `unfinished`: foydalanuvchi hali gapini tugatmagan.
* `wait`: foydalanuvchi o'ylamoqda ("hmm...", "kutib turing...").
* `finished`: navbat botga o'tdi.

### 3.2. Prompt orqali frazalarni yo'naltirish (`property.json`)
TEN da agentning barcha o'zini tutishi va iboralari System Prompt ichidagi qoidalar orqali belgilanadi:
```markdown
## Key Phrases & Fillers
- If user asks to hold on, reply with short phrases like: "Sure, take your time."
- Avoid filler phrases like "here's how" or "to do X".
- When asked to be quiet, simply reply: "Understood", "Got it".
```

### 3.3. Statik parametrlar
Faqat birinchi kirish salomlashuvi (`greeting: "Can I help you?"`) statik parametr bo'lib turadi (birinchi millisekundlarda LLM kechikishi bo'lmasligi uchun).

---

## 4. `robot-call-v2` DAgI BARCHA STATIK KOMPONENTLAR INVENTARIZATSIYASI

Loyihamizda tekshiruv natijasida quyidagi 8 ta asosiy statik komponent aniqlandi:

### 4.1. `DialogPhrases.java` (Tizim frazalari)
* **Nimalar statik:**
  * `disclosure`: AI ekanligi va yozib olinayotgani haqidagi ogohlantirish.
  * `recordingNotice`: Faqat yozib olinish bildirishnomasi.
  * `farewell`: Turn/vaqt limiti yetgandagi vidolashuv.
  * `didNotCatch`: LLM bo'sh javob berganda qayta so'rash.
  * `transferring`: Operatorga ulash bildirishnomasi.
  * `stillThere` / `stillThereLastCall`: Jimlikdagi statik so'rovlar.
  * `technicalError` / `reconnected`: Favqulodda xatolar.
  * `thinking`: O'ylash paytidagi iboralar (*"Bir soniya."*, *"Bir lahza."*).
  * `backchannels`: Tinglash signallari (*"Aha."*, *"Tushunarli."*, *"Eshityapman."*).
* **Nega statik qilingan:** `TtsWarmup.java` server start bo'lishi bilan bularni sintezlab, RAM keshga qo'yadi. Qo'ng'iroq ulanishi bilan audio **0 ms da** yangraydi.

### 4.2. `FastPathRouter.java` (LLM siz tezkor yo'naltirish, <20 ms)
* **Nimalar statik:**
  * `WRONG_PERSON`: *"men emasman"*, *"adashdingiz"*, *"noto'g'ri tushdingiz"*, *"boshqa odam"*, *"вы ошиблись"*.
  * `DO_NOT_CALL`: *"boshqa telefon qilmang"*, *"bezovta qilmang"*, *"spam"*, *"не звоните"*.
  * `TRANSFER`: *"operatorga ula"*, *"tirik odam bormi"*, *"соедините с оператором"*.
  * `CLOSING_GRATITUDE`: *"rahmat xayr"*, *"salomat bo'ling"*, *"boshqa savol yo'q"*, *"спасибо до свидания"*.
  * `REPEAT`: *"nima dedingiz"*, *"qaytaring"*, *"повторите"*.
* **Vazifasi:** Agar mijoz shu iboralardan birini aytsa, LLM ga so'rov yuborilmaydi (0 token sarfi, <20 ms javob).

### 4.3. `FloorRules.java` (Barge-in: Gapni bo'lish va tinglash morfologiyasi)
* **Nimalar statik:**
  * O'zbek tili va rus tili grammatikasi asosidagi qoidalar to'plami:
    * `TAKING_THE_FLOOR`: *"kerak"*, *"emas"*, *"kim"*, *"to'xtang"*, *"operator"*, *"stop"*, *"savol"*.
    * `STEMS` (o'zaklar): *"toxta"*, *"shoshma"*, *"kuting"*, *"daqiqa"*, *"nima"*, *"qachon"*.
    * `NEGATIVE_ENDINGS`: *"-mang"*, *"-mayman"*, *"-maydi"*, *"-madim"*, *"-mas"*.
    * `QUESTION_ENDINGS`: *"-sizmi"*, *"-mizmi"*, *"-dimi"*, *"-mikan"*.
    * `LISTENING` (shunchaki tinglash): *"ha"*, *"aha"*, *"xop"*, *"mayli"*, *"tushunarli"*, *"gapiravering"*.
* **Vazifasi:** Bot gapirayotganda mijoz tovush chiqarsa, modelga murojaat qilmasdan, darhol "mijoz gapni bo'lyaptimi yoki shunchaki 'aha' deyaptimi?" degan qarorga keladi.

### 4.4. `PreToolPhrases.java` (Vositalar chaqirilayotgandagi oraliq gaplar)
* **Nimalar statik:**
  * Faqat 6 ta qat'iy vosita uchun kodda yozilgan xabarlar:
    * `sendSmsPaymentLink`: *"Hozir to'lov havolasini SMS orqali yuborishni rasmiylashtiryapman..."*
    * `recordPaymentPromise`: *"Hozir to'lov ma'lumotlarini tizimda qayd qilyapman..."*
    * `requestPaymentExtension`: *"Hozir to'lov muddatini uzaytirish so'rovingizni tekshiryapman..."*
    * `scheduleCallback`, `requestHumanTransfer`, `knowledgeBaseQuery`.
* **Muammo:** Boshqa yangi qo'shilgan vositalar (masalan, Uysot CRM'ning `listResidentialComplexes`, `searchFlats`, `createLead` yoki tashqi Webhook'lar) uchun bu ro'yxatda xabar yo'q (`null` qaytadi). Natijada ular bajarilayotganda bot jim qoladi.

### 4.5. `Farewells.java` (Suhbat yakunini aniqlash)
* **Nimalar statik:**
  * `AGENT_CLOSINGS`: Bot nutqining oxirgi 60 belgisida *"xayr"*, *"salomat bo'ling"*, *"kuningiz xayrli bo'lsin"*, *"до свидания"* kabi so'zlar bo'lsa va jumla so'roq belgisi bilan tugamasa, qo'ng'iroq avtomatik tugatiladi.
* **Afzallik/Kamchilik:** LLM `endCall` toolini chaqirishni unutib qo'ysa, tizim qo'ng'iroq osilib qolishidan qutqaradi. Lekin ba'zida bot xushmuomalalik uchun xayrlashib, ketidan savol bersa (savol belgisi tushib qolsa), qo'ng'iroq xato uzilishi mumkin.

### 4.6. `Affirmations.java` (Tasdiqlashni tushunish)
* **Nimalar statik:**
  * `YES` (*"ha"*, *"xa"*, *"xop"*, *"bopti"*, *"boladi"*, *"mayli"*, *"albatta"*, *"togri"*, *"tasdiqlayman"*, *"roziman"*, *"да"*, *"верно"*).
  * `NO` (*"yoq"*, *"emas"*, *"kerakmas"*, *"нет"*, *"не"*).
  * `FILLERS` (*"aka"*, *"opa"*, *"endi"*, *"hammasi"*, *"rahmat"*).
* **Vazifasi:** Fakt yoki buyurtma tasdiqlanayotganda ("Tasdiqlaysizmi?") 4 tagacha so'zdan iborat javoblarni LLM siz, 0 ms da tasdiqlaydi.

### 4.7. `TranscriptTurnCues.java` (Matndan gap chala qolganini aniqlash)
* **Nimalar statik:**
  * `UZ_INCOMPLETE_WORDS`: *"chunki"*, *"agar"*, *"lekin"*, *"va"*, *"balki"*, *"sababi"*, *"chunki"*.
  * `UZ_COMPLETE_WORDS`: *"rahmat"*, *"bo'ldi"*, *"xayr"*, *"tushundim"*.
* **Vazifasi:** Mijoz gapi bog'lovchi yoki shart so'z bilan uzilsa, VAD jimlik deb topsa ham, bot shoshmasdan kutib turadi.

### 4.8. `UzbekNumberParser.java` va `SpokenDates.java` (Raqam va sanalar)
* **Vazifasi:** *"ikki yuz ellik ming"* -> `250000`, *"ertaga soat o'nda"* -> sana va vaqt. Bu qat'iy grammatik qoida bo'lib, uning statik bo'lishi 100% to'g'ri.

---

## 5. KATTA TAQQOSLASH MATRITSASI

| Vazifa / Holat | `robot-call-v2` | Pipecat | TEN Framework | Qaysi biri eng to'g'ri? |
|---|---|---|---|---|
| **Disclosure & Legal Notice** | Statik kesh (`DialogPhrases`) | Statik parametr / tts_say | Statik greeting / prompt | **Statik kesh.** Qonuniy talab, LLM xomxayoliga ishonib bo'lmaydi, 0 ms boshlanishi shart. |
| **Texnik nosozlik / Uzilish** | Statik kesh (`technicalError`) | Pipeline Error frame | Extension callback | **Statik kesh.** LLM yiqilgan paytda faqat keshdagi audio qutqaradi. |
| **Operatorga ulash** | Statik kesh (`transferring`) | Tool call + `tts_say` | Action + audio | **Statik kesh.** Zudlik bilan ijro etilishi shart. |
| **Jimlik nazorati (Silence)** | Statik (`stillThere` 1, 2, 3) | **Gibrid:** 1-2 LLM, 3-statik TTS | Micro-model classifier | **Pipecat usuli eng zo'r.** Birinchi urinishda LLM mavzuga moslab tabiiy so'raydi. |
| **O'ylash (Thinking Fillers)** | Faqat 2 ta statik so'z | LLM streaming / minimal filler | Prompt orqali boshqarish | **Persona Pool.** Har bir agentning 5–10 xil boyroq iboralari ro'yxati. |
| **Vosita oraliq xabari (Pre-tool)** | Hardcoded 6 ta tool (`Map`) | Dinamik `tool_call.message` | `manifest.json.progress` | **Dinamik (Tool darajasida).** Har bir yangi vosita (CRM, Webhook) o'z xabariga ega bo'lishi kerak. |
| **Barge-in / Tinglash (Floor)** | Morfologik lug'at (`FloorRules`) | Akustik model (`SmartTurn v3`) | Micro-LLM turn detector | **Bizdagi morfologiya juda tez (0 ms).** Kelajakda namo-text-turn modeli bilan mustahkamlash mumkin. |
| **Qo'ng'iroq yakuni (Hangup)** | Matn qidirish (`Farewells`) | Tool `end_call()` | Tool `hangup` | **Tool asosiy, Matn zaxira bo'lishi kerak.** |
| **Tezkor javob (FastPath)** | Hardcoded 20 ms FastPath | Yo'q (hamma narsa LLM) | JSON regex config | **Gibrid.** 20 ms tezlik juda katta yutuq, lekin iboralarni ssenariyga ochish kerak. |

---

## 6. MUKAMMAL GIBRID (HYBRID) ARXITEKTURA LOYIHASI

Tahlil shuni ko'rsatadiki, statik qoidalarning hammasidan voz kechish katta xatodir (chunki telefoniya 0 ms kechikishni talab qiladi). Biroq ularni o'z o'rnida to'g'ri taqsimlash lozim:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        AI OVOZLI AGENT FRAZALAR ARXITEKTURASI                          │
└───────────────────────────────────────────┬────────────────────────────────────────────┘
                                            │
        ┌───────────────────────────────────┼────────────────────────────────────┐
        ▼                                   ▼                                    ▼
┌───────────────────────────┐   ┌───────────────────────────┐   ┌────────────────────────────┐
│   1-GURUH: STATIK KESH    │   │   2-GURUH: DINAMIK LLM    │   │  3-GURUH: SSENARIY / TOOL  │
│          (0 ms)           │   │         (Kontekstli)      │   │        (Moslashuvchan)     │
├───────────────────────────┤   ├───────────────────────────┤   ├────────────────────────────┤
│ • Legal Disclosure        │   │ • Jimlik 1-urinishi       │   │ • Pre-tool xabarlari       │
│ • Recording Notice        │   │   (Pipecat usulida)       │   │   (Har bir CRM/Webhook'da) │
│ • Technical Error Failover│   │ • Asosiy savol-javoblar   │   │ • FastPath iboralari       │
│ • Operator Transfer Audio │   │ • Tushunarsiz vaziyatlar  │   │   (GlobalNode darajasida)  │
│ • 3-bosqich yakuniy uzish │   │ • Faktlarni tahlil qilish │   │ • O'ylash iboralari hovuzi │
│ • Raqam/Sana parserlari   │   │                           │   │   (Agent personasiga mos)  │
└───────────────────────────┘   └───────────────────────────┘   └────────────────────────────┘
```

---

## 7. `robot-call-v2` UCHUN AMALIY HARAKATLAR REJASI

1. **`PreToolPhrases` ni dinamiklashtirish:**
   * `PreToolPhrases.java` dagi 6 ta qat'iy vositani saqlab qolgan holda, unga birinchi navbatda `ToolDefinition.progressPhrase` yoki `ToolCall.announcement` maydonini o'qish imkoniyatini berish.
   * Shunda yangi CRM vositalari (Uysot, AmoCRM) yoki Webhooklar o'zlarining shaxsiy oraliq gaplariga ega bo'ladi (*"Hozir Uysot bazasidan bo'sh xonadonlarni tekshiryapman..."*).
2. **Jimlik nazoratini (`SilenceWatchdogRunner`) Pipecat modeliga o'tkazish:**
   * 1-marta jimlik bo'lganda: `DialogEngine` ga yashirin `DEVELOPER` ko'rsatmasi beriladi (*"Foydalanuvchi jim qoldi, suhbat mavzusi bo'yicha qisqa va muloyim tarzda aloqadami yo'qligini so'ra"*).
   * 2-marta: Qat'iyroq LLM so'rovi.
   * 3-marta: Statik `stillThereLastCall` audio eshittirilib, qo'ng'iroq uziladi.
3. **O'ylash (`thinking`) iboralarini boyitish:**
   * `DialogPhrases.thinking()` dagi faqat 2 ta gap o'rniga, agent personasiga qarab 5–8 xil tabiiy variantlar kiritish va random tanlash.
4. **`Farewells` va `endCall` mutanosibligi:**
   * Agent promptida `endCall` asosiy yo'l bo'lishi kerak. `Farewells` esa faqat LLM vositani chaqirmay qo'ygan holatlarda xavfsizlik kamaridek xizmat qiladi.
