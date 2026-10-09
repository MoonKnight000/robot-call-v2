# Jonli monitoring API

`uz.murodjon.robotcallv2.live` (SSE oqimi) + `uz.murodjon.robotcallv2.callrecord`
(`/live` ro'yxati) · huquq: **LIVE_READ** (ikkala endpoint ham).

Ikki alohida narsa: (1) hozirgi holatni bir marta olish uchun oddiy `GET`, va
(2) real-vaqt push kanali (SSE). Ikkalasi ham bir xil ma'lumotni turli
shakllarda beradi — panel odatda dastlabki holatni (1) bilan yuklaydi, keyin
(2) orqali yangilanib turadi.

Umumiy javob shakli, xatolar uchun [README.md](README.md)ga qarang — **SSE
oqimi bundan mustasno**, pastga qarang.

---

## `GET /api/calls/live` — jonli qo'ng'iroqlar ro'yxati (bir martalik) {#get-apicallslive}

Hozir suhbatda bo'lgan har bir qo'ng'iroq — **faqat token kompaniyasiniki**. Boshqa
kompaniyaning qo'ng'iroqlari ro'yxatga umuman tushmaydi (`AriService.findLiveCalls(companyId)`).
Bir nechta instance bo'lsa — hammasining qo'ng'iroqlari (boshqa instance'larniki ~2 soniya
kechikish bilan), `startedAt` bo'yicha tartiblangan.

**Response** — `List<LiveCallRow>`, `ResponseData` ichida:

```json
{
  "data": [
    {
      "channelId": "PJSIP/trunk-00000012",
      "phone": "998901234567",
      "clientName": "Aziz Karimov",
      "campaignId": 42,
      "campaignName": "Iyul qarzdorlik",
      "language": "uz-UZ",
      "startedAt": "2026-07-31T10:15:00Z",
      "dialogState": "DEBT_NOTICE"
    }
  ],
  "message": null, "messageCode": null, "accept": true, "errors": null
}
```

