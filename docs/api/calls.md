# Qo'lda va Tezkor Qo'ng'iroqlar API

`uz.murodjon.robotcallv2.callrecord` · rol: **OPERATOR / ADMIN**

Kampaniyaga bog'liq bo'lmagan, qo'lda yoki sayt/CRM orqali bir lahzada ishga tushiriladigan tekshiruv va tezkor qo'ng'iroq endpointlari. Har bir qo'ng'iroq qaysi SIP trunk orqali amalga oshirilgani tizimda aniq qayd etiladi va hisobotlarda ko'rsatiladi.

---

## 🎧 Ulanish signali (Connection Chime & Gudok)

Qo'ng'iroq ulanishi bilan (`StasisStart` / go'shak ko'tarilganda), bot darhol gap boshlab yubormasdan, xuddi Telegram yoki zamonaviy VoIP ilovalaridagi kabi tabiiy ulanish ohangini (melodic connection chime C5→E5) chalib, suhbatga tayyorgarlik hissini beradi.

---

## 📋 Qo'ng'iroq Yakuniy Natijalari (Dispositions)

| Disposition | Izoh |
|---|---|
| `PROMISE_TO_PAY` | To'lov va'dasi olindi (sana va summa yozildi) |
| `CALLBACK_REQUESTED` | Mijoz keyinroq qayta qo'ng'iroq qilishni so'radi (`scheduleCallback`) |
| `REFUSED` | Mijoz to'lashdan bosh tortdi |
| `WRONG_NUMBER` | Noto'g'ri raqam yoki boshqa shaxs |
| `DO_NOT_CALL` | Raqamni o'chirishni talab qildi (DNC ro'yxatiga olindi) |
| `TRANSFERRED` | Tirik operatorga uzatildi / Takeover qilindi |
| `VOICEMAIL` | Avtootvetchik yoki mobil operator xabari (AMD orqali aniqlangan) |
| `NO_ANSWER` | Go'shak ko'tarilmadi |
| `CARRIER_REJECTED` | Operator orqali ulanmadi |
| `HUNG_UP` | Mijoz suhbat davomida go'shakni qo'ydi |
| `COMPLETED` | Muloqot normal yakunlandi |

---

## `POST /api/calls` — Qo'lda qo'ng'iroq qilish / Ssenariyni sinash

Saqlangan stsenariy asosida kiritilgan raqamga qo'ng'iroq boshlaydi.

**Query Parametrlari:**

| Parametr | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `number` | `string` | ✅ | Teriladigan telefon raqami (masalan `+998901234567`) |
| `scenarioId` | `number` | ❌ | Sinovdan o'tkaziladigan stsenariy ID raqami. Berilmasa — standart test stsenariysi ishlatiladi |
| `sipTrunkId` | `number` | ❌ | **Chiquvchi SIP trunk ID raqami**. Aniq bir trunkdan qo'ng'iroq qilish uchun ko'rsatiladi. Agar berilmasa — kompaniyaning standart (default) yoqilgan trunki ishlatiladi |

**Misol So'rov:**
`POST /api/calls?number=+998901234567&scenarioId=4&sipTrunkId=2`

**Response** (`CallOriginateResponse`):
```json
{
  "accept": true,
  "data": {
    "number": "+998901234567",
    "channelId": "1710000000.12"
  },
  "messageCode": "SUCCESS",
  "errors": null
}
```

---

## `POST /api/calls/test` — Saqlanmagan qoralama (Draft) stsenariyni sinash

Hali bazada saqlanmagan, veb-muharrirda tahrirlanayotgan yangi stsenariy JSON strukturasini to'g'ridan-to'g'ri haqiqiy telefonga qo'ng'iroq qilib sinash.

**Query Parametrlari:**

| Parametr | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `number` | `string` | ✅ | Teriladigan telefon raqami |
| `sipTrunkId` | `number` | ❌ | Chiquvchi SIP trunk ID raqami (berilmasa — default trunk) |

