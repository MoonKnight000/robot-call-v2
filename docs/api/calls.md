# Qo'lda qo'ng'iroq (test/tekshiruv) API

`uz.murodjon.uysotvoice.call` · rol: **ADMIN** (barcha endpoint)

Kampaniyaga bog'liq bo'lmagan, qo'lda ishga tushiriladigan tekshiruv
endpointlari. **Har biri real pul sarflaydi** (trunk daqiqasi, TTS belgilari)
— faqat sinov/diagnostika uchun, ommaviy qo'ng'iroq uchun emas (buning uchun
[campaigns.md](campaigns.md)).

Jonli (hozir davom etayotgan) qo'ng'iroqlar ro'yxati uchun
[live.md](live.md)ga qarang — bu fayldagi `GET /live` o'sha bilan bir xil
ma'lumotni beradi.

Umumiy javob shakli va xatolar uchun [README.md](README.md)ga qarang.

---

## `GET /api/calls/live` — jonli qo'ng'iroqlar

Hozir suhbatda bo'lgan har bir qo'ng'iroq. Batafsil shakl va maydonlar uchun
[live.md](live.md#get-apicallslive)ga qarang (bu ikkala hujjat ham bitta
endpointni tasvirlaydi).

---

## `POST /api/calls?number=...` — qo'ng'iroq boshlash

Query parametr: `number` (majburiy) — набираемый raqam.

**Response** (`CallOriginateResponse`):

```json
{ "data": { "number": "998901234567", "channelId": "PJSIP/trunk-00000012" }, "message": null, "accept": true, "errors": null }
```

Noto'g'ri formatdagi raqam — `400`. `channelId`ni keyingi `play`/`say`
chaqiruvlarida ishlating.

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
