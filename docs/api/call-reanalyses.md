# Qo'ng'iroqlarni qayta tahlil qilish — `/api/call-reanalyses`

`uz.murodjon.robotcallv2.callrecord`

Agentga yangi `dataNeeded` maydoni qo'shildi yoki `dataEvaluation` mezoni o'zgardi — endi
shu sozlamalarni **eski qo'ng'iroqlarga** ham qo'llash mumkin. Tanlangan agentning
`[from, to)` oralig'ida boshlangan qo'ng'iroqlari transkripti bo'yicha yana bir marta tahlil
qilinadi va natijasi yangilanadi.

## 1. Nima o'zgaradi, nima o'zgarmaydi

- **O'zgaradi:** har qo'ng'iroq `outcome` idagi faqat agent tahlili kalitlari — agentning
  `dataNeeded` maydonlari, `evaluation` va `extractionErrors`. Yangi tahlil topmagan kalit
  o'chiriladi (eski qiymat qolib ketmaydi).
- **O'zgarmaydi:** `summary`, `sentiment`, `reasonCode`, `promisedDate`, `promisedAmount`,
  ssenariy `outcomeSchema` maydonlari — hisobotlar va CRM'dagi yozuv bilan moslik saqlanadi.
- **Takrorlanmaydi:** CRM eslatma, callback, post-call webhook va action'lar, mijoz xotirasi,
  bildirishnomalar.
- **Agent sozlamasi draft'dan olinadi** (tahrirlovchilar ko'radigani), publish qilingan
  versiyadan emas. Shu sababli yangi maydonni publish qilishdan oldin eski qo'ng'iroqlarda
  sinab ko'rish mumkin.
- Transkriptdagi nisbiy sanalar ("ertaga", "kelasi dushanba") **qo'ng'iroq bo'lgan kundan**
  hisoblanadi, qayta tahlil qilingan kundan emas.
- Faqat `call_result` (tahlil natijasi) va transkripti bor qo'ng'iroqlar olinadi. Birinchi
  tahlili muvaffaqiyatsiz bo'lgan yoki transkripti o'chirilgan qo'ng'iroqlar kirmaydi.

## 2. Narx

Pullik: har qo'ng'iroq tahlilining tokenlari matn-LLM narxida balansdan yechiladi — ledger
kaliti `reanalysis-charge:<jobId>:<callAttemptId>` ([billing.md](billing.md)). Bir qo'ng'iroqni
ikkinchi ish qayta tahlil qilsa, u yana yechiladi. Balans tugasa ish `FAILED` bilan to'xtaydi
(`lastError: "the balance ran out"`); o'sha paytgacha tahlil qilinganlari saqlanadi.

`/preview` — taxmin: transkriptlar uzunligidan (≈ 3 belgi = 1 token) + har qo'ng'iroqqa
~1500 token ko'rsatma va ~500 token javob. Haqiqatda yechiladigani modelning real sarfi.

## 3. Cheklovlar

| Cheklov | Qiymat |
|---|---|
| Oraliq (`to - from`) | ko'pi bilan 90 kun |
| Bitta ishdagi qo'ng'iroqlar | ko'pi bilan 5000 |
| Bir vaqtda faol ish | kompaniyaga bitta (`QUEUED` yoki `RUNNING`) |
| Worker tezligi | har `voice-agent.reanalysis.check-ms` (default 15 s) da 10 ta qo'ng'iroq, bitta instansiyada |

## 4. Endpointlar

| Metod | URL | Permission | Tavsif |
|---|---|---|---|
| `POST` | `/api/call-reanalyses/preview` | `AI_AGENT_READ` | Qo'ng'iroqlar soni va taxminiy narx. Hech narsa yaratmaydi |
| `POST` | `/api/call-reanalyses` | `AI_AGENT_EDIT` | Ishni navbatga qo'yish |
| `POST` | `/api/call-reanalyses/filter` | `AI_AGENT_READ` | Kompaniya ishlari ro'yxati |
| `GET` | `/api/call-reanalyses/{id}` | `AI_AGENT_READ` | Bitta ish va uning progressi |
| `POST` | `/api/call-reanalyses/{id}/cancel` | `AI_AGENT_EDIT` | Faol ishni to'xtatish |

### `POST /api/call-reanalyses/preview` va `POST /api/call-reanalyses`

Ikkalasi ham bir xil body oladi:

```json
{
  "aiAgentId": 7,
  "from": "2026-10-01T00:00:00Z",
  "to": "2026-10-09T00:00:00Z"
}
```