| Maydon | Izoh |
|---|---|
| `phone` | набранный raqam; `null` — qo'lda/test qo'ng'iroq bo'lsa (kampaniyasiz) |
| `clientName` | qarzdor ismi (qo'ng'iroq faktlaridan); bo'lmasa `null` |
| `campaignId`/`campaignName` | egasi kampaniya; test qo'ng'iroq bo'lsa `null` |
| `startedAt` | qachon boshlangani — **elapsed davomiylik emas**, chunki panel taymerini har soniyada client-side hisoblaydi (backend javobi kelguncha davomiylik allaqachon eskirgan bo'lardi) |
| `dialogState` | FSM holati (masalan `DEBT_NOTICE`) |

---

## `GET /api/live/stream` — SSE push kanali

`Content-Type: text/event-stream`, ulanish ochiq qoladi va server voqea
yuboraveradi. **`ResponseData<T>` konvertiga o'ralmagan** — `EventSource`
xom event-stream tanasini kutadi, uni yana bitta JSON qatlamiga o'rash oqimni
o'zini parse qilib bo'lmaydigan holga keltiradi.

Oqim tokendagi kompaniyaga bog'lanadi: `KPI`, `LIVE_CALLS`, `TRANSCRIPT`, `AUDIO_LEVEL`
va `NOTIFICATION` — hammasi faqat shu kompaniyaning qo'ng'iroqlari bo'yicha. `KPI.activeCalls`
ham butun server emas, shu kompaniya liniyasidagi qo'ng'iroqlar soni.

Oqim qaysi instance'ga ulangan bo'lmasin, kompaniyaning **barcha instance'lardagi** qo'ng'iroqlari
keladi: `TRANSCRIPT`, `AUDIO_LEVEL`, `NOTIFICATION` boshqa instance'lardan Redis pub/sub orqali,
`LIVE_CALLS`/`KPI` esa klaster bo'yicha birlashtirilgan ro'yxatdan.

### Muhim: browser'ning tayyor `EventSource`i ishlamaydi

`EventSource` maxsus sarlavha (`Authorization`) qo'ya olmaydi, bu esa autentifikatsiya
talab qiladigan endpoint uchun muammo. Frontendda **`fetch`-asosidagi SSE
klient** kerak, masalan `@microsoft/fetch-event-source`:

```ts
import { fetchEventSource } from '@microsoft/fetch-event-source';

fetchEventSource('/api/live/stream', {
  headers: { 'Authorization': 'Bearer ' + accessToken },
  onmessage(msg) {
    switch (msg.event) {
      case 'KPI': /* LiveKpiSnapshot */ break;
      case 'LIVE_CALLS': /* LiveCallRow[] */ break;
      case 'TRANSCRIPT': /* LiveTranscriptEvent */ break;
      case 'AUDIO_LEVEL': /* AudioLevelEvent */ break;
      case 'NOTIFICATION': /* LiveNotification */ break;
    }
  },
});
```

Panel foydalanuvchisi uchun brauzerning oddiy `EventSource`i ham ishlaydi — URL'ga bir martalik
ticket qo'yiladi: avval
[`POST /api/auth/stream-ticket`](auth.md#post-apiauthstream-ticket--oqim-uchun-bir-martalik-ticket),
keyin `new EventSource('/api/live/stream?ticket=' + ticket)`. Ticket 60 soniya yashaydi va bir
marta ishlatiladi, shuning uchun har bir qayta ulanishdan oldin yangisi olinadi. Eskicha
`?token=<accessToken>` / `?access_token=` hali qabul qilinadi, lekin **deprecated** (to'liq
token URL orqali access/proxy loglariga tushadi). Har qanday usulda **LIVE_READ** huquqi talab
qilinadi — tokensiz yoki yaroqsiz/ishlatilgan ticket bilan ulanish `401`, huquqsiz — `403` oladi.

"Ulanish uzildi" holati serverdan alohida push qilinmaydi — standart
`onerror`/qayta-ulanish + ~15s heartbeat orqali klient o'zi aniqlaydi.

### Voqea turlari (`event:` nomi) va payload'lari

| `event:` | Payload turi | Qachon yuboriladi |
|---|---|---|
| `KPI` | `LiveKpiSnapshot` | ulanish ochilganda bir marta, keyin qiymat o'zgarganda — hozir liniyadagi qo'ng'iroqlar soni |
| `LIVE_CALLS` | `LiveCallRow[]` | ulanish ochilganda bir marta, keyin ro'yxat o'zgarganda (yuqoridagi `GET /api/calls/live` bilan bir xil shakl) |
| `TRANSCRIPT` | `LiveTranscriptEvent` | har bir gap tanilgan/aytilgan zahoti (CLIENT yoki AGENT) |
| `AUDIO_LEVEL` | `AudioLevelEvent` | ~5/sek, faqat `voice-agent.live.audio-level-enabled=true` bo'lsa (standart holatda o'chiq) |
| `NOTIFICATION` | `LiveNotification` | bir martalik hodisa (kampaniya boshlandi/pauza qilindi va h.k.), topbar bell uchun |

**`LiveKpiSnapshot`:**

```json
{ "activeCalls": 7 }
```

Faqat "hozir nechta qo'ng'iroq bor" — davriy dashboard KPI'sining (jami,
o'zgarish %, sparkline) o'zi allaqachon [reports.md](reports.md)da bor, uni
har bir sekundda qayta yuborish keraksiz DB so'rovi bo'lardi.

**`LiveTranscriptEvent`:**

```json
{ "channelId": "PJSIP/trunk-00000012", "role": "CLIENT", "text": "Ha, tushunarli", "dialogState": "DEBT_NOTICE" }
```

`role` — `"CLIENT"` yoki `"AGENT"`.

**`AudioLevelEvent`:**

```json
{ "channelId": "PJSIP/trunk-00000012", "level": 0.42 }
```

`level` — `[0, 1]` oralig'ida normallashtirilgan RMS daraja (to'lqin
animatsiyasi uchun).

