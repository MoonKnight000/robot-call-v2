# Qo'lda va Tezkor Qo'ng'iroqlar API

`uz.murodjon.robotcallv2.callrecord` · huquq: **CALL_EDIT** (qo'ng'iroq boshlash), **LIVE_READ** / **LIVE_EDIT** (jonli kanalni boshqarish), **CALL_MONITOR** (supervayzer kuzatuvi), **OPERATOR_EDIT** (click-to-call)

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
| `GOAL_REACHED` | Ssenariy maqsadi bajarildi: `outcomeSchema` dagi `"goal": true` maydon qo'ng'iroqda yozildi (masalan tool javobidagi `orderId`) — [scenarios.md](scenarios.md) |
| `GOAL_NOT_REACHED` | Ssenariyda maqsad maydoni bor, lekin qo'ng'iroq uni yozmay tugadi |

`GOAL_REACHED`/`GOAL_NOT_REACHED` ni model emas, kod belgilaydi: model "maqsadga erishdim"
desa ham maqsad maydoni yozilmagan bo'lsa — `GOAL_NOT_REACHED`; maqsad yozilib, mijoz
xayrlashmasdan go'shakni qo'ysa ham — `GOAL_REACHED`. O'z oqibati bor natijalar
(`TRANSFERRED`, `DO_NOT_CALL`, `CALLBACK_REQUESTED`, …) o'zgartirilmaydi. Maqsad maydoni yo'q
ssenariy (masalan qarz undirish) bu ikkisi bilan tugamaydi — undagi `PROMISE_TO_PAY`/`REFUSED`
avvalgidek qoladi.

**Konversiya deb sanaladiganlar:** faqat `PROMISE_TO_PAY`, `GOAL_REACHED` va `COMPLETED`
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

