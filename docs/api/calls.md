# Qo'lda qo'ng'iroq (test/tekshiruv) API

`uz.murodjon.uysotvoice.call` · rol: **OPERATOR** (barcha endpoint — ADMIN ham kiradi, rol ierarxiyasi bo'yicha)

Kampaniyaga bog'liq bo'lmagan, qo'lda ishga tushiriladigan tekshiruv
endpointlari. **Har biri real pul sarflaydi** (trunk daqiqasi, TTS belgilari)
— faqat sinov/diagnostika uchun, ommaviy qo'ng'iroq uchun emas (buning uchun
[campaigns.md](campaigns.md)).

Ssenariylarni testlashning barcha usullari (veb simulyator, AI persona benchmark, jonli test qo'ng'iroq) uchun [scenario-testing.md](scenario-testing.md)ga qarang.

Jonli (hozir davom etayotgan) qo'ng'iroqlar ro'yxati uchun
[live.md](live.md)ga qarang — bu fayldagi `GET /live` o'sha bilan bir xil
ma'lumotni beradi.

Umumiy javob shakli va xatolar uchun [README.md](README.md)ga qarang.

---

## 🎧 Ulanish signali (Connection Chime & Gudok)

Qo'ng'iroq ulanishi bilan (`StasisStart` / go'shak ko'tarilganda), bot darhol gap boshlab yubormasdan, xuddi Telegram yoki zamonaviy VoIP ilovalaridagi kabi tabiiy ulanish ohangini (melodic connection chime C5→E5) chalar va suhbatga tayyorgarlik hissini beradi.

---

## `GET /api/calls/live` — jonli qo'ng'iroqlar

Hozir suhbatda bo'lgan har bir qo'ng'iroq. Batafsil shakl va maydonlar uchun
[live.md](live.md#get-apicallslive)ga qarang (bu ikkala hujjat ham bitta
endpointni tasvirlaydi).

---

## `POST /api/calls?number=...&scenarioId=...` — qo'ng'iroq boshlash

| Parametr | Turi | Izoh |
|---|---|---|
| `number` | query, majburiy | набираемый raqam |
| `scenarioId` | query, ixtiyoriy | ssenariyni kampaniyasiz sinash uchun (ROADMAP A.3/A.4 "sinov rejimi") — `GET/POST /api/scenarios/list`dagi `id`; noma'lum bo'lsa `404`. Berilmasa `voice-agent.dialog.test-context.scenario-key` (standart: `debt-collection`) ishlatiladi |

**Response** (`CallOriginateResponse`):

```json
{ "data": { "number": "998901234567", "channelId": "PJSIP/trunk-00000012" }, "message": null, "messageCode": null, "accept": true, "errors": null }
```

Noto'g'ri formatdagi raqam — `400`. `channelId`ni keyingi `play`/`say`
chaqiruvlarida ishlating.

---

## `POST /api/calls/test?number=...` — saqlanmagan ssenariy qoralamasini sinash {#post-apicallstest}

Ssenariy muharriridagi joriy (hali saqlanmagan) qoralamani sinov qo'ng'irog'i
bilan tekshirish (backend-uchun-talablar.md §4) — avval saqlash shart emas.
Body — to'liq `ScenarioDefinition` (formadagi joriy holat, [scenarios.md](scenarios.md)ga
qarang), xuddi `POST /api/scenarios/validate` qabul qiladigan shakl bilan bir xil.

| Parametr | Turi | Izoh |
|---|---|---|
| `number` | query, majburiy | набираемый raqam |
| body | `ScenarioDefinition` | to'liq ssenariy tanasi — hech narsa saqlanmaydi |

Backend avval `ScenarioService.validate` orqali tekshiradi — noto'g'ri
definitsiya `400` (`POST /api/scenarios/validate` bilan bir xil xato shakli).
To'g'ri bo'lsa, oddiy `POST /api/calls?number=...` kabi qo'ng'iroq boshlaydi,
faqat mavjud `scenarioId` o'rniga shu vaqtinchalik ssenariy bilan.