**`LiveNotification`:**

```json
{ "level": "warning", "title": "Kampaniya pauza qilindi", "message": "Iyul qarzdorlik kunlik limitga yetdi" }
```

`level` — `"info"` / `"warning"` / `"danger"`.

---

## 2. Jonli Suhbatni Boshqarish (Whisper, Takeover, Scenario)

Operator jonli qo'ng'iroqni eshitib turgan paytda botga ko'rinmas ko'rsatma yuborishi (`whisper`),
qo'ng'iroqni o'zining ichki SIP raqamiga o'tkazib olishi (`takeover`) yoki qo'ng'iroqni boshqa
ssenariyga o'tkazishi (`scenario`) mumkin.

Uchalasi ham faqat token kompaniyasining jonli qo'ng'irog'i uchun ishlaydi: kanal yo'q,
allaqachon tugagan yoki **boshqa kompaniyaniki** bo'lsa — `409 CALL_NOT_ACTIVE` (uchala holat
bir xil javob oladi, kanal mavjudligi oshkor qilinmaydi).

Bir nechta instance ishlayotganda qo'ng'iroq boshqa instance'da bo'lsa (Redis'dagi
`call:route:{channelId}` yozuvi, faqat o'z kompaniyangizniki uchun), server so'rovni o'zi o'sha
instance'ga uzatadi va uning javobini qaytaradi — frontend hech narsa qilmaydi (`?ticket=` bilan
kelgan so'rovga yangi ticket qo'yiladi). O'sha instance'ga yetib bo'lmasa (yoki u
`voice-agent.routing.internal-url` e'lon qilmagan bo'lsa) — `409 CALL_ON_OTHER_INSTANCE`, xabarda
o'sha instance nomi bor.

### `POST /api/calls/live/{channelId}/whisper` — Botga yashirin yo'l-yo'riq berish (Whisper Injection)

Operator kiritgan matn mijozga eshitilmaydi. Bot keyingi javobni shakllantirishda ushbu ko'rsatmani inobatga oladi (masalan, qo'shimcha chegirma taklif qilish, mijoz nomini aniqlashtirish va h.k.).

**Request Body:**
```json
{
  "instruction": "Mijozga muddatli to'lov uchun 10% chegirma taklif qil"
}
```

`instruction` majburiy, bo'sh bo'lmasligi va 1000 belgidan oshmasligi kerak — aks holda `400 VALIDATION_FAILED`.

`CASCADE` qo'ng'iroqda ko'rsatma keyingi turn'dan boshlab botning har turn'dagi promptiga
qo'shiladi va yangi whisper kelguncha amal qiladi (yangisi eskisining o'rnini egallaydi).

`REALTIME` qo'ng'iroqda ko'rsatma engine suhbatiga **kontekst** sifatida qo'shiladi — unga
alohida javob so'ralmaydi, bot uni keyingi javobida inobatga oladi va suhbat oxirigacha
eslab qoladi. Bot ayni paytda gapirayotgan bo'lsa, ko'rsatma u gapini tugatguncha kutib
turadi: yangi kontent gapirayotgan engine'ni gap o'rtasida to'xtatadi. Gemini Live va
OpenAI Realtime shunday ishlaydi; kontekstni jim qo'sha olmaydigan engine'lar (Moshi, Qwen
Omni, Pipecat) ko'rsatmani oddiy xabar sifatida oladi va darhol javob beradi.

Allaqachon operatorga uzatilgan (yoki tugagan) qo'ng'iroqda bot yo'q — `404 CALL_NOT_FOUND`.

**Response:**
```json
{
  "accept": true,
  "data": {
    "channelId": "1710000000.12",
    "status": "WHISPER_INJECTED"
  },
  "messageCode": null,
  "errors": null
}
```

### `POST /api/calls/live/{channelId}/takeover` — Qo'ng'iroqni tirik operatorga olish (Human Takeover)

