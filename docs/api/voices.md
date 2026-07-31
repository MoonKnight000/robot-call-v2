# TTS ovozlar katalogi

`uz.murodjon.uysotvoice.voice` · rol: **ADMIN**

Kampaniya qaysi ovoz bilan yaratilishi mumkinligining katalogi. Kampaniya
formasi ovoz tanlagichini shu yerdan to'ldiradi — shuning uchun operator
ko'radigan variantlar aynan `POST /api/campaigns`ning `ttsVoice` maydoni qabul
qiladigan id'larning o'zi.

Umumiy javob shakli va xatolar uchun [README.md](README.md)ga qarang.

---

## `GET /api/tts/voices?language=...` — ovozlar ro'yxati

Query parametr: `language` (ixtiyoriy) — BCP-47 filtr (masalan `uz-UZ`); forma
kampaniya tilini allaqachon bilsa, faqat shu tilda gapira oladigan ovozlarni
ko'rsatish uchun.

**Response** — `List<TtsVoice>`:

```json
{
  "data": [
    { "id": "nigora", "provider": "yandex", "language": "uz-UZ", "name": "uz_UZ_nigora", "label": "Nigora (ayol, o'zbekcha)" }
  ],
  "message": null, "accept": true, "errors": null
}
```

| Maydon | Izoh |
|---|---|
| `id` | kampaniyada saqlanadigan barqaror id (`CreateCampaignRequest.ttsVoice`ga shu qiymat yuboriladi) |
| `provider` | `yandex` yoki `google` — ovozni qaysi provayder gapiradi |
| `language` | BCP-47; boshqa tildagi qo'ng'iroq bu ovozni e'tiborsiz qoldirib standart marshrutlashga qaytadi (masalan ruscha ovoz o'zbekcha matn o'qishi standart ovozdan yomonroq bo'lgani uchun) |
| `name` | provayder tomonidagi ovoz nomi (sintez so'roviga yuboriladi) |
| `label` | UI'da ko'rsatiladigan inson-o'qiy oladigan nom |
