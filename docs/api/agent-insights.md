# Yaxshilanish inbox'i (Agent Insight) — `/api/agent-insights`

Har kecha job agentning bir sutkalik qo'ng'iroqlarini o'qiydi va **nimani o'zgartirish
kerakligini taklif qiladi**: qoidabuzarlik takrorlanyaptimi, mijozlar javob topa olmayotgan savol
bormi, tool xato qaytaryaptimi. Taklif — bu inbox'dagi bitta qator; uni odam qabul qiladi yoki rad
etadi (UNIVERSAL-OPERATOR-PLAN D.3).

Qabul qilingan **prompt o'zgarishi darhol jonli qo'ng'iroqqa chiqmaydi**: u qoralamaga qo'shiladi va
agentning o'z test holatlari unga qarshi yugurtiriladi. Barcha holatlar o'tsagina o'zgarish qoladi va
"chop etasizmi?" bildirishnomasi keladi; birortasi yiqilsa — o'zgarish qoralamadan **avtomatik
qaytariladi** (D.4). Chop etish har doim odamning tugmasi: `POST /api/ai-agents/{id}/publish`.

Takliflar API orqali yaratilmaydi — ular faqat qo'ng'iroqlardan tug'iladi.

## Endpointlar

| Metod | URL | Permission | Tavsif |
|---|---|---|---|
| `POST` | `/api/agent-insights/filter` | `AI_AGENT_READ` | Inbox (sahifalangan) |
| `GET` | `/api/agent-insights/{insightId}` | `AI_AGENT_READ` | Bitta taklif |
| `POST` | `/api/agent-insights/{insightId}/accept` | `AI_AGENT_EDIT` | Qabul qilish |
| `POST` | `/api/agent-insights/{insightId}/reject` | `AI_AGENT_EDIT` | Rad etish |

---

## `POST /api/agent-insights/filter`

```json
{
  "aiAgentId": 42,
  "type": "PROMPT_FIX",
  "status": "PROPOSED",
  "page": 0,
  "size": 20,
  "orders": { "CREATED_AT": "DESC" }
}
```

Barcha maydonlar ixtiyoriy. `orders` ustunlari: `CREATED_AT`, `CALL_COUNT`.

```json
{
  "accept": true,
  "data": {
    "data": [
      {
        "id": 7,
        "companyId": 1,
        "aiAgentId": 42,
        "type": "PROMPT_FIX",
        "title": "Agent chegirma va'da qilyapti",
        "detail": "Sutkada 30 qo'ng'iroqdan 9 tasida sifat sudyasi qoidabuzarlik belgiladi: agent chegirma yoki bepul yetkazib berish va'da qilgan.",
        "suggestion": "Chegirma, aksiya yoki bepul yetkazib berish va'da qilmang.",
        "callCount": 9,
        "sampleCalls": [10233, 10240, 10251],
        "status": "PROPOSED",
        "decisionReason": null,
        "decidedBy": null,
        "decidedAt": null,
        "evalRunId": null,
        "createdAt": "2026-09-16T01:20:00Z"
      }
    ],
    "currentPage": 0, "pageSize": 20, "totalElements": 1, "totalPages": 1
  },
  "message": null, "messageCode": null, "errors": null
}
```

| Maydon | Ma'nosi |
|---|---|
| `type` | Taklif turi — pastdagi jadval |
| `detail` | Nima ko'rinayotgani, sonlari bilan |
| `suggestion` | Aniq o'zgarish. `PROMPT_FIX` da — qoidalar ro'yxatiga qo'shiladigan **bitta gap** |
| `callCount` | Shu muammo ko'ringan qo'ng'iroqlar soni |
| `sampleCalls` | Dalil: `call_attempt` id'lari ([calls.md](calls.md) orqali ochiladi) |
| `evalRunId` | Qabul qilingandan keyin — shu o'zgarishni hal qilayotgan eval run ([ai-agents.md §15](ai-agents.md)) |

**Statuslar:** `PROPOSED` (inbox'da kutyapti) → `EVALUATING` (qabul qilindi, qoralamada, eval yugurayapti)
→ `ACCEPTED` yoki `REJECTED`. `decisionReason` da nima bo'lgani yoziladi (`eval 5/5`, `eval 3/5 —
o'zgarish qoralamadan qaytarildi`, yoki odam yozgan sabab).

---

## Taklif turlari va "qabul qilish" nima qiladi

| `type` | Qachon | Qabul qilinganda |
|---|---|---|
| `PROMPT_FIX` | Agent aytmasligi kerak bo'lgan narsani aytgan | `suggestion` gapi agent qoralamasining **guardrail** ro'yxatiga qo'shiladi va eval boshlanadi (gate) |
| `NEW_TEST_CASE` | Bu holat qayta takrorlanmasligi kerak | `suggestion` persona sifatida yangi test holati yaratiladi |
| `KB_GAP` | Mijozlar so'ragan savolga javob yo'q | Faqat belgilanadi — javob matnini kompaniya biladi, uni [knowledge-base.md](knowledge-base.md) orqali odam qo'shadi |
| `TOOL_FIX` | Tool xato qaytaryapti yoki noto'g'ri chaqirilyapti | Faqat belgilanadi — tool tavsifini odam tahrirlaydi ([tools.md](tools.md)) |
| `PRONUNCIATION` | So'z noto'g'ri eshitilyapti/o'qilyapti | Faqat belgilanadi — qoida [ai-agents.md](ai-agents.md) talaffuz bo'limida qo'shiladi |
| `HANDOFF_PATTERN` | Operatorga o'tkazish kech yoki keraksiz | Faqat belgilanadi |

"Faqat belgilanadi" — status `ACCEPTED` bo'ladi, `decisionReason`: "Belgilandi — bu o'zgarishni odam
kiritadi". Sabab oddiy: bu o'zgarishlarning matnini faqat kompaniya biladi, uni LLM yozsa agent
yolg'on gapiradi.

---

## `POST /api/agent-insights/{id}/accept`

Body yo'q. Javob — o'zgargan taklif.

`PROMPT_FIX` uchun ketma-ketlik:

1. Agentda test holati bormi tekshiriladi — yo'q bo'lsa `409 AGENT_EVAL_NO_TEST_CASES` va **hech narsa
   o'zgarmaydi** (test holatisiz gate yo'q).
