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

Hozir suhbatda bo'lgan har bir qo'ng'iroq. `DialogEngine.liveDialogs()`
(xotiradagi sessiyalar) `AriService.liveCalls()`da birlashtiriladi.

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

### Muhim: browser'ning tayyor `EventSource`i ishlamaydi

`EventSource` maxsus sarlavha (`X-Api-Key`) qo'ya olmaydi, bu esa autentifikatsiya
talab qiladigan endpoint uchun muammo. Frontendda **`fetch`-asosidagi SSE
klient** kerak, masalan `@microsoft/fetch-event-source`:

```ts
import { fetchEventSource } from '@microsoft/fetch-event-source';

fetchEventSource('/api/live/stream', {
  headers: { 'X-Api-Key': apiKey },
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

Panel foydalanuvchisi uchun `EventSource` ham ishlaydi, chunki `/api/live/stream` tokenni
query parametr sifatida ham qabul qiladi: `new EventSource('/api/live/stream?token=' +
accessToken)`. Ikkala usulda ham **LIVE_READ** huquqi talab qilinadi — tokensiz ulanish
`401`, huquqsiz token `403` oladi.

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

## 2. Jonli Suhbatni Boshqarish (Whisper & Takeover)

Operator jonli qo'ng'iroqni eshitib turgan paytda botga ko'rinmas ko'rsatma yuborishi (`whisper`) yoki qo'ng'iroqni o'zining ichki SIP raqamiga o'tkazib olishi (`takeover`) mumkin.

### `POST /api/calls/live/{channelId}/whisper` — Botga yashirin yo'l-yo'riq berish (Whisper Injection)

Operator kiritgan matn mijozga eshitilmaydi. Bot keyingi javobni shakllantirishda ushbu ko'rsatmani inobatga oladi (masalan, qo'shimcha chegirma taklif qilish, mijoz nomini aniqlashtirish va h.k.).

**Request Body:**
```json
{
  "instruction": "Mijozga muddatli to'lov uchun 10% chegirma taklif qil"
}
```

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

AI bot darhol ovozni to'xtatadi va qo'ng'iroq ko'rsatilgan operator ichki raqamiga (SIP extension) yoki navbatga uzatiladi.

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
