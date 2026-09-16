# Ovozli pochta (voicemail) API

`uz.murodjon.robotcallv2.voicemail` · huquq: qutilar — **CALL_FLOW_READ** / **CALL_FLOW_EDIT**;
xabarlar — **MISSED_CALL_READ** / **MISSED_CALL_EDIT**

Ikki qism:

- **Qutilar** (`/api/voicemail-boxes`) — call flow sozlamasi: mijoz xabar qoldiradigan
  joy, uning salomlashuvi va chegaralari. [Call flow](call-flows.md)ning `VOICEMAIL` tuguni
  (`voicemailBoxId`) va navbatning overflow → `VOICEMAIL` sozlamasi shu qutiga yozadi.
- **Xabarlar** (`/api/voicemails`) — operatorlarning ish ro'yxati: kim, qachon, nima dedi
  (audio + transkript), ko'rildi/arxivlandi.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## 🎙 Xabar qanday yozib olinadi

1. Salomlashuv: qutining `greetingFileId` audiosi → bo'lmasa `greetingText` (TTS) → u ham
   bo'lmasa standart ibora ("Iltimos, signaldan keyin xabaringizni qoldiring. Tugatgach,
   panjara belgisini bosing yoki go'shakni qo'ying.").
2. Signal (beep).
3. Mijoz gapiradi. Yozuv to'xtaydi: `#` bosilganda, gapdan keyin **5 soniya** jimlikda,
   qutining `maxSeconds` chegarasida yoki go'shak qo'yilganda.
4. Signaldan keyin **10 soniya** hech narsa aytilmasa yoki xabar 1 soniyadan qisqa bo'lsa —
   xabar **saqlanmaydi**.
5. `transcribe: true` bo'lsa yozish bilan birga STT ishlaydi — operator xabarni tinglash
   o'rniga o'qiy oladi; matn qo'ng'iroq transkriptiga ham qo'shiladi.
6. Saqlangach: mijozga "Xabaringiz qabul qilindi. Rahmat!", kompaniyaga `VOICEMAIL_RECEIVED`
   bildirishnomasi ("Yangi ovozli xabar", [notifications.md](notifications.md)) va operator
   ekraniga jonli `VOICEMAIL` hodisasi yuboriladi.

Quti o'chirilgan (`enabled: false`) yoki mavjud bo'lmasa — xabar olinmaydi, flow keyingi
tugunga (`next`) o'tadi.

---

## Qutilar (`/api/voicemail-boxes`)

### Obyekt shakli (`VoicemailBox`)

```json
{
  "id": 1,
  "companyId": 1,
  "name": "Sotuv bo'limi",
  "greetingText": "Assalomu alaykum. Hozir hech kim javob bera olmaydi. Signaldan keyin xabaringizni qoldiring, biz sizga qayta qo'ng'iroq qilamiz.",
  "greetingFileId": null,
  "maxSeconds": 120,
  "transcribe": true,
  "enabled": true,
  "createdAt": "2026-09-01T09:00:00Z"
}
```

---

### `POST /api/voicemail-boxes` — yangi quti

Huquq: **CALL_FLOW_EDIT**

**Request body** (`SaveVoicemailBoxRequest`):

