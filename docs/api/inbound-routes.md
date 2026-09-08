# Kiruvchi qo'ng'iroq va Virtual PBX (OnlinePBX) marshrutlash API

`uz.murodjon.robotcallv2.inbound` · huquq: **INBOUND_ROUTE_READ** / **INBOUND_ROUTE_EDIT**

Virtual PBX / OnlinePBX darajasidagi to'liq kiruvchi qo'ng'iroqlarni boshqarish:
DID raqamiga tushgan qo'ng'iroqni AI Ovozli agentga, operatorlar navbatiga, ichki SIP raqamiga (extension), tashqi mobil raqamga yoki CRM dagi mas'ul shaxsiy menejerga yo'naltirish.

Do-not-call ro'yxati kiruvchi qo'ng'iroqlarga **qo'llanilmaydi** — bir marta
"qo'ng'iroq qilmang" degan mijoz, o'zi qo'ng'iroq qilsa, baribir javob oladi
(ROADMAP C.2).

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## 🎯 Marshrutlash turlari (`InboundRouteType`)

| Turi | Izoh |
|---|---|
| `SCENARIO` | AI Ovozli Agent — belgilangan `aiAgentId` bo'yicha javob beradi (agent o'z senariysi, ovozi va personasi bilan) |
| `OPERATOR_QUEUE` | Operatorlar guruhi / navbatiga yo'naltirish |
| `EXTENSION` | Aniq ichki SIP raqamiga yo'naltirish (masalan: `101`, `102`) |
| `EXTERNAL_NUMBER` | Tashqi mobil yoki shahar raqamiga yo'naltirish (masalan: `+998901234567`) |
| `STICKY_AGENT` | CRM dagi mijozga biriktirilgan shaxsiy mas'ul menejeriga to'g'ridan-to'g'ri ulash |
| `IVR_MENU` | Ovozli/DTMF menyu (1 - Sotuv, 2 - AI Bot va h.k.) |
| `VOICEMAIL` | To'g'ridan-to'g'ri ovozli pochta xabari yozib olish |

---

## 🔄 Navbat strategiyalari (`QueueStrategy`)

| Strategiya | Izoh |
|---|---|
| `RING_ALL` | Barcha bo'sh operatorlarga birdaniga jiringlaydi |
| `ROUND_ROBIN` | Operatorlar bo'ylab navbatma-navbat taqsimlaydi |
| `FEWEST_CALLS` | Bugun eng kam qo'ng'iroq qabul qilgan operatorga yo'naltiradi |
| `LEAST_RECENT` | Eng uzoq vaqt kutib turgan bo'sh operatorga yo'naltiradi |
| `RANDOM` | Tasodifiy bo'sh operatorga |

---

## ⏰ Ish vaqtidan tashqari amallar (`InboundAfterHoursAction`)

| Amal | Izoh |
|---|---|
| `PLAY_MESSAGE_AND_HANGUP` | `fallbackMessage` matnini o'qib berib, qo'ng'iroqni tugatadi |
| `AI_AGENT` | Tungi AI Ovozli yordamchi ssenariysiga ulaydi |
| `VOICEMAIL` | Mijozga audio xabar qoldirish imkonini beradi |
| `FORWARD_EXTERNAL` | Navbatchi xodim mobil raqamiga (`afterHoursDestination`) yo'naltiradi |

---

## `POST /api/inbound-routes` — yangi marshrut

**Request body** (`CreateInboundRouteRequest`):