Qo'ng'iroq operatorga uzatiladi. `extension` berilsa — **shu kompaniyaning** o'sha ichki
raqamli ([users.md](users.md) `sipExtension`) va SIP akkaunti bor operatorining telefoniga
(`PJSIP/op-<userId>`), agentning navbati va raqamidan ustun; berilmasa — agentning
`transferQueueId` navbatiga, u bo'lmasa `transferPhoneNumber` ga, u ham bo'lmasa o'rnatmaning
operator endpoint'iga. `CASCADE` va `REALTIME` qo'ng'iroqlarda bir xil ishlaydi.

| Holat | Javob |
|---|---|
| `extension` 2–8 raqam emas | `400 VALIDATION_FAILED` |
| Kompaniyada bunday ichki raqamli foydalanuvchi yo'q (boshqa kompaniyaniki ham), yoki uning SIP akkaunti yo'q | `404 OPERATOR_EXTENSION_NOT_FOUND` |

> Ilgari `extension` dial string'ga tekshiruvsiz qo'yilardi: istalgan raqam Asterisk'dagi
> o'sha nomli istalgan endpoint'ga (boshqa tenant telefoniga ham) yetardi, kompaniya
> operatorlari (`op-<userId>`) esa ichki raqami orqali umuman chaqirilmasdi.

**Request Body** (ixtiyoriy):
```json
{
  "extension": "105"
}
```

**Response:**
```json
{
  "accept": true,
  "data": {
    "channelId": "1710000000.12",
    "status": "TAKEOVER_TRIGGERED"
  },
  "messageCode": null,
  "errors": null
}
```

### `POST /api/calls/live/{channelId}/scenario` — Qo'ng'iroqni boshqa ssenariyga o'tkazish (Handoff)

Qo'ng'iroq to'xtamasdan boshqa ssenariy bilan davom etadi (masalan, lidni saralashdan buyurtmani
tasdiqlashga). Suhbat tarixi, yig'ilgan faktlar va mijoz konteksti saqlanadi; qo'ng'iroq yangi
ssenariyning **birinchi bosqichidan** davom etadi va keyingi turn'dayoq uning roli va qoidalari
bilan gapiradi. Rol: `OPERATOR_EDIT`.

**Request Body:**
```json
{
  "scenarioId": 12
}
```

**Response:**
```json
{
  "accept": true,
  "data": {
    "channelId": "1710000000.12",
    "status": "SCENARIO_SWITCHED"
  },
  "messageCode": null,
  "errors": null
}
```

| Holat | Javob |
|---|---|
| `scenarioId` yo'q yoki musbat emas | `400 VALIDATION_FAILED` |
| Ssenariy topilmadi yoki boshqa kompaniyaniki | `404 SCENARIO_NOT_FOUND` |
| Ssenariy faol emas | `409 SCENARIO_NOT_ACTIVE` |
| Ssenariyda bosqich yo'q | `400 SCENARIO_HAS_NO_STAGES` (ilgari `SCENARIO_DEFINITION_INVALID` edi; xabar argumenti — ssenariy id'si) |
| Qo'ng'iroq realtime engine'da yoki allaqachon operatorda (ssenariyli bot dialogi yo'q) | `409 CALL_SCENARIO_SWITCH_UNAVAILABLE` |
| Kanal jonli qo'ng'iroq emas / boshqa instance'da | `409 CALL_NOT_ACTIVE` / `409 CALL_ON_OTHER_INSTANCE` |

> Realtime qo'ng'iroqda ssenariy almashtirilmaydi (ataylab): engine'ning ko'rsatmalari va
> tool'lari sessiya ochilganda bir marta beriladi — Gemini Live ularni sessiya o'rtasida
> o'zgartirishga imkon bermaydi, yangi sessiya ochish esa ovozni va suhbat kontekstini
> uzadi. Realtime qo'ng'iroqni yo'naltirish kerak bo'lsa — whisper ishlating.