```json
{
  "name": "Sotuv bo'limi",
  "greetingText": "Assalomu alaykum. Hozir hech kim javob bera olmaydi. Signaldan keyin xabaringizni qoldiring, biz sizga qayta qo'ng'iroq qilamiz.",
  "greetingFileId": null,
  "maxSeconds": 120,
  "transcribe": true,
  "enabled": true
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `name` | string | ✅ (`@NotBlank`) | |
| `greetingText` | string | ❌ | Signaldan oldin TTS bilan aytiladi; ≤ 1000 belgi. Bo'sh satr `null` saqlanadi |
| `greetingFileId` | long | ❌ | Oldindan yozilgan salomlashuv (WAV, [files.md](files.md)); berilsa `greetingText` o'rniga shu ijro etiladi. Fayl shu kompaniyaniki bo'lishi shart |
| `maxSeconds` | integer | ❌ | Eng uzun xabar, soniya: `10`–`600`. Standart `120` |
| `transcribe` | boolean | ❌ | Standart `true` |
| `enabled` | boolean | ❌ | Standart `true` |

Javob — `ResponseData<VoicemailBox>`:

```json
{
  "data": {
    "id": 1,
    "companyId": 1,
    "name": "Sotuv bo'limi",
    "greetingText": "Assalomu alaykum. ...",
    "greetingFileId": null,
    "maxSeconds": 120,
    "transcribe": true,
    "enabled": true,
    "createdAt": "2026-09-01T09:00:00Z"
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

**Xatolar:** `VALIDATION_FAILED` (400), `VOICEMAIL_BOX_NAME_REQUIRED` (400 — nom bo'sh),
`VOICEMAIL_BOX_GREETING_TOO_LONG` (400 — `greetingText` 1000 belgidan uzun), `VOICEMAIL_BOX_MAX_SECONDS_OUT_OF_RANGE`
(400 — `maxSeconds` 10–600 oralig'ida emas), `greetingFileId`
topilmasa yoki boshqa kompaniyaniki bo'lsa — fayl ombori xatosi (404).

---

### `POST /api/voicemail-boxes/list` — ro'yxat

Huquq: **CALL_FLOW_READ**

Body — `VoicemailBoxFilter`:

```json
{ "page": 0, "size": 20, "orders": { "NAME": "ASC" } }
```

Saralanadigan ustunlar: `ID`, `NAME`, `CREATED_AT`. Standart: `NAME ASC`.

Javob — `PageableData<VoicemailBox>`.

---

### `GET /api/voicemail-boxes/{id}` — bitta quti

Huquq: **CALL_FLOW_READ** · Javob — `VoicemailBox`.

**Xatolar:** `VOICEMAIL_BOX_NOT_FOUND` (404).

---

### `PUT /api/voicemail-boxes/{id}` — tahrirlash

Huquq: **CALL_FLOW_EDIT** · Body — `POST` bilan bir xil (`SaveVoicemailBoxRequest`); berilmagan
`maxSeconds`/`transcribe`/`enabled` yana standart qiymatga (`120`/`true`/`true`) qaytadi —
to'liq obyektni yuboring. Javob — yangilangan `VoicemailBox`.

**Xatolar:** `VOICEMAIL_BOX_NOT_FOUND` (404), `POST` dagi `VOICEMAIL_BOX_*` validatsiya xatolari (400),
`VALIDATION_FAILED` (400).

---

### `DELETE /api/voicemail-boxes/{id}` — o'chirish

Huquq: **CALL_FLOW_EDIT** · Javob — `data: null`.

> ⚠️ Quti **butunlay** o'chiriladi va unga qoldirilgan **barcha xabarlar ham o'chadi**
> (DB `ON DELETE CASCADE`). Xabarlarni saqlab qolish kerak bo'lsa, qutini o'chirish
> o'rniga `enabled: false` qiling. Qutiga ishora qilgan call flow `VOICEMAIL` tugunlari
> xabar olmay `next`ga o'tadi (keyingi `publish` esa `VOICEMAIL_BOX_NOT_FOUND` muammosini
> ko'rsatadi).

**Xatolar:** `VOICEMAIL_BOX_NOT_FOUND` (404).

---

## Xabarlar (`/api/voicemails`)

### Obyekt shakli (`VoicemailMessage`)

```json
{
  "id": 42,
  "companyId": 1,
  "boxId": 1,
  "callAttemptId": 5123,
  "callerPhone": "998901234567",
  "storedFileId": 880,
  "durationSec": 17,
  "transcript": "Assalomu alaykum, men Chilonzordagi kvartira bo'yicha qo'ng'iroq qilgandim, iltimos qayta qo'ng'iroq qiling.",
  "status": "NEW",
  "createdAt": "2026-09-12T19:42:10Z"
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `boxId` | long | Qaysi qutiga qoldirilgan |
| `callAttemptId` | long \| null | Xabar qoldirilgan qo'ng'iroq — uning xulosasi, yozuvi va transkripti [reports.md](reports.md)da |
| `callerPhone` | string \| null | Qo'ng'iroq qiluvchi raqami (yashirin raqamda `null`) |
| `storedFileId` | long \| null | Xabar audiosi; yozish paytida fayl ombori ishlamagan bo'lsa `null` (transkript va raqam baribir saqlanadi) |
| `durationSec` | integer | Xabar uzunligi, soniya |
| `transcript` | string \| null | STT eshitgan matn; quti transkripsiya qilmasa yoki hech narsa tanilmasa `null` |
| `status` | `VoicemailStatus` | `NEW` (yangi), `HEARD` (tinglangan), `ARCHIVED` (arxivlangan) |

---

### `POST /api/voicemails/list` — xabarlar ro'yxati

Huquq: **MISSED_CALL_READ**

Body — `VoicemailMessageFilter`:

```json
{
  "page": 0,
  "size": 20,
  "orders": { "CREATED_AT": "DESC" },
  "status": "NEW",
  "boxId": 1
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `status` | `VoicemailStatus` \| null | Faqat shu holatdagilar; `null` — hammasi |
| `boxId` | long \| null | Faqat shu quti xabarlari; `null` — hammasi |

Saralanadigan ustunlar: `ID`, `CREATED_AT`, `DURATION_SEC`. Standart: `CREATED_AT DESC`.

Javob — `PageableData<VoicemailMessage>`:

```json
{
  "data": {
    "totalPages": 1,
    "currentPage": 0,
    "totalElements": 1,
    "data": [
      {
        "id": 42,
        "companyId": 1,
        "boxId": 1,
        "callAttemptId": 5123,
        "callerPhone": "998901234567",
        "storedFileId": 880,
        "durationSec": 17,
        "transcript": "Assalomu alaykum, ...",
        "status": "NEW",
        "createdAt": "2026-09-12T19:42:10Z"
      }
    ]
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

> `GET /api/voicemails/{id}` va xabarni o'chirish endpointi **yo'q**.

---

### `PUT /api/voicemails/{id}/status` — holatni o'zgartirish

Huquq: **MISSED_CALL_EDIT**

**Request body** (`UpdateVoicemailStatusRequest`):

```json
{ "status": "HEARD" }
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `status` | enum | ✅ (`@NotNull`) | `NEW`, `HEARD`, `ARCHIVED` — istalgan holatdan istalganiga (masalan, `ARCHIVED` → `NEW`) |

Javob — yangilangan `VoicemailMessage`.

**Xatolar:** `VOICEMAIL_MESSAGE_NOT_FOUND` (404), `VALIDATION_FAILED` (400).

---

### `GET /api/voicemails/{id}/audio` — xabarni tinglash

Huquq: **MISSED_CALL_READ**

Xabar audiosi (WAV, 8 kHz mono) — **`ResponseData` ga o'ralmagan** fayl javobi, xuddi
qo'ng'iroq yozuvi kabi. `Range` sarlavhasi qo'llab-quvvatlanadi (brauzer `<audio>` pleyeri
o'rtasidan boshlab ijro eta oladi → `206 Partial Content`).

```http
GET /api/voicemails/42/audio
Authorization: Bearer <accessToken>
Range: bytes=0-
```

`<audio src>` Authorization sarlavhasini yubora olmagani uchun faylni `fetch` bilan olib
`Blob` URL orqali ijro eting.

**Xatolar** (JSON `ResponseData` bilan): `VOICEMAIL_MESSAGE_NOT_FOUND` (404),
`VOICEMAIL_AUDIO_NOT_FOUND` (404 — xabarning audiosi yo'q, `storedFileId: null`).
