# Platforma: modellar narxi — `/api/platform/model-prices`

Rol: `PLATFORM_ADMIN`. Har bir LLM / realtime model o'z narxida hisoblanishi uchun narxlar ro'yxati
(Gemini, GPT va boshqalarning token narxi har xil). Narxlar **so'mda, ustamasiz** — `billing.yml` dagi
`markup-basis-points` ustidan qo'shiladi, boshqa provayder xarajatlari kabi.

Ro'yxatda yo'q model `billing.yml` dagi umumiy LLM tarifi (`rates.llm`) bilan hisoblanadi.

> Holat (2026-11-06): ro'yxat **model tanlashdagi narx taxmini**da ishlatiladi (`AiModelService` →
> `ModelPricingService`; avval shu ro'yxat, keyin `billing.yml` `model-rates`) va **hisobda**:
> qo'ng'iroq, summary, operator tahlili, qayta tahlil, Copilot, chat va qo'ng'iroq savollari tokenlari
> shu ro'yxat bo'yicha narxlanadi, ro'yxatda yo'q model — umumiy tarifda. Har model qatori `llm_usage`
> jadvaliga yoziladi. Model id provayder javobidan olinadi (bo'lmasa — sozlangan model nomi;
> u ham bo'lmasa — umumiy tarif). Realtime qo'ng'iroq engine modeli (`llmModel`) ro'yxatda
> `realtimePerMinuteUzs` bilan bo'lsa, o'sha daqiqa narxida, aks holda `billing.yml` realtime tarifida
> hisoblanadi; realtime daqiqalari `llm_usage` ga yozilmaydi (u faqat tokenlar uchun).

## Model nomi qanday moslashadi

- Id kichik harfda saqlanadi, Gemini'ning `models/` prefiksi olib tashlanadi.
- Provayder aniqroq nom qaytarsa, u **versiya qo'shimchasi** bo'lsagina shu qatorga tushadi:
  `gpt-4o-mini-2024-07-18` → `gpt-4o-mini`, `gemini-2.5-flash-preview-05-20` → `gemini-2.5-flash`.
  Qo'shimcha raqam, `latest`, `preview` yoki `exp` bilan boshlanishi kerak.
- Boshqa model hech qachon prefiksi bo'yicha narxlanmaydi: faqat `gpt-4o` qatori bo'lsa,
  `gpt-4o-mini` unga **tushmaydi** (umumiy tarif).
- Bir nechtasi mos kelsa — eng uzuni.

## `GET /api/platform/model-prices`

```json
{
  "accept": true,
  "data": [
    {
      "model": "gemini-2.5-flash",
      "inputPerMillionUzs": 3900,
      "outputPerMillionUzs": 32000,
      "cachedPerMillionUzs": 1000,
      "realtimePerMinuteUzs": null,
      "note": "provayder narx sahifasi, 2026-11-06",
      "updatedAt": "2026-11-06T10:00:00Z"
    }
  ],
  "message": null,
  "messageCode": null,
  "errors": null
}
```

(Raqamlar namuna, haqiqiy narx emas.)

## `PUT /api/platform/model-prices/{model}` — qo'shish yoki almashtirish

Body (`UpsertModelPriceRequest`):

| Maydon | Turi | Majburiy | Izoh |
|---|---|---|---|
| `inputPerMillionUzs` | number ≥ 0 | ha | 1M kiruvchi (prompt) token, keshlanmagan |
| `outputPerMillionUzs` | number ≥ 0 | ha | 1M chiquvchi (completion) token, thinking ham shu narxda |
| `cachedPerMillionUzs` | number ≥ 0 | ha | 1M keshdan berilgan prompt token |
| `realtimePerMinuteUzs` | number ≥ 0 | yo'q | Faqat speech-to-speech engine uchun (1 daqiqa); `null` — `billing.yml` realtime tarifi |
| `note` | string ≤ 255 | yo'q | Narx manbai, tekshirilgan sana |

Javob: saqlangan qator (`ModelPriceResponse`, yuqoridagi shakl).
Xatolar: noto'g'ri id — `400 MODEL_PRICE_MODEL_INVALID`; manfiy yoki yetishmagan narx — `400 VALIDATION_FAILED`.
Audit: `MODEL_PRICE_UPSERT`.

## `DELETE /api/platform/model-prices/{model}`

Model umumiy tarifga qaytadi. Javob `200`, `data: null`. Qator yo'q — `404 MODEL_PRICE_NOT_FOUND`.
Audit: `MODEL_PRICE_DELETE`.
