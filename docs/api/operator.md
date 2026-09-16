# Operator API

> Operator SIP akkaunti, statusi va panel SSE oqimi (`/api/operators`, ko'plik) — alohida hujjat: [operators.md](operators.md).

`uz.murodjon.robotcallv2.operator` · huquq: **OPERATOR_READ** / **OPERATOR_EDIT**

Qo'ng'iroq operatorga uzatilgandan keyin operator ekrani shu endpointlarni ishlatadi:
mijoz kontekstini ko'rish, jonli qo'ng'iroqni o'z apparatiga qabul qilish (takeover) va
supervisor orqali botga yo'naltirish (whisper) berish.

Umumiy javob shakli va xatolar uchun [README.md](README.md)ga qarang.

Uchala endpoint ham faqat token kompaniyasining jonli qo'ng'irog'i uchun ishlaydi: kanal
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
bo'lsa — `409 CALL_ON_OTHER_INSTANCE` (bu uchala endpoint uchun ham amal qiladi).

`message` majburiy: bo'sh, faqat bo'shliqdan iborat yoki 1000 belgidan uzun bo'lsa — `400
VALIDATION_FAILED` (`errors: ["message: must not be blank"]`), body'siz so'rov — `400
MALFORMED_REQUEST_BODY`. Ilgari bunday so'rov bo'sh ko'rsatma sifatida yuborilardi.
