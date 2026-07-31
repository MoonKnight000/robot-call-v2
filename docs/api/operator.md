# Operator API

`uz.murodjon.uysotvoice.operator` · rol: **ADMIN**

Faqat o'qish uchun (read-only) — qo'ng'iroq operatorga uzatilgandan keyin
operator ekrani shu endpointni poll qilib mijozning faktlarini, joriy dialog
holatini va shu paytgacha bo'lgan suhbatni ko'radi. Hali ochiq bo'lgan dialog
sessiyasidan jonli o'qiladi — DB emas.

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
  "message": null, "accept": true, "errors": null
}
```

| Maydon | Izoh |
|---|---|
| `debtAmount`/`dueDate` | matn sifatida keladi (aniq sonli/sana turi emas) — to'g'ridan-to'g'ri ko'rsatish uchun |
| `dialogState` | uzatish sodir bo'lgan paytdagi FSM holati |
| `transcript` | butun suhbat, bitta matn ichida `ROLE: matn` qatorlari bilan (strukturaviy massiv emas — agar UI har bir qatorni alohida render qilishi kerak bo'lsa, `\n` bo'yicha split qiling) |

Sessiya hali ochiq bo'lmasa (qo'ng'iroq allaqachon tugagan yoki
`channelId` noto'g'ri) — `404`.