2. `suggestion` gapi qoralama guardrail'lariga qo'shiladi.
3. Eval run boshlanadi, taklif `EVALUATING` bo'ladi, `evalRunId` to'ldiriladi.
4. Run tugagach: hammasi o'tsa — `ACCEPTED` + `AGENT_INSIGHT_READY` bildirishnomasi
   ([notifications.md](notifications.md)); birortasi yiqilsa — gap qoralamadan olib tashlanadi va
   taklif `REJECTED` bo'ladi. Shu orada agent qoralamasi boshqa joydan o'zgartirilgan bo'lsa gapni
   avtomatik olib tashlab bo'lmaydi — taklif baribir `REJECTED` bo'ladi, `decisionReason` da qaysi
   gapni qo'lda o'chirish kerakligi yoziladi.

Eval boshlana olmasa (masalan shu agentning boshqa run'i yuryapti) qo'shilgan gap darhol qaytariladi
va xato qaytadi — qoralama tegilmagan holicha qoladi.

**Xatolar**

| Status | `messageCode` | Qachon |
|---|---|---|
| `404` | `AGENT_INSIGHT_NOT_FOUND` | Bunday taklif yo'q yoki boshqa kompaniyaniki |
| `409` | `AGENT_INSIGHT_ALREADY_DECIDED` | Taklif allaqachon hal qilingan |
| `409` | `AGENT_INSIGHT_NOTHING_TO_APPLY` | `PROMPT_FIX`/`NEW_TEST_CASE` da `suggestion` bo'sh |
| `409` | `AGENT_EVAL_NO_TEST_CASES` | Agentda test holati yo'q |
| `409` | `AGENT_EVAL_ALREADY_RUNNING` | Shu agentning eval'i allaqachon yuryapti |
| `409` | `AI_AGENT_SCENARIO_SHARED` | Agent umumiy ssenariyni gapiradi — u ssenariyning o'zi tahrirlanadi ([scenarios.md](scenarios.md)) |

## `POST /api/agent-insights/{id}/reject`

```json
{ "reason": "Bu mijozning o'zi shunday so'ragan, agent to'g'ri qilgan" }
```

Body ixtiyoriy. Sabab saqlanadi — halqa keyinroq shu sabablar bo'yicha kalibrlanadi.

---

## Kunlik job

`DailyInsightJob`, har kuni **06:20 (Asia/Tashkent)**, klasterda bitta instance'da
(`insight.daily` lock). Sozlamalar — `voice-agent.insight.*` (`config/misc.yml`):

| Kalit | Standart | Ma'nosi |
|---|---|---|
| `INSIGHT_ENABLED` | `true` | O'chirilsa hech narsa taklif qilinmaydi |
| `INSIGHT_CRON` | `0 20 6 * * *` | Qachon yuradi. Sinash uchun `0 * * * * *` (har daqiqada) |
| `INSIGHT_WINDOW_HOURS` | `24` | Qancha orqaga qaraydi |
| `INSIGHT_MIN_CALLS` | `10` | Shundan kam qo'ng'iroq qilgan agent o'tkazib yuboriladi |
| `INSIGHT_MAX_PER_AGENT` | `5` | Bitta yugurishda bitta agentdan nechta taklif |
| `INSIGHT_MODEL` | `gemini-3.8-flash` | Klasterlovchi model |

Job LLM'ga **transkript bermaydi** — faqat sonlar, sifat sudyasining bir qatorli izohlari va
supervayzerning "agent xato qildi" sabablari boradi ([call-reviews.md](call-reviews.md)). Shu sababli
u sifat sudyasi o'chirilgan deployment'da deyarli hech narsa topmaydi. Model sozlanmagan bo'lsa job
jim tugaydi.

Oyna ataylab ustma-ust tushadi (har kuni oxirgi 24 soat), shuning uchun bir xil taklif ertasi kuni ham
chiqadi — lekin inbox'da **bitta ochiq taklif** bo'ladi: `(agent, type, title)` bo'yicha takrorlanmaydi.