`from` kiradi, `to` kirmaydi; `call_attempt.started_at` bo'yicha.

`/preview` javobi:

```json
{
  "callCount": 340,
  "maxCalls": 5000,
  "estimatedPromptTokens": 612000,
  "estimatedCompletionTokens": 170000,
  "estimatedCostUzs": 18400
}
```

`callCount > maxCalls` bo'lsa `/preview` baribir javob beradi, lekin ishni yaratishda
`400 CALL_REANALYSIS_TOO_MANY_CALLS` qaytadi — oraliqni toraytiring.

`POST /api/call-reanalyses` javobi — yaratilgan ish (`CallReanalysisJob`, pastda).

### `POST /api/call-reanalyses/filter`

```json
{ "page": 0, "size": 20, "orders": { "CREATED_AT": "DESC" }, "aiAgentId": 7, "status": "RUNNING" }
```

`aiAgentId`, `status` ixtiyoriy. Javob — `PageableData<CallReanalysisJob>`.

### `CallReanalysisJob`

```json
{
  "id": 3,
  "companyId": 1,
  "aiAgentId": 7,
  "from": "2026-10-01T00:00:00Z",
  "to": "2026-10-09T00:00:00Z",
  "status": "RUNNING",
  "totalCalls": 340,
  "processedCalls": 120,
  "updatedCalls": 117,
  "failedCalls": 3,
  "promptTokens": 214000,
  "completionTokens": 51000,
  "chargedUzs": 6200,
  "lastCallId": 98123,
  "lastError": null,
  "createdAt": "2026-10-09T10:00:00Z",
  "startedAt": "2026-10-09T10:00:15Z",
  "finishedAt": null
}
```

| Maydon | Izoh |
|---|---|
| `status` | `QUEUED` → `RUNNING` → `COMPLETED`; yoki `CANCELLED` (paneldan), `FAILED` (balans tugadi yoki agent o'chirildi — `lastError`) |
| `totalCalls` | Ish yaratilgandagi qo'ng'iroqlar soni. Shu orada qo'ng'iroq o'chirilsa, `processedCalls` undan kam bo'lib tugashi mumkin |
| `processedCalls` | Ko'rib chiqilganlar, natijasidan qat'i nazar |
| `updatedCalls` | `outcome` i yangilanganlar |
| `failedCalls` | Model javob bermagan yoki xato bo'lganlar — `outcome` o'zgarmagan |
| `chargedUzs` | Shu ish hozirgacha yechgan summa |
| `lastCallId` | Worker'ning kursori: qo'ng'iroqlar id tartibida olinadi, qayta ishga tushganda shu yerdan davom etadi |

### `POST /api/call-reanalyses/{id}/cancel`

Faol ishni to'xtatadi; worker qo'lidagi qo'ng'iroqni tugatib, keyingisiga o'tmaydi. Tahlil
qilinganlari yangi natija bilan qoladi. Javob — yangilangan ish.

## 5. Xatolar

| Xato | Qachon |
|---|---|
| `400 VALIDATION_FAILED` | `aiAgentId`, `from` yoki `to` yo'q |
| `400 CALL_REANALYSIS_RANGE_INVALID` | `from >= to` yoki oraliq 90 kundan uzun |
| `400 CALL_REANALYSIS_NOTHING_TO_ANALYSE` | Agentda `dataNeeded` ham, `dataEvaluation` ham yo'q |
| `400 CALL_REANALYSIS_NO_CALLS` | Oraliqda natija va transkripti bor qo'ng'iroq yo'q (faqat yaratishda) |
| `400 CALL_REANALYSIS_TOO_MANY_CALLS` | Qo'ng'iroqlar 5000 dan ko'p (faqat yaratishda) |
| `404 AI_AGENT_NOT_FOUND` | Agent yo'q yoki boshqa kompaniyaniki |
| `404 CALL_REANALYSIS_NOT_FOUND` | Ish yo'q yoki boshqa kompaniyaniki |
| `409 CALL_REANALYSIS_ALREADY_ACTIVE` | Kompaniyada boshqa ish `QUEUED` yoki `RUNNING` |
| `409 CALL_REANALYSIS_NO_BALANCE` | Balans yetarli emas |
| `409 CALL_REANALYSIS_UNAVAILABLE` | Serverda tahlil modeli sozlanmagan (`voice-agent.summary`) |
| `409 CALL_REANALYSIS_NOT_ACTIVE` | Bekor qilinayotgan ish allaqachon tugagan yoki bekor qilingan |
