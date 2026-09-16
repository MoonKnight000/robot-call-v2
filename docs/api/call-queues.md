# Operatorlar navbati (Call Queue / ACD) API

`uz.murodjon.robotcallv2.callqueue` · huquq: **CALL_QUEUE_READ** / **CALL_QUEUE_EDIT**

Operatorlar navbati — kiruvchi qo'ng'iroq bo'sh operator topilguncha kutadigan joy:
kutish musiqasi, "Sizning navbatingiz — ...", taxminiy kutish vaqti, qayta qo'ng'iroq
taklifi va navbat to'lib ketganda nima bo'lishi (overflow). Qaysi operatorga birinchi
qo'ng'iroq qilinishini `strategy` belgilaydi.

Qo'ng'iroq navbatga uch yo'l bilan tushadi:

- call flow'dagi `QUEUE` tuguni (huquq: `CALL_FLOW_*`);
- AI agent operatorga uzatganda, agentda `transferQueueId` sozlangan bo'lsa
  ([ai-agents.md](ai-agents.md#11-agent-advanced-settings--apiai-agentsidadvanced));
- boshqa navbatning overflow'i `QUEUE` bo'lsa.

Bog'liq hujjatlar: operator SIP akkaunti, statuslar va operator paneli SSE oqimi —
[operators.md](operators.md); javobsiz qolgan qo'ng'iroqlar ro'yxati —
[missed-calls.md](missed-calls.md).

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## 🔄 Taqsimlash strategiyalari (`CallQueueStrategy`)

Faqat **tayyor** a'zolar qatnashadi: statusi `AVAILABLE`, hozir smenada (profil jadvali;
jadval bo'lmasa — doim smenada) va telefoniga yetib bo'ladi (softphone ro'yxatdan o'tgan
yoki `forwardNumber` bor). `priority` har doim birinchi o'rinda: strategiya faqat bir xil
`priority` dagi a'zolarni tartiblaydi (`SKILL_BASED` bundan mustasno, pastga qarang).

| Strategiya | Izoh |
|---|---|
| `RING_ALL` | Barcha tayyor a'zolar **birdaniga** jiringlaydi, birinchi ko'targan oladi; qolganlarining jiringlashi to'xtatiladi. Bu rejimda `priority` amalda ta'sir qilmaydi — hammasi bir vaqtda jiringlaydi |
| `ROUND_ROBIN` | Navbatma-navbat: navbat oldingi qo'ng'iroqni kimga taklif qilgan bo'lsa, a'zolar ro'yxatida undan keyingisidan boshlanadi (aylanib) |
| `LEAST_RECENT` | **Standart.** Eng uzoq vaqt bo'sh turgan a'zo birinchi (oxirgi qo'ng'irog'i eng oldin tugagan; bugun hali qo'ng'iroq olmagan — eng oldinda) |
| `FEWEST_CALLS` | Bugun eng kam qo'ng'iroq qabul qilgan a'zo birinchi; teng bo'lsa — `LEAST_RECENT` tartibi. Hisoblagich yarim tunda nolga tushadi |
| `RANDOM` | Tasodifiy tartib |
| `SKILL_BASED` | Qo'ng'iroq tili (`uz-UZ`, `ru-RU`) a'zoning `skills` ro'yxatida bo'lsa (registr farqsiz) — u birinchi. Navbatda shu tilni biladigan **kamida bitta** a'zo bo'lsa, birinchi **30 soniya** faqat o'shalarga taklif qilinadi (hozir band bo'lsalar ham, bo'shashini kutadi); 30 soniyadan keyin — hammaga. Tartib: tilni biladiganlar → `priority` → `LEAST_RECENT` |

`RING_ALL` dan boshqa strategiyalarda bir vaqtda **bitta** operator jiringlaydi; u
`ringTimeoutSec` ichida ko'tarmasa, keyingi tayyor a'zoga o'tiladi (dispetcher har
soniyada qayta tekshiradi).

---

## 🚦 Overflow amallari (`QueueOverflowAction`)

Overflow — qo'ng'iroqqa navbat xizmat qila olmagan holat: `maxWaitSec` o'tdi, navbat to'la
(`maxLength`), navbatda hech kim tizimda emas (hamma `OFFLINE`/`BREAK`) yoki navbat
o'chirilgan (`enabled=false`).

`overflowAction` qo'ng'iroq navbatga **call flow'siz** kelganda ishlaydi (AI agentning
`transferQueueId` uzatishi, boshqa navbatning overflow'i). Call flow'dagi `QUEUE` tugunida
`OVERFLOW` chiqishi ulangan bo'lsa — o'sha ustun; ulanmagan bo'lsa, navbatning o'z
`overflowAction` i ishlaydi.

| Amal | `overflowTarget` | Nima bo'ladi |
|---|---|---|
| `CALLBACK` | yo'q (saqlanmaydi) | **Standart.** Mijoz "Afsuski, hozir barcha operatorlarimiz band. Sizga qayta qo'ng'iroq qilamiz." ni eshitadi. `callbackCampaignId` bo'lsa, mijoz o'sha kampaniyaga nishon bo'lib qo'shiladi (AI qayta qo'ng'iroq qiladi); bo'lmasa — [missed-calls](missed-calls.md) ro'yxatiga `NO_OPERATOR` sababi bilan tushadi |
| `VOICEMAIL` | ovozli pochta qutisi ID si (`"7"`) | Mijoz shu qutiga xabar qoldiradi (missed call sababi — `VOICEMAIL`) |
| `QUEUE` | boshqa navbat ID si (`"3"`) | Mijoz o'sha navbatda kutadi. U ham to'lsa, uning overflow'i ishlaydi — zanjir ko'pi bilan **3** qadam, keyin xabar aytilib qo'ng'iroq tugaydi. Navbat o'ziga overflow qila olmaydi |
| `AI_AGENT` | AI agent ID si (`"9"`) | Mijoz shu AI agent bilan gaplashadi |
| `EXTERNAL_NUMBER` | telefon raqami (`"+998901234567"`) | Raqamga kompaniya trunk'i orqali 25 soniya jiringlatiladi |
| `HANGUP` | yo'q (saqlanmaydi) | O'sha xabar ("...Sizga qayta qo'ng'iroq qilamiz.") aytiladi va qo'ng'iroq tugatiladi; mijoz missed-call ro'yxatiga `NO_OPERATOR` bilan tushadi. `CALLBACK` dan farqi — kampaniyaga qo'shilmaydi |

`overflowTarget` — **string**: ID ham matn sifatida yuboriladi (`"7"`, `7` emas).
`CALLBACK`/`HANGUP` da yuborilgan qiymat e'tiborsiz qoldiriladi va `null` saqlanadi.
ID ko'rsatilgan obyekt shu kompaniyaniki bo'lishi shart (pastdagi xatolar jadvaliga qarang).

---

## ⚙️ Qanday ishlaydi

1. **Kirish.** Navbat o'chirilgan bo'lsa, navbatdagi hamma a'zo `OFFLINE` yoki `BREAK` da
   bo'lsa, yoki `maxLength > 0` va kutayotganlar soni shunga yetgan bo'lsa — qo'ng'iroq
   kutmasdan darhol overflow'ga o'tadi.
2. **VIP birinchi.** VIP qo'ng'iroq qiluvchi (kiruvchi qo'ng'iroq qoidasida `VIP` deb
   belgilangan raqam) navbatga oddiylardan **oldin**, lekin boshqa VIP'lardan keyin qo'yiladi.
   Qolganlar kelish tartibida.
3. **Sticky operator.** `stickySeconds > 0` bo'lsa, shu raqam bilan oxirgi 30 kun ichida
   gaplashgan operator topiladi va birinchi `stickySeconds` soniya davomida **faqat unga**
   taklif qilinadi. U hozir `AVAILABLE` bo'lmasa (band, tanaffusda, tizimda emas) —
   kutilmaydi, darhol navbatning oddiy tartibiga o'tiladi.
4. **E'lonlar.** Kutish boshida mijoz "Barcha operatorlarimiz hozir band. Iltimos,
   kuting — sizga tez orada javob beramiz." va o'z o'rnini eshitadi:
   `Siz navbatda birinchisiz.` yoki `Sizning navbatingiz — bir yuz yigirma to'qqizinchi.`
   (tartib raqami so'z bilan aytiladi; rus va ingliz tillarida ham). O'rin har
   `announcePositionEverySec` soniyada qayta aytiladi, lekin **faqat o'zgargan bo'lsa**.
   `announcePositionEverySec = 0` — faqat boshida bir marta.
5. **Taxminiy kutish vaqti.** `announceWaitTime = true` bo'lsa o'rindan keyin qo'shiladi:
   `Taxminiy kutish vaqti — besh daqiqa.` Hisob: *o'rin × o'rtacha xizmat vaqti ÷ faol
   operatorlar*, yuqoriga yaxlitlangan daqiqa (kamida 1). O'rtacha xizmat vaqti — shu
   navbatning oxirgi 50 ta qo'ng'irog'i (suhbat + `wrapUpSec`); faol operator — `AVAILABLE`,
   `RINGING`, `ON_CALL` yoki `WRAP_UP` dagi a'zo. Baho **ishonchli bo'lgandagina** aytiladi:
   kamida 20 ta qo'ng'iroq to'plangan va kamida 2 ta faol operator bo'lsa; aks holda faqat
   o'rin aytiladi. Statistika xotirada — ilova qayta ishga tushsa yana 20 ta qo'ng'iroq kerak.
6. **Kutish paytida.** `holdMusicFileId` dagi WAV (bo'lmasa — o'rnatilgan melodiya);
   `periodicAnnounceEverySec` da `periodicAnnounceText` o'qiladi; `callbackOfferAfterSec`
   o'tgach **bir marta** "Navbatda kutmasdan, sizga qayta qo'ng'iroq qilishimizni
   xohlasangiz, 1 ni bosing." aytiladi. `1` bosilsa mijoz navbatdan chiqadi:
   `callbackCampaignId` bo'lsa — o'sha kampaniyaga nishon bo'lib ~1 daqiqadan keyinga
   rejalashtiriladi, bo'lmasa missed-call ro'yxatiga `CALLBACK_REQUESTED` bilan tushadi.
7. **Hamroh AI (`waitingAgentId`).** Berilgan bo'lsa, kutish vaqtida mijoz bilan shu AI
   agent gaplashadi — e'lonlar, musiqa va qayta qo'ng'iroq taklifi o'rniga. Agent suhbatni
   o'zi yakunlasa (masala hal bo'ldi), mijoz navbatdan chiqadi va javobsiz hisoblanmaydi.
   Operator ko'targan zahoti agent to'xtatiladi.
8. **Taklif (offer).** Operatorning softphone'i `ringTimeoutSec` davomida jiringlaydi, panelga
   SSE `CALL_OFFER` keladi (kim, qaysi navbat, AI bilan bo'lgan transkript —
   [operators.md](operators.md#get-apioperatorsmeevents--operator-paneli-sse-oqimi)).
   Softphone oflayn, lekin `forwardNumber` bo'lsa — operatorning mobiliga trunk orqali
   qo'ng'iroq qilinadi va u 10 soniya ichida `1` ni bosib qabul qilishi kerak. Ko'targan
   operator avval qisqa xulosani eshitadi (whisper: navbat, VIP, mijoz ismi — noma'lum
   bo'lsa raqami, menyudagi tanlovi, 1 daqiqadan ko'p kutgan bo'lsa necha daqiqa), keyin
   mijoz bilan ulanadi. Suhbat yoziladi.
9. **RONA.** Taklif javobsiz jiringlab tugasa — operatorga "miss" yoziladi. **Ketma-ket
   ikkinchi** missdan keyin operator avtomatik `BREAK` ga o'tkaziladi (`reason:
   "javob bermadi"`) va unga boshqa qo'ng'iroq berilmaydi, toki o'zi `AVAILABLE` ni
   tanlamaguncha. Ko'tarilgan qo'ng'iroq hisoblagichni nolga tushiradi. Operatorning
   aybi bo'lmagan to'xtatishlar (`RING_ALL` da boshqasi ko'tardi, mijoz go'shakni qo'ydi)
   miss hisoblanmaydi.
10. **Wrap-up.** Suhbat tugagach operator `wrapUpSec` soniya `WRAP_UP` da turadi, keyin
    avtomatik `AVAILABLE` bo'ladi. Operator vaqtidan oldin o'zi `AVAILABLE` ni tanlashi mumkin.
11. **Suhbatdan keyin.** Operator go'shakni qo'ygach, mijoz liniyada qolgan bo'lsa va
    `postCallSurvey = true` — mijozdan 1–5 baho so'raladi (8 soniya). 1 yoki 2 baho —
    `LOW_SURVEY_SCORE` bildirishnomasi ([notifications.md](notifications.md)).
12. **Tugash.** `maxWaitSec` o'tsa — overflow (missed call sababi `NO_OPERATOR`). Mijoz
    kutayotganda go'shakni qo'ysa — `ABANDONED`. Operator mijozni ko'targanda shu raqamning
    ochiq missed-call yozuvi o'zi `RESOLVED` bo'ladi.

Navbatdagi jonli holat (kim kutyapti, taklif, bog'langan operator) **ilova xotirasida**
saqlanadi: bitta voice-agent instansiyasi bitta Asterisk'ga xizmat qiladi.

---

## `POST /api/call-queues` — yangi navbat

Huquq: **CALL_QUEUE_EDIT**

**Request body** (`SaveCallQueueRequest`) — `name` dan boshqa hamma maydon ixtiyoriy;
`null` yuborilgan maydon jadvaldagi standart qiymatni oladi:

```json
{
  "name": "Sotuv bo'limi",
  "strategy": "SKILL_BASED",
  "ringTimeoutSec": 15,
  "wrapUpSec": 10,
  "maxWaitSec": 300,
  "maxLength": 0,
  "announcePositionEverySec": 60,
  "announceWaitTime": true,
  "periodicAnnounceText": "Qo'ng'irog'ingiz biz uchun muhim. Iltimos, liniyada qoling.",
  "periodicAnnounceEverySec": 90,
  "holdMusicFileId": null,
  "callbackOfferAfterSec": 120,
  "callbackCampaignId": null,
  "overflowAction": "VOICEMAIL",
  "overflowTarget": "7",
  "serviceLevelSec": 20,
  "stickySeconds": 15,
  "postCallSurvey": true,
  "waitingAgentId": null,
  "autoCallback": true,
  "enabled": true,
  "members": [
    { "userId": 12, "priority": 0, "skills": ["uz-UZ", "ru-RU"] },
    { "userId": 15, "priority": 0, "skills": ["uz-UZ"] },
    { "userId": 21, "priority": 10, "skills": [] }
  ]
}
```

Barcha taymerlar **soniyada**. "0 yoki X–Y" deganda `0` shu funksiyani o'chiradi.

| Maydon | Turi | Standart | Chegara | Izoh |
|---|---|---|---|---|
| `name` | string | — | ✅ `@NotBlank` | Navbat nomi; operator whisper'ida va qo'ng'iroq caller ID'sida ko'rinadi |
| `strategy` | enum | `LEAST_RECENT` | yuqoridagi jadval | Kimga birinchi taklif qilinadi |
| `ringTimeoutSec` | integer | `15` | 5–120 | Bitta operator qancha jiringlaydi, keyin keyingisiga o'tiladi |
| `wrapUpSec` | integer | `10` | 0–600 | Suhbatdan keyingi dam, shundan keyin operator yana `AVAILABLE` |
| `maxWaitSec` | integer | `300` | 10–7200 | Eng uzoq kutish; o'tsa — overflow |
| `maxLength` | integer | `0` | 0–1000 | Bir vaqtda kutishi mumkin bo'lganlar; `0` — cheksiz. To'la bo'lsa yangi qo'ng'iroq darhol overflow |
| `announcePositionEverySec` | integer | `60` | 0 yoki 20–900 | O'rin qanchalik tez-tez qayta aytiladi (faqat o'zgargan bo'lsa) |
| `announceWaitTime` | boolean | `true` | — | O'ringa taxminiy kutish vaqtini qo'shish (ishonchli bo'lsa) |
| `periodicAnnounceText` | string | `null` | — | Kutish paytida davriy o'qiladigan matn (TTS). `periodicAnnounceEverySec > 0` bo'lsa **majburiy** |
| `periodicAnnounceEverySec` | integer | `0` | 0 yoki 30–1800 | Davriy e'lon oralig'i |
| `holdMusicFileId` | long | `null` | — | Kutish musiqasi — [files](files.md) orqali yuklangan WAV ID si; `null` — o'rnatilgan melodiya. Fayl shu kompaniyaniki bo'lishi shart |
| `callbackOfferAfterSec` | integer | `0` | 0 yoki 10–3600 | Shuncha kutgandan keyin "1 ni bosing — qayta qo'ng'iroq qilamiz" taklifi |
| `callbackCampaignId` | long | `null` | — | Qayta qo'ng'iroq qabul qilinsa (yoki `CALLBACK` overflow) mijoz qo'shiladigan [kampaniya](campaigns.md); `null` — mijoz missed-call ro'yxatiga tushadi |
| `overflowAction` | enum | `CALLBACK` | yuqoridagi jadval | Navbat xizmat qila olmaganda |
| `overflowTarget` | string | `null` | amalga qarab | `QUEUE`/`AI_AGENT`/`VOICEMAIL` — ID; `EXTERNAL_NUMBER` — `^\+?[1-9]\d{8,14}$` formatdagi raqam |
| `serviceLevelSec` | integer | `20` | 5–600 | Shu vaqt ichida javob berilgan qo'ng'iroq SLA ga kiradi (hisobotlar uchun) |
| `stickySeconds` | integer | `0` | 0–120 | Mijoz bilan oxirgi gaplashgan operatorni shuncha vaqt yolg'iz jiringlatish |
| `postCallSurvey` | boolean | `false` | — | Operator suhbatidan keyin 1–5 baho so'rash |
| `waitingAgentId` | long | `null` | — | Kutish vaqtida mijoz bilan gaplashadigan [AI agent](ai-agents.md) |
| `autoCallback` | boolean | `false` | — | Shu navbatning javobsiz qo'ng'iroqlariga tizim o'zi qayta qo'ng'iroq qiladi ([missed-calls.md](missed-calls.md#-avtomatik-qayta-qongiroq)) |
| `enabled` | boolean | `true` | — | O'chirilgan navbatga tushgan qo'ng'iroq darhol overflow; wallboard'da ko'rinmaydi |
| `members` | array | `[]` | — | Navbat a'zolari (pastda). Ro'yxat **to'liq almashtiriladi** |

**`members[]`** (`CallQueueMember`):

| Maydon | Turi | Standart | Chegara | Izoh |
|---|---|---|---|---|
| `userId` | long | — | ro'yxatda bir marta | Kompaniya foydalanuvchisi. Qo'ng'iroq olishi uchun unda [SIP akkaunt](operators.md) bo'lishi kerak |
| `priority` | integer | `0` | 0–100 | **Kichigi birinchi** jiringlaydi; tenglar orasida tartibni strategiya belgilaydi |
| `skills` | string[] | `[]` | har biri `[A-Za-z0-9_-]{1,32}` | Teglar; `SKILL_BASED` uchun qo'ng'iroq tillari — `uz-UZ`, `ru-RU` (registr farqsiz solishtiriladi) |

**Javob** — saqlangan `CallQueue` (`ResponseData` ichida):

```json
{
  "data": {
    "id": 4,
    "companyId": 1,
    "name": "Sotuv bo'limi",
    "strategy": "SKILL_BASED",
    "ringTimeoutSec": 15,
    "wrapUpSec": 10,
    "maxWaitSec": 300,
    "maxLength": 0,
    "announcePositionEverySec": 60,
    "announceWaitTime": true,
    "periodicAnnounceText": "Qo'ng'irog'ingiz biz uchun muhim. Iltimos, liniyada qoling.",
    "periodicAnnounceEverySec": 90,
    "holdMusicFileId": null,
    "callbackOfferAfterSec": 120,
    "callbackCampaignId": null,
    "overflowAction": "VOICEMAIL",
    "overflowTarget": "7",
    "serviceLevelSec": 20,
    "stickySeconds": 15,
    "postCallSurvey": true,
    "waitingAgentId": null,
    "autoCallback": true,
    "enabled": true,
    "members": [
      { "userId": 12, "priority": 0, "skills": ["uz-UZ", "ru-RU"] },
      { "userId": 15, "priority": 0, "skills": ["uz-UZ"] },
      { "userId": 21, "priority": 10, "skills": [] }
    ],
    "createdAt": "2026-09-12T08:00:00Z"
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

**Xatolar:**

| Holat | Javob |
|---|---|
| `name` bo'sh | `400 VALIDATION_FAILED` |
| Taymer chegaradan tashqarida, `periodicAnnounceText` yo'q, `overflowTarget` yo'q/noto'g'ri, a'zo ikki marta, `priority`/`skills` noto'g'ri | `400 CALL_QUEUE_INVALID` — `message` da qaysi maydon ekani inglizcha yoziladi (`"Invalid call queue: ringTimeoutSec must be between 5 and 120"`) |
| Navbat o'ziga overflow qiladi (`overflowAction: QUEUE`, `overflowTarget` — shu navbatning o'zi) | `400 CALL_QUEUE_OVERFLOW_TO_SELF` |
| `members[].userId` topilmadi / boshqa kompaniyaniki | `404 USER_NOT_FOUND` |
| `waitingAgentId` yoki `AI_AGENT` overflow agenti topilmadi | `404 AI_AGENT_NOT_FOUND` |
| `QUEUE` overflow navbati topilmadi | `404 CALL_QUEUE_NOT_FOUND` |
| `VOICEMAIL` overflow qutisi topilmadi | `404 VOICEMAIL_BOX_NOT_FOUND` |
| `callbackCampaignId` topilmadi | `404 CAMPAIGN_NOT_FOUND` |
| `holdMusicFileId` topilmadi | `404 FILE_NOT_FOUND` |

Audit jurnaliga `CALL_QUEUE_CREATE` yoziladi.

---

## `POST /api/call-queues/list` — ro'yxat

Huquq: **CALL_QUEUE_READ**

Body — `CallQueueFilter`:

```json
{ "page": 0, "size": 20, "orders": { "NAME": "ASC" } }
```

Saralanadigan ustunlar: `ID`, `NAME`, `CREATED_AT`. Standart: `NAME ASC`. Qidiruv yoki
boshqa filtr yo'q; o'chirilgan (`enabled=false`) navbatlar ham qaytadi.

Javob — `PageableData<CallQueue>` (har bir qator `members` bilan birga).

---

## `GET /api/call-queues/{id}` — bitta navbat

Huquq: **CALL_QUEUE_READ**

Javob — `CallQueue`. Topilmasa yoki boshqa kompaniyaniki bo'lsa — `404 CALL_QUEUE_NOT_FOUND`.

---

## `PUT /api/call-queues/{id}` — tahrirlash

Huquq: **CALL_QUEUE_EDIT**

Body — `POST` bilan bir xil `SaveCallQueueRequest`. **To'liq almashtirish, delta emas:**
yuborilmagan (`null`) maydon eski qiymatini saqlamaydi — standart qiymatga qaytadi; `members`
ham to'liq almashtiriladi. Shuning uchun forma navbatning hamma maydonini yuboradi.

Xatolar — `POST` dagidek, plus `404 CALL_QUEUE_NOT_FOUND`. Audit: `CALL_QUEUE_UPDATE`.

O'zgarish keyingi navbatga kirgan qo'ng'iroqdan kuchga kiradi; hozir kutayotgan qo'ng'iroq
eski sozlama bilan davom etadi.

---

## `DELETE /api/call-queues/{id}` — o'chirish

Huquq: **CALL_QUEUE_EDIT**

Navbat **butunlay o'chiriladi** (arxivlanmaydi), a'zolari ham. Unga ishora qilgan
AI agentlarning `transferQueueId`, missed-call va qo'ng'iroq yozuvlaridagi `queueId`
`null` ga tushadi. Boshqa navbatning `overflowTarget` i matn bo'lgani uchun tozalanmaydi —
bunday navbat overflow'da xabar aytib qo'ng'iroqni tugatadi. Vaqtincha to'xtatish uchun
`PUT` bilan `enabled: false` qiling.

Javob: `data: null`, `accept: true`. Topilmasa — `404 CALL_QUEUE_NOT_FOUND`. Audit:
`CALL_QUEUE_DELETE`.

---

## `GET /api/call-queues/live` — wallboard (bir martalik)

Huquq: **CALL_QUEUE_READ**

Kompaniyaning har bir **yoqilgan** navbati hozirgi holati. Ma'lumotlar bazadan emas,
dispetcher va operator presence xotirasidan o'qiladi.

**Javob** (`List<CallQueueLiveRow>`):

```json
{
  "data": [
    {
      "queueId": 4,
      "name": "Sotuv bo'limi",
      "waiting": 2,
      "longestWaitSeconds": 74,
      "connected": 3,
      "available": 1,
      "ringing": 1,
      "onCall": 3,
      "wrapUp": 0,
      "onBreak": 1,
      "offline": 2,
      "calls": [
        {
          "queueId": 4,
          "channelId": "1757664000.118",
          "callerNumber": "998901234567",
          "position": 1,
          "waitSeconds": 74,
          "state": "RINGING",
          "vip": true,
          "operatorUserId": null
        },
        {
          "queueId": 4,
          "channelId": "1757664031.121",
          "callerNumber": "998935556677",
          "position": 0,
          "waitSeconds": 212,
          "state": "CONNECTED",
          "vip": false,
          "operatorUserId": 12
        }
      ]
    }
  ],
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

| Maydon | Izoh |
|---|---|
| `waiting` | Operator kutayotganlar (`WAITING` + `RINGING`) |
| `longestWaitSeconds` | Shulardan eng uzoq kutayotganiniki — supervisor shu raqamga qarab harakat qiladi |
| `connected` | Hozir operator bilan (`CONNECTING` + `CONNECTED`) |
| `available` … `offline` | Navbat **a'zolari** statuslar bo'yicha (`onBreak` = `BREAK`). SIP akkaunti yoki presence'i yo'q a'zo `offline` ga kiradi |
| `calls[]` | Navbatdagi har bir qo'ng'iroq (`WaitingCallView`), xizmat tartibida |
| `calls[].position` | Kutayotganlar orasidagi 1 dan boshlangan o'rin; operator bilan bo'lganlarda `0` |
| `calls[].operatorUserId` | Bog'langan operator; hali bog'lanmagan bo'lsa `null` |

**`calls[].state`** (`WaitingCallState`):

| Qiymat | Ma'nosi |
|---|---|
| `WAITING` | Navbatda, hech kim jiringlamayapti |
| `RINGING` | Bir yoki bir nechta operator jiringlayapti |
| `CONNECTING` | Operator ko'tardi, whisper eshityapti, ulanyapti |
| `CONNECTED` | Mijoz va operator gaplashyapti |
| `OPERATOR_LEFT` | Operator go'shakni qo'ydi (yoki uzatildi), mijoz hali liniyada (so'rovnoma va h.k.) |
| `CANCELLED` | Navbatdan chiqdi (odatda ro'yxatda qolmaydi) |

---

## `GET /api/call-queues/live/stream` — wallboard SSE oqimi

Huquq: **CALL_QUEUE_READ**

`Content-Type: text/event-stream`, **`ResponseData` ga o'ralmagan**. Autentifikatsiya
[live.md](live.md#muhim-browserning-tayyor-eventsourcei-ishlamaydi)dagidek: `Authorization:
Bearer` sarlavhasi (`fetch`-asosidagi SSE klient) yoki bir martalik `?ticket=`
(`new EventSource('/api/call-queues/live/stream?ticket=' + ticket)`, ticket —
[`POST /api/auth/stream-ticket`](auth.md#post-apiauthstream-ticket--oqim-uchun-bir-martalik-ticket),
60 soniya, har ulanishga yangisi); eskicha `?token=` / `?access_token=` — deprecated.
Ulanishning vaqt chegarasi yo'q; har ~15 soniyada `: ping` izohi (heartbeat) keladi.

| `event:` | Payload | Qachon |
|---|---|---|
| `connected` | raqam — obuna ID si | Ulanish ochilganda bir marta |
| `QUEUE_STATS` | `CallQueueLiveRow[]` — `GET /live` bilan bir xil | Har 2 soniyada (`voice-agent.live.kpi-poll-ms`, standart `2000`) |
| `MISSED_CALL` | `MissedCall` ([missed-calls.md](missed-calls.md)) | Javobsiz qo'ng'iroq yozildi (yangi yoki takroriy) — butun kompaniyaga |
| `VOICEMAIL` | `VoicemailMessage` | Mijoz ovozli xabar qoldirdi — butun kompaniyaga |

`MISSED_CALL` va `VOICEMAIL` payload'lari [operators.md](operators.md#get-apioperatorsmeevents--operator-paneli-sse-oqimi)da.
