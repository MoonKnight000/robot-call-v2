# Operator API

`uz.murodjon.robotcallv2.operator` · huquq: **OPERATOR_READ** / **OPERATOR_EDIT**

Qo'ng'iroq operatorga uzatilgandan keyin operator ekrani shu endpointlarni ishlatadi:
mijoz kontekstini ko'rish, jonli qo'ng'iroqni o'z apparatiga qabul qilish (takeover) va
supervisor orqali botga yo'naltirish (whisper) berish.

Umumiy javob shakli va xatolar uchun [README.md](README.md)ga qarang.

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
