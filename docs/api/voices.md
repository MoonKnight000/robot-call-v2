# TTS ovozlar katalogi

`uz.murodjon.uysotvoice.voice` · rol: **ADMIN**

Kampaniya qaysi ovoz bilan yaratilishi mumkinligining katalogi — `tts_voice`
jadvalida saqlanadi (migration bilan seed qilinadi), config fayl emas. Kampaniya
formasi ovoz tanlagichini shu yerdan to'ldiradi — shuning uchun operator
ko'radigan variantlar aynan `POST /api/campaigns`ning `ttsVoice` maydoni qabul
qiladigan id'larning o'zi. Hozircha faqat o'qish uchun (`GET`) — yaratish/
o'chirish endpoint yo'q.

`id` va `name` alohida ustunlar — shuning uchun bitta provayder ovozi bir nechta
katalog qatori bo'lib turishi mumkin, faqat `role` bilan farq qiladi (masalan
`id: "yulduz-whisper"` → `name: "yulduz"`, `role: "whisper"`). Operator uchun bu
ikkita tanlov, provayder uchun bitta ovozning ikki uslubi.

Umumiy javob shakli va xatolar uchun [README.md](README.md)ga qarang.

---

## `GET /api/tts/voices?language=...` — ovozlar ro'yxati

Query parametr: `language` (ixtiyoriy) — BCP-47 filtr (masalan `uz-UZ`); forma
kampaniya tilini allaqachon bilsa, faqat shu tilda gapira oladigan ovozlarni
ko'rsatish uchun.

Javob shu build'da **yoqilgan** (kredensiali sozlangan, shuning uchun Spring
kontekstiga registratsiya bo'lgan) provayderlarga tegishli ovozlar bilan
cheklanadi — kredensiali yo'q provayderning ovozi umuman qaytmaydi, chunki
`TtsRouter` baribir uni e'tiborsiz qoldirib standart provayderning o'z ovozida
gapiradi (`settings.md`). Bu **kompaniyaning `engine_config.ttsProvider`
tanlovidan mustaqil** — `TtsRouter` tanlangan ovozni to'g'ridan-to'g'ri o'zining
provayderi orqali gapiradi, shuning uchun bitta kampaniya turli provayderlarning
ovozlarini aralashtirib ishlata oladi (masalan bitta target Yandex `nigora`da,
boshqasi Aisha `gulnoza-cheerful`da). Masalan Aisha uchun kredensial sozlangan
bo'lsa, `gulnoza-neutral`, `gulnoza-cheerful`, `gulnoza-happy`, `gulnoza-sad`
qatorlari ham shu ro'yxatda chiqadi — qaysi provayder ekanidan qat'i nazar.

**Response** — `List<TtsVoice>`:

```json
{
  "data": [
    { "id": "nigora", "provider": "yandex", "language": "uz-UZ", "name": "nigora", "label": "Nigora — o'zbek, ayol", "role": null },
    { "id": "zamira", "provider": "yandex", "language": "uz-UZ", "name": "zamira", "label": "Zamira — o'zbek, ayol", "role": null },
    { "id": "yulduz", "provider": "yandex", "language": "uz-UZ", "name": "yulduz", "label": "Yulduz — o'zbek, ayol", "role": null }
  ],
  "message": null, "messageCode": null, "accept": true, "errors": null
}
```

| Maydon | Izoh |
|---|---|
| `id` | kampaniyada saqlanadigan barqaror id (`CreateCampaignRequest.ttsVoice`ga shu qiymat yuboriladi) |
| `provider` | `yandex` yoki `google` — ovozni qaysi provayder gapiradi |
| `language` | BCP-47; boshqa tildagi qo'ng'iroq bu ovozni e'tiborsiz qoldirib standart marshrutlashga qaytadi (masalan ruscha ovoz o'zbekcha matn o'qishi standart ovozdan yomonroq bo'lgani uchun) |
| `name` | provayder tomonidagi ovoz nomi (sintez so'roviga yuboriladi) |
| `label` | UI'da ko'rsatiladigan inson-o'qiy oladigan nom |
| `role` | ovozning gapirish uslubi (Yandex v3 `Hints.role`: `neutral`, `strict`, `friendly`, `whisper`), yoki `null` — yuborilmaydi. Har bir ovozning o'z role'lari bor: `nigora` da umuman yo'q, `zamira` da neutral/strict/friendly, `yulduz` da yana `whisper`. Ovoz qo'llab-quvvatlamaydigan role yuborilsa provayder butun so'rovni rad etadi, shuning uchun bu global sozlama emas, aynan shu qatorning ustuni |