**Request Body**: To'liq `ScenarioDefinition` JSON obyekti (`states`, `edges`, `systemPrompt`, va h.k.).

**Misol So'rov:**
`POST /api/calls/test?number=+998901234567&sipTrunkId=1`

---

## `POST /api/calls/instant` — Tezkor (Instant Trigger) qo'ng'iroq

Saytda yangi ariza (Lead) tushgan zahoti, webhook yoki CRM orqali 5-10 soniya ichida navbatsiz ustuvor (URGENT) qo'ng'iroqni ishga tushiradi.

**Request Body** (`InstantCallRequest`):
```json
{
  "campaignId": 12,
  "phone": "+998901234567",
  "clientName": "Sardor aka",
  "contextData": {
    "orderNumber": "ORD-9981",
    "deliveryAddress": "Toshkent, Yunusobod 4-mavze",
    "orderAmount": 450000
  }
}
```

**Response**:
```json
{
  "accept": true,
  "data": {
    "targetId": 1042,
    "status": "QUEUED_INSTANT"
  },
  "errors": null
}
```

---

## `GET /api/calls/live` — Jonli faol qo'ng'iroqlar monitoringi

Ayni paytda suhbatda bo'lgan barcha qo'ng'iroqlar ro'yxatini, jumladan qaysi SIP trunkdan qo'ng'iroq ketayotganini qaytaradi.

**Response** (`List<LiveCallRow>`):
```json
{
  "accept": true,
  "data": [
    {
      "channelId": "1710000000.12",
      "phone": "+998901234567",
      "clientName": "Aziz Karimov",
      "campaignId": 12,
      "campaignName": null,
      "language": "uz-UZ",
      "startedAt": "2026-03-01T10:15:30Z",
      "dialogState": "OFFER_PAYMENT_PLAN",
      "trunk": "trunk_1_2"
    }
  ],
  "errors": null
}
```

---

## `POST /api/calls/live/{channelId}/whisper` — Jonli operator ko'rsatmasi (Whisper Mode)

Operator davom etayotgan jonli suhbatni eshitib turib, robotga maxfiy ko'rsatma kiritadi. Robot keyingi gapida ushbu ko'rsatmaga amal qiladi.

**Request Body**:
```json
{
  "instruction": "Mijozga bugun to'lasa 5% chegirma bera olishimizni ayt."
}
```

**Response**:
```json
{
  "accept": true,
  "data": {
    "channelId": "1710000000.12",
    "status": "WHISPER_INJECTED"
  },
  "errors": null
}
```

---

## `POST /api/calls/live/{channelId}/takeover` — Qo'ng'iroqni operatorga olish (Human Takeover)

Robot ovozi to'xtatiladi va jonli mijoz zudlik bilan tirik operator ichki raqamiga (SIP extension) uzatiladi.

**Request Body**:
```json
{
  "extension": "101"
}
```

**Response**:
```json
{
  "accept": true,
  "data": {
    "channelId": "1710000000.12",
    "status": "TAKEOVER_TRIGGERED"
  },
  "errors": null
}
```

---

## Boshqa jonli kanal boshqaruv endpointlari

- `POST /api/calls/{channelId}/play?file=greeting.wav` — Jonli kanalga audio fayl eshittirish.
- `POST /api/calls/{channelId}/say?text=...&language=uz-UZ&voice=dilnavoz` — Jonli kanalga matnni TTS qilib o'qib berish.
- `POST /api/calls/{channelId}/hangup` — Jonli kanalni majburiy to'xtatish (go'shakni qo'yish).
- `POST /api/calls/{channelId}/transfer` — Standart operator navbatiga uzatish.
- `GET /api/calls/{channelId}/listen` — Jonli qo'ng'iroq audio oqimini WAV formatida real-vaqtda tinglash (`audio/wav`).
