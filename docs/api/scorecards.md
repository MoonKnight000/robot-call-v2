# Baholash kartasi (Scorecard) API

`uz.murodjon.robotcallv2.quality` · huquq: o'qish — **QUALITY_REVIEW_READ**, mezonlarni sozlash —
**QUALITY_REVIEW_EDIT** · [reja](../OPERATOR-DIALER-ANALYTICS-PLAN.md) §2.3

Operator qo'ng'iroqlari kompaniya mezonlari bo'yicha avtomatik baholanadi: "Salomlashdi",
"Mijozni tasdiqladi", "Chegirma va'da qilmadi". Ro'yxat **kompaniya bo'yicha bitta** va
operator ishtirok etgan har bir qo'ng'iroqqa qo'llanadi (navbat, callback, softfon, preview).

Qo'ng'iroq tugagach, LLM transkriptni ([reports.md](reports.md) — `OPERATOR` / `CLIENT` qatorlari)
o'qiydi va har bir yoqilgan mezonga javob beradi:

| `answer` | Ma'nosi |
|---|---|
| `YES` | Operator mezonni bajardi |
| `NO` | Mezon shu qo'ng'iroqqa tegishli edi, lekin bajarilmadi |
| `NOT_APPLICABLE` | Qo'ng'iroqda mezonga tegishli vaziyat bo'lmadi |

**Ball (0–100)** = `YES` javoblar og'irligi yig'indisi / (`YES` + `NO`) og'irligi yig'indisi × 100,
butungacha yaxlitlanadi. `NOT_APPLICABLE` hisobga kirmaydi. Hech bir mezon tegishli bo'lmasa —
`score: null`.

Baholash AI qo'ng'iroqlar sifat hakami bilan bir xil sozlamada ishlaydi (`QUALITY_ENABLED`,
`QUALITY_SAMPLE_RATE`, `QUALITY_MODEL`). Mezon yo'q, transkript yo'q yoki LLM ishlamasa —
qo'ng'iroq baholanmaydi.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun [README.md](README.md)ga qarang.

---

## `ScorecardCriterion` (javob)

```json
{
  "id": 1,
  "companyId": 1,
  "name": "Salomlashish",
  "instruction": "Operator suhbat boshida salomlashdimi va o'zini hamda kompaniyani tanishtirdimi",
  "weight": 2,
  "enabled": true,
  "createdAt": "2026-10-06T10:00:00Z"
}
```

| Maydon | Izoh |
|---|---|
| `instruction` | LLM'ga nimani tekshirish kerakligi — kompaniyaning o'z so'zlari bilan |
| `weight` | Ballga ta'siri, 1–10 |
| `enabled` | O'chirilgan mezon bo'yicha yangi qo'ng'iroqlar baholanmaydi; avval baholanganlarda qoladi |

---

## `POST /api/scorecard-criteria` — Mezon yaratish · `QUALITY_REVIEW_EDIT`

Body (`SaveScorecardCriterionRequest`):

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `name` | `string` (≤100) | ✅ | Ko'rinadigan nom |
| `instruction` | `string` (≤1000) | ✅ | LLM uchun ko'rsatma |
| `weight` | `number` (1–10) | ✅ | Og'irlik |
| `enabled` | `boolean` | ❌ | Standart `true` |

Javob — `ScorecardCriterion`.

## `PUT /api/scorecard-criteria/{id}` — Tahrirlash · `QUALITY_REVIEW_EDIT`

Body — yaratishdagi bilan bir xil, to'liq almashtiradi. Avval baholangan qo'ng'iroqlar
o'zgarmaydi: ularda mezon nomi va og'irligi o'sha paytdagidek saqlangan. Topilmasa yoki boshqa
kompaniyaniki bo'lsa — `404 SCORECARD_CRITERION_NOT_FOUND`.

## `DELETE /api/scorecard-criteria/{id}` — O'chirish · `QUALITY_REVIEW_EDIT`