**Xatolar** (`POST /api/calls` va `POST /api/calls/test` uchun bir xil): `400 PHONE_INVALID`,
`403 COMPANY_SUSPENDED` (kompaniya to'xtatilgan), `409 CALL_NUMBER_ON_DO_NOT_CALL_LIST` (raqam
kompaniyaning DNC ro'yxatida), `409 CALL_BALANCE_INSUFFICIENT` (faqat `BILLING_ENFORCE_BALANCE=true`
bo'lganda, balans bitta qo'ng'iroq zaxirasidan kam), `404 SIP_TRUNK_NOT_FOUND` (berilgan `sipTrunkId`
topilmadi — endi boshqa trunk'ka jimgina o'tilmaydi), `409 SIP_TRUNK_NONE_CONFIGURED` (kompaniyaning o'z
trunk'i yo'q; platforma trunk'i faqat platforma kompaniyasining qo'ng'iroqlarini olib chiqadi),
`502 ARI_ORIGINATE_FAILED`. Trunk sababli rad etilgan qo'ng'iroq ham `call_attempt` da `FAILED` bo'lib
yoziladi. Kampaniya dialerida trunk'i yo'q kompaniyaning kampaniyasi `PAUSED` qilinadi. Bu uch tekshiruv
(to'xtatilgan kompaniya, DNC, balans) operator, callback, navbatdan tashqi raqamga yo'naltirish va
konsultatsiya qo'ng'iroqlariga ham qo'llanadi — ilgari ularni faqat kampaniya dialeri tekshirardi.
Ichki raqamlar (softphone, `3–4` xonali) tekshirilmaydi.

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

**Xatolar:** yuqoridagilar, qo'shimcha `400 SCENARIO_DEFINITION_INVALID` — ssenariy tekshiruvdan
o'tmadi. Har bir topilgan muammo `errors` ro'yxatida alohida qator bo'lib keladi; `message` faqat
umumiy matn (muammolar soni bilan), ro'yxatni o'z ichiga olmaydi.

---

## `POST /api/calls/web-test` — Brauzerdan test qo'ng'irog'i (mikrofon ↔ AI)

Kampaniyani (yoki ssenariyni) telefon va trunk daqiqasisiz, **to'g'ridan-to'g'ri brauzerda** sinash: foydalanuvchi mikrofonga gapiradi, AI javobi dinamikdan eshitiladi. Audio yo'li real qo'ng'iroq bilan bir xil — brauzer WebRTC orqali Asterisk'ga ulanadi, u yog'i odatdagi Stasis → RTP → STT/LLM/TTS pipeline. Qo'ng'iroq `call_attempt` da target'siz (`target_id = null`), telefon `WEB-TEST` bilan yoziladi; kampaniya statistikasi va target holati **o'zgarmaydi**, CRM ga hech narsa yozilmaydi.

Endpoint qo'ng'iroq qilmaydi — u bir martalik **sessiya** yaratadi va brauzer o'zi ulanishi uchun kerak bo'lgan hamma narsani qaytaradi. Sessiya 5 daqiqa ichida terilmasa unutiladi.

**Request Body** (`WebTestCallRequest`) — `campaignId`, `scenarioId`, `definition` dan **ko'pi bilan bittasi**; hech biri berilmasa standart test ssenariysi:

| Maydon | Turi | Izoh |
|---|---|---|
| `campaignId` | number | Kampaniya [agentining](ai-agents.md) to'liq profili bilan: ssenariy, tillar bo'yicha ovozlar, persona, ambient sound, disclosure, DTMF |
| `targetId` | number | Ixtiyoriy, faqat `campaignId` bilan. Shu target faktlari (contextData + CRM + memory) ishlatiladi; berilmasa `voice-agent.dialog.test-context` faktlari |
| `agentVersion` | number | Ixtiyoriy, faqat `campaignId` bilan. Kampaniya agentining qaysi [versiyasi](ai-agents.md#14-versiyalar--draft--publish--rollback) bilan gaplashish: e'lon qilingan raqam yoki `null` — **qoralama** (e'lon qilishdan oldin sinash uchun). Noma'lum raqam `404 AI_AGENT_VERSION_NOT_FOUND` |
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

**Xatolar:** `400 WEB_TEST_SOURCE_INVALID` (bir nechta manba yoki `targetId`/`agentVersion` `campaignId`siz), `400 SCENARIO_DEFINITION_INVALID` (`definition` noto'g'ri; muammolar `errors` ro'yxatida), `404 CAMPAIGN_NOT_FOUND` / `TARGET_NOT_FOUND` / `SCENARIO_NOT_FOUND`
(`scenarioId` boshqa kompaniyaniki bo'lsa ham — test qo'ng'irog'i endi doim chaqiruvchi kompaniya nomidan ishlaydi), `403 COMPANY_SUSPENDED`,
`409 CALL_BALANCE_INSUFFICIENT` (faqat `BILLING_ENFORCE_BALANCE=true` bo'lganda), `409 WEB_TEST_NOT_CONFIGURED` (serverda `ws-url` bo'sh).

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

Nishon navbatga qo'yiladi va dialerning keyingi tik'i (`voice-agent.dialer.tick-seconds`, standart 5 s)
uni birinchi bo'lib oladi — endpoint endi o'zi dispatch sikli ishga tushirmaydi. `campaignId` berilsa,
kampaniya `ACTIVE` bo'lishi shart: aks holda `409 CAMPAIGN_NOT_ACTIVE` (ilgari to'xtatilgan kampaniyaga
ham "queued" deb javob berardi, qo'ng'iroq esa hech qachon chiqmasdi). `campaignId` berilmasa —
kompaniyaning birinchi `ACTIVE` kampaniyasi, topilmasa `404 INSTANT_CALL_CAMPAIGN_NOT_FOUND`.

**Request Body** (`InstantCallRequest`):
```json
{
  "campaignId": 12,
  "phone": "+998901234567",
  "clientName": "Sardor aka",
  "contextData": {
    "orderNumber": "ORD-9981",
    "deliveryAddress": "Toshkent, Yunusobod 4-mavze",
    "orderAmount": 450000,
    "agentOverrides": {
      "firstMessage": "Assalomu alaykum, Sardor aka! Buyurtmangiz bo'yicha qo'ng'iroq qilyapman.",
      "systemPrompt": "Mijoz VIP, yetkazib berish sanasini aniqlashtiring.",
      "variables": { "courierName": "Jasur" }
    }
  }
}
```

**`contextData.agentOverrides`** (ixtiyoriy) — faqat shu bitta qo'ng'iroq uchun agent sozlamasini
almashtiradi; agentning o'zi va uning versiyasi o'zgarmaydi. Kampaniya nishonining `contextData`sida
ham xuddi shunday ishlaydi ([campaigns.md](campaigns.md)). CSV importi tekis ustunlar bilan ishlaydi,
shuning uchun `agentOverrides` faqat JSON orqali (bu endpoint yoki nishon qo'shish API) beriladi.

| Maydon | Ma'nosi |
|---|---|
| `firstMessage` | Agentning ochilish gapi o'rniga aytiladi. `initiationWebhook` o'zining `firstMessage`ini qaytarsa, o'shaniki ustun (u keyinroq so'raladi) |
| `systemPrompt` | Promptga qo'shimcha ko'rsatma: kampaniya `goal`i ortidan qo'shiladi (jami 500 belgigacha). Agent promptini **almashtirmaydi** |
| `variables` | Kalit va oddiy qiymat (matn, son, boolean). Ssenariy `factSchema`sida bo'lmasa ham fakt bo'lib qo'shiladi va promptdagi `{{...}}` o'rniga qo'yiladi, xuddi `initiationWebhook` qaytargan o'zgaruvchilar kabi. E'lon qilingan fakt bilan nomi bir xil bo'lsa, faktning o'z qiymati qoladi. Matnlar prompt uchun xavfsiz tozalanadi |

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

Ayni paytda suhbatda bo'lgan **token kompaniyasining** qo'ng'iroqlari ro'yxatini, jumladan qaysi SIP trunkdan qo'ng'iroq ketayotganini qaytaradi. Boshqa kompaniyaning qo'ng'iroqlari ro'yxatga tushmaydi.

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

`instruction` majburiy va bo'sh bo'lmasligi kerak — aks holda `400 VALIDATION_FAILED`.

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
`extension` — **shu kompaniyaning** SIP akkaunti bor operatorining ichki raqami (`sipExtension`);
boshqa raqam (boshqa kompaniyaniki ham) — `404 OPERATOR_EXTENSION_NOT_FOUND`. Batafsil:
[live.md](live.md#post-apicallslivechannelidtakeover--qongiroqni-tirik-operatorga-olish-human-takeover).

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

Har biri faqat token kompaniyasining jonli kanali uchun ishlaydi. Kanal yo'q, tugagan yoki
boshqa kompaniyaniki bo'lsa — `409 CALL_NOT_ACTIVE` (uchala holat bir xil, kanal mavjudligi
oshkor qilinmaydi). `/api/calls/live/{channelId}/whisper|takeover` ham shunday.

- `POST /api/calls/{channelId}/play?file=greeting.wav` — Jonli kanalga audio fayl eshittirish.
- `POST /api/calls/{channelId}/say?text=...&language=uz-UZ&voice=nigora` — Jonli kanalga matnni TTS qilib o'qib berish.
- `POST /api/calls/{channelId}/hangup` — Jonli kanalni majburiy to'xtatish (go'shakni qo'yish).
- `POST /api/calls/{channelId}/transfer` — Operatorga / navbatga uzatish (body'siz yoki manzil bilan — pastda [Operator qo'ng'iroq boshqaruvi](#operator-qongiroq-boshqaruvi)).
- `GET /api/calls/{channelId}/listen` — Jonli qo'ng'iroq audio oqimini WAV formatida real-vaqtda tinglash (`audio/wav`). `Authorization: Bearer` sarlavhasi yoki — `<audio src>` sarlavha qo'ya olmagani uchun — URL'da bir martalik `?ticket=` ([`POST /api/auth/stream-ticket`](auth.md#post-apiauthstream-ticket--oqim-uchun-bir-martalik-ticket)); eskicha `?token=` / `?access_token=` — deprecated.

---

## Operator qo'ng'iroq boshqaruvi

Operator ulangan jonli qo'ng'iroq ustidagi amallar: uzatish, hold, konsultatsiya (attended
transfer), supervayzer monitoringi va click-to-call.

**`{channelId}` — doim mijoz (caller) kanali**, operatorniki emas. Operator ekranida u
`CALL_OFFER` SSE hodisasidagi `channelId` ([operators.md](operators.md), `GET /api/operators/me/events`),
jonli ro'yxatda — `LiveCallRow.channelId`. Boshqa kompaniyaning kanali `409` bilan rad etiladi
(`CALL_NOT_WITH_OPERATOR` yoki `CALL_NOT_ACTIVE`).

Transfer, hold va konsultatsiya endpointlari bitta javob shaklini qaytaradi — `TransferResponse`:

```json
{
  "accept": true,
  "data": { "channelId": "1710000000.12", "status": "TRANSFERRING" },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

| Endpoint | `status` |
|---|---|
| `POST /{channelId}/transfer` | `TRANSFERRING` |
| `POST /{channelId}/hold` | `ON_HOLD` |
| `POST /{channelId}/unhold` | `RESUMED` |
| `POST /{channelId}/consult` | `CONSULTING` |
| `POST /{channelId}/consult/complete` | `TRANSFERRED` |
| `POST /{channelId}/consult/cancel` | `RESUMED` |

`status` faqat buyruq qabul qilinganini bildiradi. Keyin nima bo'lgani (hamkasb ko'tardimi,
navbatda kim oldi) qo'ng'iroq timeline'ida ko'rinadi:
`GET /api/reports/calls/{callId}/events` ([reports.md](reports.md#get-apireportscallscallidevents--qongiroq-timelinei)).

### Uzatish manzili — `TransferCallRequest`

`transfer` va `consult` body'si.

| Maydon | Turi | Izoh |
|---|---|---|
| `type` | `TransferTargetType` | `QUEUE`, `USER` yoki `EXTERNAL` |
| `targetId` | number | `QUEUE` — navbat id'si ([call-queues.md](call-queues.md)); `USER` — hamkasb foydalanuvchi id'si. `EXTERNAL` da e'tiborga olinmaydi |
| `number` | string | Faqat `EXTERNAL` uchun: telefon raqami (3–15 raqam, boshida `+` bo'lishi mumkin) |

| `type` | Nima bo'ladi | Kerakli maydon | Tekshiruv xatolari |
|---|---|---|---|
| `QUEUE` | Mijoz boshqa operator navbatiga o'tadi va o'sha yerda kutadi | `targetId` | `404 CALL_QUEUE_NOT_FOUND` (navbat yo'q yoki boshqa kompaniyaniki) |
| `USER` | Bitta hamkasbning SIP telefoni jiringlaydi | `targetId` | `404 USER_NOT_FOUND`; `409 OPERATOR_NOT_REACHABLE` — hamkasbda SIP akkaunt yo'q |
| `EXTERNAL` | Raqam trunk orqali teriladi | `number` | `400 PHONE_INVALID` |

`QUEUE`/`USER` da `targetId` berilmasa — `400 CALL_TRANSFER_TARGET_ID_REQUIRED`. `USER` uchun faqat
SIP akkaunt borligi tekshiriladi, hamkasbning holati (`AVAILABLE`/`BREAK`/`OFFLINE`) tekshirilmaydi.

---

### `POST /api/calls/{channelId}/transfer` — Uzatish (blind transfer)

Huquq: **LIVE_EDIT**. Body **ixtiyoriy**.

**Body'siz (yoki `type: null`) — eski xatti-harakat: AI → operator (takeover).** Qayerga
uzatishni agent sozlamasi hal qiladi: agentda `transferQueueId` bo'lsa, qo'ng'iroq o'sha navbatga
tushadi (kutish musiqasi, navbatdagi o'rni, operatorga whisper). Bo'lmasa agentning
`transferPhoneNumber` endpointi, u ham bo'lmasa serverdagi `voice-agent.operator.endpoint`
jiringlaydi. **Hech biri sozlanmagan bo'lsa qo'ng'iroq uziladi.** Kanalda shu kompaniyaning
jonli AI suhbati bo'lmasa — `409 CALL_NOT_ACTIVE`. Aks holda javob doim `200 TRANSFERRING`:
uzatish keyin amalga oshmasa, kanal uziladi. Operator ulangan qo'ng'iroqni uzatayotganda doim
body bering.

**Body bilan — operator qo'ng'iroqni boshqa joyga uzatadi:**

- **Qo'ng'iroqda operator ulangan bo'lsa**, operatorning liniyasi uziladi va mijoz ko'rsatilgan
  manzilga o'tadi. Timeline'ga `TRANSFER` hodisasi yoziladi (`mode: "BLIND"`).
- **Operator hali ulanmagan bo'lsa** (masalan, AI gaplashyapti), faqat `QUEUE` ruxsat etiladi:
  AI gapini tugatadi (8 soniyagacha jimlik kutiladi), keyin mijoz navbatga o'tadi. `USER` yoki
  `EXTERNAL` bo'lsa — `409 CALL_NOT_WITH_OPERATOR`.

```json
{ "type": "QUEUE", "targetId": 3 }
```

```json
{ "type": "USER", "targetId": 17 }
```

```json
{ "type": "EXTERNAL", "number": "+998901234567" }
```

**Xatolar (body bilan):** `400 CALL_TRANSFER_TYPE_REQUIRED` (`type` yo'q), `400 CALL_TRANSFER_TARGET_ID_REQUIRED` (`targetId` yo'q), `400 PHONE_INVALID`,
`404 CALL_QUEUE_NOT_FOUND`, `404 USER_NOT_FOUND`, `409 OPERATOR_NOT_REACHABLE` (hamkasbda SIP
akkaunt yo'q), `409 CALL_NOT_WITH_OPERATOR` (operator ulanmagan va manzil `QUEUE` emas),
`409 CALL_NOT_ACTIVE` (operatorsiz `QUEUE` uzatish, lekin kanal jonli emas yoki boshqa
kompaniyaniki).

---

### `POST /api/calls/{channelId}/hold` va `POST /api/calls/{channelId}/unhold`

Huquq: **LIVE_EDIT**. Body yo'q.

- **hold** — operator qo'ng'iroq bridge'idan chiqariladi (liniyasi uzilmaydi, faqat jim qoladi),
  mijoz kutish musiqasini eshitadi. Timeline: `HOLD`.
- **unhold** — musiqa to'xtaydi, operator yana mijoz bilan ulanadi. Timeline: `UNHOLD`.

`unhold` qo'ng'iroq haqiqatan hold'da ekanini tekshirmaydi — uni faqat `hold` dan keyin chaqiring.

**Xatolar:** `409 CALL_NOT_WITH_OPERATOR` (qo'ng'iroqda operator yo'q yoki boshqa kompaniyaniki),
`502 ARI_BRIDGE_FAILED` (Asterisk bridge amali bajarilmadi).

---

### Konsultatsiya (attended transfer)

Operator mijozni uzatishdan oldin hamkasbi bilan gaplashib oladi. Uch qadam:

| Qadam | Endpoint | Natija |
|---|---|---|
| 1 | `POST /api/calls/{channelId}/consult` | Mijoz hold'ga o'tadi (musiqa). Operator alohida bridge'ga o'tadi, hamkasbning telefoni 30 soniya jiringlaydi. Hamkasb ko'tarsa, u operator bilan gaplashadi — mijoz ularni eshitmaydi |
| 2a | `POST /api/calls/{channelId}/consult/complete` | Hamkasb mijozni oladi va endi qo'ng'iroqning operatori bo'ladi, oldingi operator uziladi |
| 2b | `POST /api/calls/{channelId}/consult/cancel` | Hamkasb uziladi, operator mijozga qaytadi (hold tugaydi) |

Hamkasb 30 soniyada ko'tarmasa yoki o'zi go'shakni qo'ysa, operator **avtomatik** mijozga
qaytariladi. Konsultatsiya paytida operator o'zi uzilsa, hamkasb ham uziladi.

#### `POST /api/calls/{channelId}/consult`

Huquq: **LIVE_EDIT**. Body **majburiy** — `TransferCallRequest`, faqat `USER` yoki `EXTERNAL`
(navbat bilan konsultatsiya qilib bo'lmaydi). `USER` — hamkasbning SIP telefoni jiringlaydi
(caller ID: `"Konsultatsiya"`); `EXTERNAL` — raqam kompaniyaning default trunki orqali teriladi.
Timeline: `TRANSFER` (`mode: "CONSULT"`).

```json
{ "type": "USER", "targetId": 17 }
```

**Xatolar:** `400 MALFORMED_REQUEST_BODY` (body umuman yo'q), `400 CALL_TRANSFER_TYPE_REQUIRED` (`type` yo'q), `400 CALL_CONSULT_TARGET_INVALID`
(`type: "QUEUE"` — konsultatsiya faqat odam yoki raqam bilan), `400 CALL_TRANSFER_TARGET_ID_REQUIRED` (`targetId` yo'q), `400 PHONE_INVALID`, `404 USER_NOT_FOUND`,
`409 OPERATOR_NOT_REACHABLE`, `409 CALL_NOT_WITH_OPERATOR`, `409 CALL_CONSULT_ALREADY_ACTIVE`
(bu qo'ng'iroqda konsultatsiya allaqachon ketyapti), `502 ARI_BRIDGE_FAILED`,
`502 ARI_ORIGINATE_FAILED` / `ARI_NOT_CONNECTED` (hamkasbni terib bo'lmadi). Har qanday 502 da
operator mijozga qaytariladi.

#### `POST /api/calls/{channelId}/consult/complete`

Huquq: **LIVE_EDIT**. Body yo'q. Hamkasb ko'targandan keyingina ishlaydi. Timeline: `TRANSFER`
(`mode: "ATTENDED"`).

**Xatolar:** `409 CALL_NOT_WITH_OPERATOR`, `409 CALL_CONSULT_NOT_ACTIVE` (konsultatsiya yo'q
yoki hamkasb hali ko'tarmagan), `502 ARI_BRIDGE_FAILED`.

#### `POST /api/calls/{channelId}/consult/cancel`

Huquq: **LIVE_EDIT**. Body yo'q. Hamkasb hali ko'tarmagan bo'lsa ham bekor qilsa bo'ladi —
uning jiringlayotgan liniyasi ham uziladi. Operator mijozga qaytariladi.

**Xatolar:** `409 CALL_NOT_WITH_OPERATOR`, `409 CALL_CONSULT_NOT_ACTIVE`, `502 ARI_BRIDGE_FAILED`.

---

### `POST /api/calls/{channelId}/monitor` — Supervayzer: tinglash / pichirlash / aralashish

Huquq: **CALL_MONITOR** (tizim rollaridan `SUPERVISOR`, `ADMIN`, `DEVELOPER` da bor).

Brauzerga audio kelmaydi — so'rovdan keyin **supervayzerning o'z softfoni jiringlaydi**. Bu joriy
foydalanuvchining SIP akkaunti ([operators.md](operators.md)); telefon 30 soniya jiringlaydi,
caller ID: `"Monitoring" <listen|whisper|barge>`. Supervayzer ko'targach, qo'ng'iroqqa tanlangan
rejimda qo'shiladi. Kuzatuvni tugatish uchun supervayzer go'shakni qo'yadi. Kuzatilayotgan
qo'ng'iroq tugasa, supervayzer ham avtomatik uziladi.

**Request Body** (`MonitorCallRequest`):

```json
{ "mode": "WHISPER" }
```

| `mode` (`MonitorMode`) | Supervayzer nimani eshitadi | Uni kim eshitadi | Qachon mumkin |
|---|---|---|---|
| `LISTEN` | Ikkala tomonni | Hech kim | Har qanday jonli qo'ng'iroq (AI yoki operator) |
| `WHISPER` | Ikkala tomonni | Faqat operator (coaching) | Faqat operator ulangan qo'ng'iroq |
| `BARGE` | Hammani | Hamma — suhbatning to'liq qatnashchisi | Har qanday jonli qo'ng'iroq |

**Response** (`MonitorCallResponse`):

```json
{
  "accept": true,
  "data": {
    "channelId": "1710000000.12",
    "supervisorChannelId": "1710000000.57",
    "mode": "WHISPER"
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

| Maydon | Izoh |
|---|---|
| `channelId` | Kuzatilayotgan mijoz kanali |
| `supervisorChannelId` | Supervayzerning jiringlayotgan telefon kanali |
| `mode` | So'ralgan rejim |

Timeline'ga `MONITOR` hodisasi (`userId`, `mode`) telefon jiringlagan paytda yoziladi —
supervayzer ko'tarmasa ham.

**Xatolar:** `400 VALIDATION_FAILED` (`mode` yo'q), `403 NO_USER_SESSION` (`X-Api-Key` bilan
chaqirilgan — foydalanuvchi yo'q), `404 OPERATOR_SIP_ACCOUNT_NOT_FOUND` (joriy foydalanuvchida SIP
akkaunt yo'q), `409 CALL_NOT_ACTIVE` (kanal jonli emas yoki boshqa kompaniyaniki),
`409 CALL_NOT_WITH_OPERATOR` (`WHISPER`, lekin qo'ng'iroqda operator yo'q),
`502 ARI_ORIGINATE_FAILED` / `ARI_NOT_CONNECTED`. Supervayzer ko'targandan keyin ulab bo'lmasa,
javob allaqachon qaytgan bo'ladi — bunda uning liniyasi shunchaki uziladi.

---

### `POST /api/calls/click-to-call` — Operator softfonidan qo'ng'iroq

Huquq: **OPERATOR_EDIT**.

Oqim ("operator-first"):

1. Joriy foydalanuvchining softfoni jiringlaydi (30 soniya, caller ID: `"Qayta qo'ng'iroq" <raqam>`).
2. Operator ko'tarsa, unga "mijozga qo'ng'iroq qilinmoqda" degan ibora o'qiladi, keyin mijoz
   raqami kompaniyaning default trunki orqali teriladi (45 soniya). Qo'ng'iroq `call_attempt`
   ga (`MANUAL` target) shu paytdayoq yoziladi — mijoz ko'tarmasa ham.
3. Mijoz ko'tarsa, operator bilan ulanadi va suhbat yoziladi (recording); qo'ng'iroq
   hisobotlarda shu operator nomi bilan chiqadi.
4. Mijoz javob bermasa, operator "mijoz javob bermadi" iborasini eshitadi va liniya uziladi.
5. Operator ko'tarmasa, mijozga umuman qo'ng'iroq qilinmaydi.

O'tkazib yuborilgan qo'ng'iroqqa qayta qo'ng'iroq ham xuddi shu mexanizmda ishlaydi:
`POST /api/missed-calls/{id}/call-back` ([missed-calls.md](missed-calls.md)).

**Request Body** (`ClickToCallRequest`):

```json
{ "phone": "+998901234567" }
```

| Maydon | Turi | Izoh |
|---|---|---|
| `phone` | string | Majburiy. `^\+?[1-9]\d{8,14}$` — 9–15 raqam, boshida `+` bo'lishi mumkin |

**Response** (`ClickToCallResponse`):

```json
{
  "accept": true,
  "data": {
    "operatorChannelId": "1710000000.61",
    "phone": "+998901234567"
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

| Maydon | Izoh |
|---|---|
| `operatorChannelId` | Operator telefonini jiringlatayotgan kanal |
| `phone` | So'rovdagi raqam, qanday yuborilgan bo'lsa shunday |

**Xatolar:** `400 VALIDATION_FAILED` (`phone` bo'sh yoki formati noto'g'ri), `403 NO_USER_SESSION`,
`409 OPERATOR_NOT_REACHABLE` (joriy foydalanuvchida SIP akkaunt yo'q), `409 OPERATOR_ON_CALL`
(operator hozir `RINGING` yoki `ON_CALL` holatida), `502 ARI_ORIGINATE_FAILED` /
`ARI_NOT_CONNECTED`. Mijoz tomonidagi muvaffaqiyatsizlik (band, trunk xatosi) keyin asinxron
yuz beradi va javobga ta'sir qilmaydi.
