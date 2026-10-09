# Preview dialing (operator kartasi) API

`uz.murodjon.robotcallv2.dialer` · huquq: **OPERATOR_EDIT** ·
[reja](../OPERATOR-DIALER-ANALYTICS-PLAN.md) §1.2

`dialMode: "PREVIEW"` kampaniyani AI emas, kampaniya navbatining (`callQueueId`, [call-queues.md](call-queues.md))
**operatorlari** teradi. Operator mijoz kartasini oladi, uni ko'rib chiqadi, keyin teradi yoki
qaytaradi. Kampaniya yaratish va target qo'shish — [campaigns.md](campaigns.md).

Oqim:

```
POST /next ──► karta (mijoz + xotira) ──┬─► POST /targets/{id}/dial ──► operator softfoni jiringlaydi,
                                        │                               ko'targach mijoz teriladi
                                        │      ... suhbat ...
                                        │   POST /api/calls/{callId}/wrap-up ──► karta yopiladi
                                        └─► POST /targets/{id}/skip ──► karta 15 daqiqaga qaytariladi
```

Qoidalar:

- Operatorda bir kampaniyadan bir vaqtda **bitta karta** bo'ladi. `next` qayta chaqirilsa, o'sha
  karta qaytadi (sahifa yangilansa ham yo'qolmaydi).
- Karta olingan zahoti target `IN_PROGRESS` bo'ladi va bitta urinish hisoblanadi. Karta
  operatorda **2 soat** turadi; shu vaqt ichida yopilmasa, dialer uni `FAILED` deb hisoblab qayta
  navbatga qo'yadi.
- Yangi karta faqat kampaniya `ACTIVE`, dial oynasi ochiq (kampaniya va kompaniya oynasi,
  kompaniya vaqt zonasida, bayramlar hisobga olinadi) va balans yetarli bo'lganda beriladi.
  DNC ro'yxatidagi raqamlar va kompaniyaning kontakt chastotasi siyosati (`settings.md`) ushlab
  turgan raqamlar o'tkazib yuboriladi.
- `maxConcurrentCalls`, `dailyCallCap` va kampaniya budjeti PREVIEW'da **qo'llanmaydi** — tezlikni
  operatorlarning o'zi belgilaydi.
- Qo'ng'iroq natijasi:
  - mijoz ko'tarmadi → target avtomatik `NO_ANSWER` bilan yopiladi (kampaniyaning retry qoidasi);
    mijozni terib bo'lmadi → `FAILED`. Wrap-up kerak emas;
  - mijoz ko'tardi → operator qo'ng'iroqni [wrap-up](wrap-up-codes.md#post-apicallscallidwrap-up--qongiroqni-yopish--operator_edit)
    bilan yopadi, kodning `outcome`i targetni yopadi.

Umumiy javob shakli va xatolar uchun [README.md](README.md)ga qarang.

---

## `POST /api/operator/campaigns/{campaignId}/next` — Keyingi karta

Body yo'q. Operatordagi karta, u bo'lmasa yangisi. Hozir teriladigan mijoz yo'q bo'lsa
`data: null`.

Ushlab turilgan karta kampaniya to'xtatilgan (`PAUSED`) yoki dial oynasi yopilgan bo'lsa ham
qaytadi — terilgan kartani yopish, terilmaganini qaytarish uchun.

Javob (`PreviewCardResponse`):

```json
{
  "target": {
    "id": 300,
    "campaignId": 3,
    "clientId": 15,
    "phone": "998901112233",
    "language": "uz",
    "contextData": "{\"name\":\"Ali\",\"debt\":1500000}",
    "status": "IN_PROGRESS",
    "attempts": 1,
    "doNotCall": false,
    "nextAttemptAt": "2026-10-06T12:00:00Z"
  },
  "campaignName": "Qarzdorlar — oktyabr",
  "callId": null,
  "memory": {
    "id": 8,
    "companyId": 1,
    "phone": "998901112233",
    "preferredName": "Ali aka",
    "preferredLanguage": "uz",
    "operatorNotes": "Ertalab qo'ng'iroq qilmaslik",
    "recentCalls": [],
    "facts": {},
    "updatedAt": "2026-10-01T09:00:00Z"
  }
}
```

| Maydon | Izoh |
|---|---|
| `target.nextAttemptAt` | Karta shu vaqtgacha operatorda turadi (olingan vaqt + 2 soat) |
| `callId` | Karta olingandan beri shu target uchun qilingan oxirgi qo'ng'iroq. `null` — hali terilmagan. `dial` dan keyin `next` ni qayta chaqirib olinadi va wrap-up shu id bilan yuboriladi |
| `memory` | Mijoz xotirasi ([memory.md](memory.md)); bo'lmasa `null` |

## `POST /api/operator/campaigns/{campaignId}/targets/{targetId}/dial` — Terish

Body yo'q. Avval operatorning softfoni jiringlaydi (caller ID'da mijoz raqami), operator
ko'targach mijoz teriladi va ikkalasi yozuv bilan ulanadi. Operator ko'tarmasa karta unda
qoladi — qayta terish yoki qaytarish mumkin.

Javob (`PreviewDialResponse`):

```json
{ "targetId": 300, "operatorChannelId": "1728208800.42" }
```

## `POST /api/operator/campaigns/{campaignId}/targets/{targetId}/skip` — Qaytarish

Body yo'q, javob `data: null`. Terilmagan kartani qaytaradi: target `PENDING` bo'ladi va
15 daqiqadan keyin yana beriladi, hisoblangan urinish qaytariladi. Terilgan kartani qaytarib
bo'lmaydi — u wrap-up bilan yopiladi.

---

**Xatolar** (uchala endpoint):

| Status | `messageCode` | Qachon |
|---|---|---|
| `403` | `PREVIEW_NOT_QUEUE_MEMBER` | Operator kampaniya navbatining a'zosi emas |
| `404` | `CAMPAIGN_NOT_FOUND` | Kampaniya yo'q yoki boshqa kompaniyaniki |
| `409` | `CAMPAIGN_NOT_PREVIEW` | Kampaniya `dialMode` i `PREVIEW` emas |
| `409` | `CAMPAIGN_HAS_NO_QUEUE` | Kampaniya navbati o'chirilgan — boshqasini belgilang |
| `409` | `CAMPAIGN_NOT_DIALLING` | `next` (yangi karta) / `dial`: kampaniya `ACTIVE` emas |
| `409` | `CAMPAIGN_OUTSIDE_DIAL_WINDOW` | `next` (yangi karta) / `dial`: dial oynasidan tashqari |
| `409` | `CALL_BALANCE_INSUFFICIENT` | `next` (yangi karta) / `dial`: balans yetarli emas |
| `409` | `PREVIEW_TARGET_NOT_HELD` | `dial` / `skip`: bu target operatorning joriy kartasi emas |
| `409` | `PREVIEW_WRAP_UP_REQUIRED` | `skip`: karta allaqachon terilgan — wrap-up qiling |
| `409` | `OPERATOR_NOT_REACHABLE` | `dial`: operatorning SIP akkaunti yo'q yoki ulanmagan |
| `409` | `OPERATOR_ON_CALL` | `dial`: operator hozir qo'ng'iroqda |
