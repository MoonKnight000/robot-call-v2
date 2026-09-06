# Qo'lda va Tezkor Qo'ng'iroqlar API

`uz.murodjon.robotcallv2.callrecord` · huquq: **CALL_EDIT** (qo'ng'iroq boshlash), **LIVE_READ** / **LIVE_EDIT** (jonli kanalni boshqarish)

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
| `FAILED` | Texnik nosozlik (kanal ochilmadi, pipeline yiqildi) |
| `COMPLETED` | Muloqot normal yakunlandi |

**Konversiya deb sanaladiganlar:** faqat `PROMISE_TO_PAY` va `COMPLETED`
(`Disposition.isConversion()`). `TRANSFERRED` ataylab hisoblanmaydi — operatorga o'tkazish
botning ishni oxiriga yetkaza olmagani; uni konversiya deb sanash eng tez taslim
bo'ladigan ssenariyni A/B testda g'olib qilardi ([campaigns.md](campaigns.md)).

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
    "channelId": "+998901234567",
    "status": "1710000000.12"
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

> ⚠️ **Maydon nomlari kodda almashib ketgan.** `CallOriginateResponse` record'i
> `(channelId, status)` deb e'lon qilingan, lekin `AriService` uni
> `new CallOriginateResponse(number, channelId)` bilan to'ldiradi. Ya'ni amalda
> **`channelId` — terilgan raqam, `status` — haqiqiy Asterisk kanal id'si**. Jonli
> boshqaruv endpointlariga (`/{channelId}/hangup` va h.k.) `status` maydonidagi
> qiymatni bering. Bu `POST /api/calls` va `POST /api/calls/test` uchun bir xil.

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

## `POST /api/calls/web-test` — Brauzerdan test qo'ng'irog'i (mikrofon ↔ AI)

Kampaniyani (yoki ssenariyni) telefon va trunk daqiqasisiz, **to'g'ridan-to'g'ri brauzerda** sinash: foydalanuvchi mikrofonga gapiradi, AI javobi dinamikdan eshitiladi. Audio yo'li real qo'ng'iroq bilan bir xil — brauzer WebRTC orqali Asterisk'ga ulanadi, u yog'i odatdagi Stasis → RTP → STT/LLM/TTS pipeline. Qo'ng'iroq `call_attempt` da `MANUAL` target ostida, telefon `WEB-TEST` bilan yoziladi; kampaniya statistikasi va target holati **o'zgarmaydi**, CRM ga hech narsa yozilmaydi.

Endpoint qo'ng'iroq qilmaydi — u bir martalik **sessiya** yaratadi va brauzer o'zi ulanishi uchun kerak bo'lgan hamma narsani qaytaradi. Sessiya 5 daqiqa ichida terilmasa unutiladi.

**Request Body** (`WebTestCallRequest`) — `campaignId`, `scenarioId`, `definition` dan **ko'pi bilan bittasi**; hech biri berilmasa standart test ssenariysi:

| Maydon | Turi | Izoh |
|---|---|---|
| `campaignId` | number | Kampaniya [agentining](ai-agents.md) to'liq profili bilan: ssenariy, tillar bo'yicha ovozlar, persona, ambient sound, disclosure, DTMF |
| `targetId` | number | Ixtiyoriy, faqat `campaignId` bilan. Shu target faktlari (contextData + CRM + memory) ishlatiladi; berilmasa `voice-agent.dialog.test-context` faktlari |
| `scenarioId` | number | Saqlangan ssenariyni standart sozlamalar bilan sinash (`POST /api/calls?scenarioId=` kabi) |
| `definition` | `ScenarioDefinition` | Saqlanmagan qoralama (`POST /api/calls/test` kabi) |

```json
{ "campaignId": 12, "targetId": 1042 }
```

**Response** (`WebTestCallResponse`):

```json
{
  "accept": true,
  "data": {
    "sessionId": "6f1c2a4e-3b7d-4c1e-9a0f-2d5e8b7c1a90",
    "wsUrl": "ws://192.168.0.100:8088/ws",
    "sipUser": "webtest",
    "sipPassword": "webtest123",
    "dialNumber": "700",
    "sessionHeader": "X-Web-Test"
  },
  "errors": null
}
```

**Xatolar:** `400 WEB_TEST_SOURCE_INVALID` (bir nechta manba yoki `targetId` `campaignId`siz), `400 SCENARIO_DEFINITION_INVALID`, `404 CAMPAIGN_NOT_FOUND` / `TARGET_NOT_FOUND`, `409 WEB_TEST_NOT_CONFIGURED` (serverda `ws-url` bo'sh).

### Frontend oqimi

1. `POST /api/calls/web-test` → javobni ol.
2. SIP.js (`sip.js@0.21`) bilan `wsUrl` ga `sipUser`/`sipPassword` sifatida ulan. Ro'yxatdan o'tish (REGISTER) **shart emas** — har bir INVITE o'zi autentifikatsiya qilinadi.
3. `sip:<dialNumber>@<wsUrl host>` ga INVITE yubor, `extraHeaders` ga `"<sessionHeader>: <sessionId>"` qo'sh, `constraints: { audio: true, video: false }`.
4. Sessiya `Established` bo'lganda remote track'ni `<audio autoplay>` ga ula. Tugatish — `session.bye()`.
5. Jonli transkript va holat — odatdagidek `GET /api/calls/live` va `/api/live/**` SSE orqali (`phone` = `WEB-TEST`).

```js
import { UserAgent, Inviter } from "sip.js";

const { data } = await api.post("/api/calls/web-test", { campaignId: 12 });
const host = new URL(data.wsUrl).host;
const ua = new UserAgent({
  uri: UserAgent.makeURI(`sip:${data.sipUser}@${host}`),
  transportOptions: { server: data.wsUrl },
  authorizationUsername: data.sipUser,
  authorizationPassword: data.sipPassword,
});
await ua.start();

const inviter = new Inviter(ua, UserAgent.makeURI(`sip:${data.dialNumber}@${host}`), {
  extraHeaders: [`${data.sessionHeader}: ${data.sessionId}`],
  sessionDescriptionHandlerOptions: { constraints: { audio: true, video: false } },
});
inviter.stateChange.addListener(state => {
  if (state === "Established") {
    const remote = new MediaStream();
    inviter.sessionDescriptionHandler.peerConnection.getReceivers()
      .forEach(r => r.track && remote.addTrack(r.track));
    audioElement.srcObject = remote;      // <audio autoplay>
  }
});
await inviter.invite();
// tugatish: await inviter.bye(); await ua.stop();
```

**Brauzer talablari:** `getUserMedia` faqat `https://` yoki `http://localhost` da ishlaydi; `https` sahifadan faqat `wss://` ochiladi (Asterisk `http.conf` TLS, `WEB_TEST_WS_URL=wss://…:8089/ws`). Backend'siz sinash uchun `docs/web-test.html` sahifasini oching (docs/RUN.md "Brauzerdan test").

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