Avval baholangan qo'ng'iroqlardagi javoblar qoladi (`criterionId` `null` bo'ladi). Vaqtincha
to'xtatish uchun `enabled: false` qiling.

## `GET /api/scorecard-criteria/{id}` — Bitta mezon · `QUALITY_REVIEW_READ`

## `POST /api/scorecard-criteria/list` — Ro'yxat · `QUALITY_REVIEW_READ`

Body — `ScorecardCriterionFilter`:

```json
{ "page": 0, "size": 50, "orders": { "ID": "ASC" }, "enabled": true }
```

`enabled`: `true` — hozir qo'llanadiganlar, `false` — o'chirilganlar, berilmasa — hammasi.
Saralanadigan ustunlar: `ID`, `NAME`, `WEIGHT`, `CREATED_AT`; standart `ID ASC`.
Javob — `PageableData<ScorecardCriterion>`.

---

## `GET /api/calls/{callId}/scorecard` — Qo'ng'iroq bahosi · `QUALITY_REVIEW_READ`

Javob (`CallScorecard`):

```json
{
  "callId": 42,
  "companyId": 1,
  "operatorUserId": 9,
  "score": 100,
  "judgedScore": 75,
  "sentiment": "NEGATIVE",
  "topics": ["to'lov muddati", "chegirma"],
  "needsReview": true,
  "reviewStatus": "CORRECTED",
  "reviewNote": "Operator mijozni tasdiqlagan, transkriptda tushib qolgan",
  "reviewedBy": 5,
  "reviewedAt": "2026-10-06T11:00:00Z",
  "answers": [
    {
      "id": 101,
      "criterionId": 1,
      "criterionName": "Salomlashish",
      "weight": 3,
      "answer": "YES",
      "judgedAnswer": "YES",
      "evidence": "Assalomu alaykum, Uysot kompaniyasidan Dilnoza"
    },
    {
      "id": 102,
      "criterionId": 2,
      "criterionName": "Mijozni tasdiqlash",
      "weight": 1,
      "answer": "YES",
      "judgedAnswer": "NO",
      "evidence": null
    }
  ],
  "promptTokens": 1850,
  "completionTokens": 240,
  "cachedTokens": 0,
  "createdAt": "2026-10-06T10:06:00Z"
}
```

| Maydon | Izoh |
|---|---|
| `score` | Hozir amaldagi ball — supervisor tuzatgan bo'lsa, tuzatilgan javoblardan qayta hisoblangan |
| `judgedScore` | LLM bergan ball; tuzatishdan keyin ham o'zgarmaydi (hakam qanchalik to'g'riligini ko'rish uchun) |
| `sentiment` | Mijoz kayfiyati LLM o'qishicha: `POSITIVE` / `NEUTRAL` / `NEGATIVE` / `HOSTILE`; aytmasa `null` |
| `topics` | Qo'ng'iroq nima haqida bo'lgani: LLM qo'ygan 1–3 ta qisqa teg (o'zbekcha, kichik harflarda); bo'lmasa `[]`. Hisobot — [reports.md](reports.md) §6 |
| `needsReview` | Qo'ng'iroq tekshiruvga tushganmi — ball kompaniya chegarasidan past (`scorecardReviewThreshold`, [settings.md](settings.md)) yoki `sentiment` `NEGATIVE`/`HOSTILE` |
| `reviewStatus` | `PENDING` — tekshiruv kutmoqda; `CONFIRMED` — supervisor LLM bilan rozi; `CORRECTED` — kamida bitta javob tuzatilgan; `null` — tekshiruvga tushmagan va hech kim ko'rmagan |
| `reviewNote` / `reviewedBy` / `reviewedAt` | Oxirgi tekshiruv |
| `answers[].id` | Tuzatish yuborishda shu id ishlatiladi |
| `answers[].answer` | Amaldagi javob; `judgedAnswer` — LLM'niki |
| `answers[].criterionName` / `weight` | Baholash paytidagi qiymat (mezon keyin o'zgarsa ham) |
| `answers[].evidence` | Javob tayangan gap, transkriptdan (≤500 belgi); `NOT_APPLICABLE` da va dalil bo'lmasa `null`. Mijoz so'zlari bo'lgani uchun **transkript bilan birga o'chadi** (retention) — undan keyin `null` |
| `promptTokens` / `completionTokens` / `cachedTokens` | Baholash narxi — qo'ng'iroqqa tahlil to'lovi sifatida yoziladi ([billing.md](billing.md)) |

**Xatolar:**

| Status | `messageCode` | Qachon |
|---|---|---|
| `404` | `CALL_SCORECARD_NOT_FOUND` | Qo'ng'iroq baholanmagan (operatorsiz, mezon yo'q, LLM o'chirilgan yoki sample'ga tushmagan) yoki boshqa kompaniyaniki |

---

## Tekshiruv (review inbox)

Ball chegaradan past yoki mijoz norozi (`NEGATIVE` / `HOSTILE`) bo'lsa, qo'ng'iroq
`reviewStatus: PENDING` bilan inbox'ga tushadi va kompaniyaga `SCORECARD_REVIEW` bildirishnomasi
yuboriladi ([notifications.md](notifications.md)). Supervisor yozuv va transkriptni
`GET /api/reports/calls/{callId}` dan oladi, bahoni shu yerda tasdiqlaydi yoki tuzatadi.

### `POST /api/call-scorecards/list` — Baholangan qo'ng'iroqlar · `QUALITY_REVIEW_READ`

(`/filter` ham ishlaydi.) Body — `CallScorecardFilter`:

```json
{ "page": 0, "size": 20, "orders": { "CREATED_AT": "DESC" }, "status": "PENDING", "operatorUserId": 9,
  "from": "2026-10-01T00:00:00Z", "to": "2026-10-08T00:00:00Z" }
```

| Maydon | Izoh |
|---|---|
| `status` | `PENDING` — inbox; `CONFIRMED` / `CORRECTED` — tekshirilganlar; berilmasa — barcha baholangan qo'ng'iroqlar |
| `operatorUserId` | Faqat shu operatorniki |
| `from` / `to` | Baholangan vaqt bo'yicha, `from` kiradi, `to` kirmaydi |

Saralanadigan ustunlar: `CREATED_AT`, `SCORE`; standart `CREATED_AT DESC`.
Javob — `PageableData<CallScorecard>`.

### `POST /api/call-scorecards/{callId}/review` — Bahoni tekshirish · `QUALITY_REVIEW_EDIT`

Body (`ReviewScorecardRequest`):

```json
{
  "corrections": [ { "answerId": 102, "answer": "YES" } ],
  "note": "Operator mijozni tasdiqlagan, transkriptda tushib qolgan"
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `corrections` | `array` (≤100) | ❌ | LLM'dan boshqacha baholangan javoblar; bo'sh yoki berilmasa — LLM tasdiqlanadi |
| `corrections[].answerId` | `number` | ✅ | `answers[].id` |
| `corrections[].answer` | `YES` / `NO` / `NOT_APPLICABLE` | ✅ | Supervisor javobi |
| `note` | `string` (≤2000) | ❌ | Izoh |

Ball tuzatilgan javoblardan qayta hisoblanadi. Biror javob LLM'nikidan farq qilsa — `CORRECTED`,
aks holda `CONFIRMED`. Tekshiruvga tushmagan qo'ng'iroqni ham tekshirish mumkin; qayta
tekshiruv avvalgisini almashtiradi. Javob — yangilangan `CallScorecard`.

**Xatolar:**

| Status | `messageCode` | Qachon |
|---|---|---|
| `400` | `SCORECARD_ANSWER_UNKNOWN` | `answerId` shu qo'ng'iroq bahosida yo'q |
| `400` | `VALIDATION_FAILED` | `answerId` / `answer` berilmagan, `note` juda uzun |
| `404` | `CALL_SCORECARD_NOT_FOUND` | Qo'ng'iroq baholanmagan yoki boshqa kompaniyaniki |
