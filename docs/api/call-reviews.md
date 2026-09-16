# Qo'ng'iroq sifati va review inbox — `/api/call-reviews`

Har bir tugagan AI qo'ng'iroq uchun `call_quality` qatori yoziladi (UNIVERSAL-OPERATOR-PLAN D.1):
sifat hakami (`CallQualityJudge`, ikkinchi LLM) bergan baho va LLM'siz o'qiladigan signallar. Hakam
shubhali deb topgan qo'ng'iroq **review inbox**'ga tushadi. U yerda supervayzer "agent to'g'ri qildi" yoki
"agent xato qildi: <sabab>" deb belgilaydi, kerak bo'lsa shu qo'ng'iroqdan KB javobi yoki test holati
yaratadi (D.2). Odam qo'ygan belgi — o'z-o'zini yaxshilash halqasidagi yagona haqiqat manbai: hakam unga qarab
kalibrlanadi, kunlik insight job (D.3) uni o'qiydi.

Qo'ng'iroqning yozuvi va transkripti bu yerda emas — `GET /api/reports/calls/{callId}` va
`/recording` ([reports.md](reports.md)). Bu endpoint faqat qo'ng'iroqning sifat tomonini qaytaradi.

## Endpointlar

| Metod | URL | Permission | Tavsif |
|---|---|---|---|
| `POST` | `/api/call-reviews/filter` (yoki `/list`) | `QUALITY_REVIEW_READ` | Inbox: belgilangan yoki ko'rib chiqilgan qo'ng'iroqlar |
| `GET` | `/api/call-reviews/{callId}` | `QUALITY_REVIEW_READ` | Bitta qo'ng'iroqning sifat qatori |
| `PUT` | `/api/call-reviews/{callId}` | `QUALITY_REVIEW_EDIT` | Baho: `AGENT_CORRECT` / `AGENT_WRONG` + sabab |
| `POST` | `/api/call-reviews/{callId}/knowledge-item` | `QUALITY_REVIEW_EDIT` **va** `KNOWLEDGE_BASE_EDIT` | Qo'ng'iroq agentiga KB javobi |
| `POST` | `/api/call-reviews/{callId}/test-case` | `QUALITY_REVIEW_EDIT` | Qo'ng'iroq agentiga eval test holati |

`QUALITY_REVIEW_READ` / `QUALITY_REVIEW_EDIT` — `ADMIN` va `SUPERVISOR` rollarida ([roles.md](roles.md)).
API kalitiga berilmaydi. `{callId}` — `call_attempt` id'si (hisobotdagi `callId` bilan bir xil).

---

## Sifat qatori (`CallQuality`)

```json
{
  "id": 812,
  "companyId": 7,
  "callAttemptId": 55,
  "aiAgentId": 42,
  "agentVersion": 3,
  "disposition": "GOAL_NOT_REACHED",
  "score": 40,
  "guardrailViolation": true,
  "languageMismatch": false,
  "talkedOverClient": false,
  "outcomeCorrect": true,
  "goalReached": false,
  "unansweredQuestion": false,
  "toolMisuse": false,
  "note": "Agent katalogda yo'q narxni aytdi",
  "needsReview": true,
  "turnCount": 9,
  "repairCount": 2,
  "bargeInCount": 3,
  "noInputPromptCount": 1,
  "lowConfidenceCount": 0,
  "toolErrorCount": 1,
  "repeatCall": false,
  "reviewStatus": "PENDING",
  "reviewReason": null,
  "reviewedBy": null,
  "reviewedAt": null,
  "knowledgeItemId": null,
  "testCaseId": null,
  "createdAt": "2026-09-15T10:12:00Z"
}
```

