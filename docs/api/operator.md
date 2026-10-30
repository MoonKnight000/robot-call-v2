# Operator API

> Operator SIP akkaunti, statusi va panel SSE oqimi (`/api/operators`, ko'plik) — alohida hujjat: [operators.md](operators.md).

`uz.murodjon.robotcallv2.operator` · huquq: **OPERATOR_READ** / **OPERATOR_EDIT**

Qo'ng'iroq operatorga uzatilgandan keyin operator ekrani shu endpointlarni ishlatadi:
mijoz kontekstini ko'rish, jonli qo'ng'iroqni o'z apparatiga qabul qilish (takeover) va
supervisor orqali botga yo'naltirish (whisper) berish.

Umumiy javob shakli va xatolar uchun [README.md](README.md)ga qarang.

Dastlabki uchala endpoint ham faqat token kompaniyasining jonli qo'ng'irog'i uchun ishlaydi: kanal
yo'q, tugagan yoki **boshqa kompaniyaniki** bo'lsa — `409 CALL_NOT_ACTIVE` (bir xil javob,
kanal mavjudligi oshkor qilinmaydi). Kontekst hali yig'ilmagan bo'lsa —
`404 OPERATOR_SNAPSHOT_NOT_FOUND`.

---

## `GET /api/operator/calls/{channelId}` — uzatilgan qo'ng'iroq konteksti

**Response** (`OperatorSnapshot`):

```json
{
  "data": {
    "channelId": "PJSIP/trunk-00000012",
    "clientName": "Aziz Karimov",
    "debtAmount": "1500000",
    "currency": "so'm",
    "dueDate": "2026-07-01",
    "contractNumber": "UY-2026-00123",
    "dialogState": "HUMAN_TRANSFER",
    "transcript": "AGENT: Assalomu alaykum...\nCLIENT: Ha, tinglayapman...\n..."
  },
  "message": null, "messageCode": null, "accept": true, "errors": null
}
```

---

## `POST /api/operator/calls/{channelId}/takeover` — qo'ng'iroqni operatorga qabul qilish (Takeover)

**Query params:** `extension` (ixtiyoriy, standart `100`).

**Response:**

```json
{
  "data": {
    "channelId": "PJSIP/trunk-00000012",
    "status": "TRANSFERRED",
    "operatorExtension": "101"
  },
  "message": null, "messageCode": null, "accept": true, "errors": null
}
```

---

## `POST /api/operator/calls/{channelId}/whisper` — supervisor yo'riqnomasi yuborish (Whisper)

**Request body:**

```json
{
  "message": "Mijozga 10 foiz chegirma taklif qiling"
}
```

**Response:**

```json
{
  "data": {
    "channelId": "PJSIP/trunk-00000012",
    "status": "DELIVERED",
    "message": "Mijozga 10 foiz chegirma taklif qiling"
  },
  "message": null, "messageCode": null, "accept": true, "errors": null
}
```

`status`:
- `DELIVERED` — bot ko'rsatmani oldi: keyingi turn'dan boshlab promptida turadi, yangi whisper
  kelguncha amal qiladi (xuddi `POST /api/calls/live/{channelId}/whisper` kabi).
- `NOTIFIED` — qo'ng'iroqda ssenariyli bot dialogi yo'q (realtime engine, yoki qo'ng'iroq allaqachon
  operatorda): faqat jonli kanalga `WHISPER` bildirishnomasi ketdi.

Kanal kompaniyangizning jonli qo'ng'irog'i bo'lmasa — `409 CALL_NOT_ACTIVE`; boshqa instance'da
bo'lsa server so'rovni o'sha instance'ga uzatadi, unga yetib bo'lmasa — `409 CALL_ON_OTHER_INSTANCE`
(bu uchala endpoint uchun ham amal qiladi; batafsil — [live.md](live.md)).

`message` majburiy: bo'sh, faqat bo'shliqdan iborat yoki 1000 belgidan uzun bo'lsa — `400
VALIDATION_FAILED` (`errors: ["message: must not be blank"]`), body'siz so'rov — `400
MALFORMED_REQUEST_BODY`. Ilgari bunday so'rov bo'sh ko'rsatma sifatida yuborilardi.

---

## LiveKit dvigatelida uzatilgan qo'ng'iroq (brauzerdan qabul qilish)

Kompaniya `LIVEKIT` dvigatelida bo'lsa ([../LIVEKIT.md](../LIVEKIT.md)), agent qo'ng'iroqni operatorga
uzatganda softphone jiringlamaydi: operator **brauzerdan** WebRTC orqali qo'ng'iroq xonasiga qo'shiladi.

Agent uzatganda (yoki [call flow](call-flows.md)ning `RING_GROUP` / `EXTENSION` / `QUEUE` tuguni LiveKit qo'ng'irog'ida) qo'ng'iroq kompaniyaning eng uzoq vaqtdan beri `AVAILABLE` turgan bitta operatoriga taklif
qilinadi (`claimForRinging` — operator `RINGING` bo'ladi) va unga `CALL_OFFER` SSE hodisasi keladi
([operators.md](operators.md)). Hodisadagi `channelId` — qo'ng'iroq xonasining nomi, quyidagi endpointlarda
shu ishlatiladi. Operator `voice-agent.operator.answer-timeout-sec` ichida (standart 30 soniya) javob bermasa,
`markMissed` bajariladi (ketma-ket ikki marta — `BREAK`) va mijozga "operatorlar band" aytilib, qo'ng'iroq
`CALLBACK_REQUESTED` bilan yopiladi. `AVAILABLE` operator bo'lmasa shu zahoti.

### `POST /api/operator/calls/{channelId}/accept` — taklifni qabul qilish

Huquq: **OPERATOR_EDIT**. Faqat shu qo'ng'iroq o'ziga taklif qilingan operator uchun.

**Response:**

```json
{
  "data": {
    "url": "wss://livekit.example.com",
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "identity": "op-17"
  },
  "message": null, "messageCode": null, "accept": true, "errors": null
}
```

Brauzer `livekit-client` bilan `url` ga `token` bilan ulanadi va mikrofonini nashr etadi. Token faqat shu
xonaga kirish uchun, 10 daqiqa amal qiladi. Operator xonaga qo'shilgach agent chiqib ketadi, operator `ON_CALL`
bo'ladi, mijoz bilan to'g'ridan-to'g'ri gaplashadi. Qo'ng'iroq mijoz yoki operator chiqqanda tugaydi
(operator `WRAP_UP` ga o'tadi). Qabul qilingandan keyin `accept` ni qayta chaqirish yangi token beradi.

Xatolar: `404 LIVEKIT_TRANSFER_NOT_FOUND` (qo'ng'iroq bu operatorga taklif qilinmagan yoki taklif muddati
o'tgan), `409 LIVEKIT_DISABLED`, `409 LIVEKIT_NOT_CONFIGURED` (`LIVEKIT_PUBLIC_URL` berilmagan).

### `POST /api/operator/calls/{channelId}/decline` — taklifni rad etish

Huquq: **OPERATOR_EDIT**. Operator darhol `AVAILABLE` ga qaytadi, mijozga "operatorlar band" aytilib,
qo'ng'iroq `CALLBACK_REQUESTED` bilan yopiladi. Javob: `data: null`. Xato: `404 LIVEKIT_TRANSFER_NOT_FOUND`.

Bu bosqichda yo'q: navbat (hold musiqa, pozitsiya), bir nechta operatorga ketma-ket taklif, ish smenasini
tekshirish, uzatilgandan keyingi suhbat yozuvi.