**Response** (`CallOriginateResponse`) — yuqoridagi `POST /api/calls` bilan bir xil shakl.

---

## `POST /api/calls/{channelId}/play?file=...` — WAV o'ynatish

Faylni qo'ng'iroqdagi tomonga RTP orqali o'ynatadi.

| Parametr | Turi | Izoh |
|---|---|---|
| `channelId` | path | `POST /api/calls`dan olingan yoki `GET /api/calls/live`dagi id |
| `file` | query, majburiy | fayl nomi/yo'li; ruxsatsiz (whitelist tashqarisidagi) yo'l `400` bilan rad etiladi |

**Response** (`PlayResponse`):

```json
{ "channelId": "PJSIP/trunk-00000012", "file": "greeting.wav", "status": "playing" }
```

---

## `POST /api/calls/{channelId}/say?text=...&language=...&voice=...` — matnni ovozga aylantirib o'qish

| Parametr | Turi | Izoh |
|---|---|---|
| `channelId` | path | — |
| `text` | query, majburiy | o'qiladigan matn; bo'sh/juda uzun bo'lsa `400` |
| `language` | query, ixtiyoriy | BCP-47, masalan `uz-UZ` |
| `voice` | query, ixtiyoriy | `GET /api/tts/voices` katalogidagi `id` — [voices.md](voices.md)ga qarang. Kampaniyaga qo'yishdan oldin ovozni shu yerda eshitib ko'rish mumkin |

**Response** (`SayResponse`):

```json
{ "channelId": "PJSIP/trunk-00000012", "status": "speaking" }
```

---

## `POST /api/calls/{channelId}/hangup` — qo'ng'iroqni majburan tugatish

Jonli monitoring paneli "Tugatish" tugmasi (§10.3) — kanalni darhol yopadi,
suhbat qay holatda bo'lishidan qat'i nazar. Audit jurnaliga
`CALL_HANGUP_MANUAL` sifatida yoziladi ([reports.md](reports.md#audit-log)).
Mavjud bo'lmagan/allaqachon tugagan `channelId` uchun ham xatosiz `200`
qaytadi (Asterisk darajasida idempotent).

**Response** (`HangupResponse`):

```json
{ "channelId": "PJSIP/trunk-00000012", "status": "HUNG_UP" }
```

---

## `POST /api/calls/{channelId}/transfer` — operatorga uzatish

Jonli monitoring paneli "Operatorga uzatish" tugmasi (§10.3, §11.6) — kanalni
`voice-agent.operator.*` da sozlangan ichki SIP extension'ga bog'laydi (bridge),
xuddi dialog o'zi eskalatsiya qilganda ishlatadigan yo'l bilan. Operator
transferi o'chirilgan yoki sozlanmagan bo'lsa (yoki kanal endi mavjud
bo'lmasa) — kanal shunchaki tugatiladi. Audit jurnaliga `CALL_TRANSFER_MANUAL`
sifatida yoziladi.

**Response** (`TransferResponse`):

```json
{ "channelId": "PJSIP/trunk-00000012", "status": "TRANSFERRING" }
```

---

## `GET /api/calls/{channelId}/listen` — jonli tinglash {#get-apicallschannelidlisten}

Jonli monitoring paneli "Tinglash" tugmasi (§10.3) — mijoz va bot ovozi
mikslangan holda, uzluksiz `audio/wav` oqimi sifatida qaytadi (`ResponseData`
bilan **o'ralmaydi** — fayl yuklab olish kabi xom audio tana). Frontendda
to'g'ridan-to'g'ri `<audio autoplay src="/api/calls/{channelId}/listen">`ga
beriladi.

Bir nechta operator bitta kanalni bir vaqtda tinglashi mumkin — har biri
alohida oqim oladi.