| Maydon | Ma'nosi |
|---|---|
| `score` … `toolMisuse`, `note` | Hakam bahosi ([ai-agents.md §15](ai-agents.md) dagi rubrika). Hakam baholamagan bo'lsa (o'chiq, namunaga tushmagan, xato) — **hammasi `null`**, qator baribir yoziladi |
| `needsReview` | Hakam biror kamchilik topdi (guardrail, til, gapni bo'lish, noto'g'ri yakun, javobsiz savol, tool'ni noto'g'ri ishlatish) |
| `turnCount` | Suhbat turnlari soni |
| `repairCount` | Mijoz "nima?", "tushunmadim", "qaytaring", "что?", "повторите" degan qatorlar soni — agent gapi yetib bormagani belgisi |
| `bargeInCount` | Mijoz agentning gapini necha marta bo'ldi (shovqin bilan birga — keyin shovqin deb topilganlari ham) |
| `noInputPromptCount` | Jimlik watchdog'i "Alo, eshityapsizmi?" deb necha marta so'radi |
| `lowConfidenceCount` | STT o'zi ishonmagan mijoz gaplari (`low-confidence-threshold` dan past). Realtime qo'ng'iroqda doim `0` — engine audioni o'zi eshitadi |
| `toolErrorCount` | Shu qo'ng'iroqdagi 2xx qaytmagan kompaniya tool so'rovlari (`tool_execution`) |
| `repeatCall` | Shu raqam 24 soat ichida yana qo'ng'iroq qildi — masala hal bo'lmagan bo'lishi mumkin. **Keyingi** qo'ng'iroq tugaganda belgilanadi |
| `reviewStatus` | `null` — hech narsa belgilanmagan va hech kim ko'rmagan; `PENDING` — hakam belgiladi, kutmoqda; `AGENT_CORRECT` / `AGENT_WRONG` — odam bahosi |
| `note` | Nol-PII agentda saqlanmaydi (mijoz so'zlarini keltirishi mumkin) |

Hakam sozlamasi: `QUALITY_ENABLED` (standart `true`), `QUALITY_SAMPLE_RATE` (standart `1.0` — har qo'ng'iroq).
Har qo'ng'iroqqa ikkinchi LLM chaqiruvi ketadi. Hakam belgilariga ishonch hosil qilingach, namunani `0.2` gacha
tushiring. Faqat AI agent yuritgan qo'ng'iroqlar baholanadi (IVR, navbat, operator — yo'q).

---

## `POST /api/call-reviews/filter` — inbox

```json
{ "status": "PENDING", "aiAgentId": 42, "from": "2026-09-14T00:00:00Z", "to": "2026-09-16T00:00:00Z",
  "page": 0, "size": 20, "orders": { "CREATED_AT": "DESC" } }
```

| Maydon | Ma'nosi |
|---|---|
| `status` | `null` — belgilangan yoki ko'rib chiqilgan hamma qo'ng'iroq; berilsa — faqat shu holatdagilar |
| `aiAgentId` | Bitta agent |
| `from` / `to` | Qo'ng'iroq baholangan vaqt; `from` kiradi, `to` kirmaydi. `from >= to` — `400 DATE_RANGE_INVALID` |
| `orders` | `CREATED_AT` (standart, `DESC`), `SCORE` |

Javob — `PageableData<CallQuality>`.

## `PUT /api/call-reviews/{callId}` — baho

```json
{ "status": "AGENT_WRONG", "reason": "Yetkazib berish narxini to'qidi" }
```

- `AGENT_CORRECT` — agent to'g'ri qilgan, ya'ni hakam belgisi yolg'on signal bo'lgan.
- `AGENT_WRONG` — agent xato qilgan; `reason` (≤ 1000) majburiy.
- Belgilanmagan qo'ng'iroqni ham baholash mumkin (hakam o'tkazib yuborgan xato). Bahoni keyin o'zgartirish mumkin.
- `reviewedBy` / `reviewedAt` yoziladi, audit'ga `CALL_REVIEW` tushadi.

## `POST /api/call-reviews/{callId}/knowledge-item`

Body — [knowledge-base.md](knowledge-base.md) dagi `POST /api/knowledge-base` bilan bir xil
(`key`, `topic`, `title`, `answerUz`, …). `agentId` e'tiborga olinmaydi: javob qo'ng'iroqni yuritgan agentga
yoziladi. Javobda `knowledgeItemId` to'ldirilgan sifat qatori qaytadi.

## `POST /api/call-reviews/{callId}/test-case`

Body — `SaveAgentTestCaseRequest` ([ai-agents.md §15](ai-agents.md)). Holat qo'ng'iroq agentiga qo'shiladi,
keyingi eval uni ham o'ynaydi. Javobda `testCaseId` to'ldirilgan sifat qatori qaytadi.

## Xatolar

| Status | `messageCode` | Qachon |
|---|---|---|
| `404` | `CALL_QUALITY_NOT_FOUND` | Qo'ng'iroq baholanmagan (AI qo'ng'iroq emas, V12 dan oldingi) yoki boshqa kompaniyaniki |
| `400` | `CALL_REVIEW_STATUS_INVALID` | `status` — `PENDING` (u odam qo'yadigan baho emas) |
| `400` | `CALL_REVIEW_REASON_REQUIRED` | `AGENT_WRONG` sababsiz |
| `409` | `CALL_REVIEW_NO_AGENT` | KB javobi / test holati so'raldi, lekin qo'ng'iroq agenti o'chirilgan |

## Hali yo'q

- Operator ekranidagi (wrap-up) shu tugmalar.