```json
{
  "didNumber": "998712345678",
  "aiAgentId": 9,
  "routeType": "SCENARIO",
  "targetDestination": null,
  "queueStrategy": "RING_ALL",
  "ringTimeoutSec": 20,
  "failoverAction": "SCENARIO",
  "failoverDestination": null,
  "afterHoursAction": "PLAY_MESSAGE_AND_HANGUP",
  "afterHoursDestination": "+998909998877",
  "ivrMenuConfig": "{\"1\": {\"action\": \"OPERATOR_QUEUE\", \"target\": \"sales\"}, \"2\": {\"action\": \"SCENARIO\", \"scenarioId\": 6}}",
  "businessHoursStart": "09:00:00",
  "businessHoursEnd": "18:00:00",
  "fallbackMessage": "Assalomu alaykum! Ish vaqtimiz 9:00 dan 18:00 gacha. Iltimos ish vaqtida qo'ng'iroq qiling."
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `didNumber` | string | ✅ (`@NotBlank`) | dialangan DID raqam (E.164); bitta faol raqam uchun bitta yoqilgan marshrut |
| `aiAgentId` | long | ❌ | `routeType == SCENARIO` bo'lsa [AI agent](ai-agents.md) ID — qo'ng'iroq shu agentning senariysi, tili, ovozi va personasi bilan javob beradi; boshqa turlarda bo'sh qoldiriladi (agentsiz marshrutga kelgan qo'ng'iroqni AI ko'tarmaydi). Agent senariysida [`factWebhook`](scenarios.md) sozlangan bo'lsa, qo'ng'iroqqa **javob berilishidan oldin** (mijoz hali gudok eshitayotganda) kompaniya tizimidan qo'ng'iroq qiluvchining faktlari so'raladi |
| `routeType` | enum | ❌ | Standart: `SCENARIO`. Variantlar: `SCENARIO`, `OPERATOR_QUEUE`, `EXTENSION`, `EXTERNAL_NUMBER`, `STICKY_AGENT`, `IVR_MENU`, `VOICEMAIL` |
| `targetDestination` | string | ❌ | Extension raqami (`101`) yoki guruh nomi (`sales_queue`) |
| `queueStrategy` | enum | ❌ | Standart: `RING_ALL`. Variantlar: `RING_ALL`, `ROUND_ROBIN`, `FEWEST_CALLS`, `LEAST_RECENT`, `RANDOM` |
| `ringTimeoutSec` | integer | ❌ | Operator jiringlash kutish vaqti (soniyalarda, standart: 20) |
| `failoverAction` | enum | ❌ | Javob bo'lmaganda: `SCENARIO`, `VOICEMAIL`, `EXTERNAL_FORWARD`, `HANGUP` |
| `afterHoursAction` | enum | ❌ | Ish vaqtidan tashqari: `PLAY_MESSAGE_AND_HANGUP`, `AI_AGENT`, `VOICEMAIL`, `FORWARD_EXTERNAL` |
| `afterHoursDestination` | string | ❌ | Tungi navbatchi mobil raqami |
| `ivrMenuConfig` | string (JSON) | ❌ | DTMF tugmalar konfiguratsiyasi |
| `businessHoursStart` / `businessHoursEnd` | `LocalTime` (`HH:mm:ss`) | ❌ | Ish vaqti oralig'i (berilmasa — 24/7 ochiq) |
| `fallbackMessage` | string | ❌ | Ish vaqtidan tashqari o'qib beriladigan TTS xabari |

**Response** (`InboundRouteRow`):

```json
{
  "id": 3,
  "didNumber": "998712345678",
  "aiAgentId": 9,
  "aiAgentName": "Kirish so'rovlari agenti",
  "routeType": "SCENARIO",
  "targetDestination": null,
  "queueStrategy": "RING_ALL",
  "ringTimeoutSec": 20,
  "failoverAction": "SCENARIO",
  "failoverDestination": null,
  "afterHoursAction": "PLAY_MESSAGE_AND_HANGUP",
  "afterHoursDestination": "+998909998877",
  "ivrMenuConfig": "...",
  "businessHoursStart": "09:00:00",
  "businessHoursEnd": "18:00:00",
  "fallbackMessage": "Assalomu alaykum! Ish vaqtimiz 9:00 dan 18:00 gacha.",
  "enabled": true,
  "createdAt": "2026-09-01T09:00:00Z"
}
```

---

## `POST /api/inbound-routes/list` — ro'yxat

Body — `InboundRouteFilter` (`page`/`size`/`orders`).
Saralash: `ID`, `DID_NUMBER`, `AI_AGENT_ID`, `ENABLED`, `CREATED_AT`.
Standart: `CREATED_AT DESC`.

Javob — `PageableData<InboundRouteRow>`.

---

## `GET /api/inbound-routes/{id}` — bitta marshrut

Javob — `InboundRouteRow`.

---

## `PUT /api/inbound-routes/{id}` — tahrirlash

**Request body** (`UpdateInboundRouteRequest`) — `POST /api/inbound-routes` bilan bir xil maydonlar, plus `enabled`.

---

## `DELETE /api/inbound-routes/{id}` — o'chirish (arxivlash)

Qatorni o'chirmaydi, `enabled=false` qiladi.

---

## `GET /api/inbound-routes/{id}/stats` — statistika

Qo'ng'iroqlar soni, qabul qilish foizi va dispositionlar taqsimoti.

---

## 🌱 Seed'dagi tayyor marshrut

Yangi baza ko'tarilganda (`R__seed_data.sql`) bitta marshrut allaqachon mavjud:

| `didNumber` | `aiAgentId` | `routeType` | Ish vaqti |
|---|---|---|---|
| `600` | `Kiruvchi qabulxona` agenti | `SCENARIO` | belgilanmagan (24/7) |

`600` — dialplan'dagi softphone test raqami (`asterisk/etc/asterisk/extensions.conf`,
`[from-internal]`): trunk'siz, faqat registratsiya qilingan softphone bilan kiruvchi
qo'ng'iroqni to'liq pipeline orqali sinash uchun. Haqiqiy DID `POST /api/inbound-routes`
orqali qo'shiladi.

`businessHoursStart`/`businessHoursEnd` ataylab bo'sh: ish vaqti belgilangan zahoti
undan tashqarida `fallbackMessage` o'qiladi va qo'ng'iroq tugatiladi.
