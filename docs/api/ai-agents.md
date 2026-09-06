# AI Agentlar — `/api/ai-agents`

**Permission:** `AI_AGENT_READ` (ko'rish) · `AI_AGENT_EDIT` (yaratish/tahrirlash/o'chirish)

---

## 1. Agent nima va nega kerak

Tizimda uchta alohida tushuncha bor va ular bir-birini takrorlamaydi:

| Obyekt | Savolga javob beradi | Nimani ushlaydi |
|---|---|---|
| **Scenario** (`/api/scenarios`) | **Nima gapiriladi** | Bosqichlar (FSM), faktlar, tool'lar, role prompt, guardrails |
| **AI Agent** (`/api/ai-agents`) | **Kim gapiradi** | Senariy havolasi + ovoz, til, persona, LLM model, SIP trunklar |
| **Campaign** (`/api/campaigns`) | **Kimga va qachon** | Nishonlar ro'yxati, qo'ng'iroq oynasi, urinishlar, takrorlanish |

```
Campaign      →  AiAgent  →  Scenario
InboundRoute  →  AiAgent  →  Scenario
```

**Kampaniya senariyga to'g'ridan-to'g'ri bog'lanmaydi** — u agentga bog'lanadi, agent esa
senariyga. Bitta senariyni ikki agent (masalan o'zbekcha va ruscha, yoki "qat'iy" va
"yumshoq") gapirishi mumkin — senariyni nusxalash shart emas.

Kiruvchi qo'ng'iroq ham xuddi shu obyektdan o'qiydi: ilgari kiruvchi qo'ng'iroqda kampaniya
bo'lmagani uchun ovoz va personani berish imkoni yo'q edi.

---

## 2. Endpointlar

| Metod | URL | Permission | Tavsif |
|---|---|---|---|
| `POST` | `/api/ai-agents` | `AI_AGENT_EDIT` | Yangi agent |
| `POST` | `/api/ai-agents/filter` (yoki `/list`) | `AI_AGENT_READ` | Sahifalangan ro'yxat |
| `GET` | `/api/ai-agents/{id}` | `AI_AGENT_READ` | Bitta agent |
| `PUT` | `/api/ai-agents/{id}` | `AI_AGENT_EDIT` | To'liq tahrirlash |
| `DELETE` | `/api/ai-agents/{id}` | `AI_AGENT_EDIT` | O'chirish (band bo'lsa `409`) |

---

## 3. `POST /api/ai-agents`

```json
{
  "name": "Qarz undirish — o'zbekcha",
  "description": "Kunlik qarzdorlar bazasi uchun",
  "scenarioId": 12,
  "language": "uz-UZ",
  "ttsVoice": "nigora",
  "languageVoices": { "uz-UZ": "nigora", "ru-RU": "alena" },
  "persona": "AI_ASSISTANT",
  "llmModel": null,
  "temperature": null,
  "maxOutputTokens": null,
  "ambientSound": "OFFICE",
  "emotionAdaptiveVoice": true,
  "dtmfInputEnabled": false,
  "voicemailAction": "HANGUP",
  "voicemailMessage": null,
  "midCallSmsEnabled": false,
  "midCallSmsTemplate": null,
  "sipTrunkIds": [3, 4],
  "enabled": true
}
```

| Maydon | Majburiy | Izoh |
|---|---|---|
| `name` | ✅ | Ko'rinadigan nom (maks. 255) |
| `scenarioId` | ✅ | `GET /api/scenarios` dagi senariy id. Keyin ham o'zgartirsa bo'ladi |
| `language` | ❌ | BCP-47. Bo'sh bo'lsa kompaniyaning default tili |
| `ttsVoice` | ❌ | `GET /api/tts/voices` dagi id. Noma'lum id → `400 TTS_VOICE_UNKNOWN` |
| `languageVoices` | ❌ | Til → ovoz. Har bir ovoz o'sha tilda gapirishi shart, aks holda `400 TTS_VOICE_LANGUAGE_MISMATCH` |
| `persona` | ❌ | `AI_ASSISTANT` (default) — qo'ng'iroq §11.1 disclosure bilan boshlanadi; `HUMAN_LIKE` — boshlanmaydi |
| `llmModel` / `temperature` / `maxOutputTokens` | ❌ | Bo'sh bo'lsa kompaniyaning `/api/settings/ai-model` sozlamasi. `temperature` 0..2, `maxOutputTokens` 1..4096 |
| `ambientSound` | ❌ | `OFF` (default), `OFFICE`, `CALL_CENTER`, `NATURAL_LINE`, `CAFE` |
| `sipTrunkIds` | ❌ | Shu agent qaysi trunklardan qo'ng'iroq qilishi mumkin. Bo'sh — kompaniyaning barcha yoqilgan trunklari bo'yicha balanslanadi |
| `enabled` | ❌ | `false` bo'lsa dialer bu agentli kampaniyani chetlab o'tadi |

**Javob** — `AiAgentRow`:

```json
{
  "accept": true,
  "data": {
    "id": 7,
    "companyId": 1,
    "name": "Qarz undirish — o'zbekcha",
    "description": "Kunlik qarzdorlar bazasi uchun",
    "scenarioId": 12,
    "scenarioName": "Qarz undirish v3",
    "language": "uz-UZ",
    "ttsVoice": "nigora",
    "languageVoices": { "uz-UZ": "nigora", "ru-RU": "alena" },
    "persona": "AI_ASSISTANT",
    "disclosureEnabled": true,
    "llmModel": null,
    "temperature": null,
    "maxOutputTokens": null,
    "ambientSound": "OFFICE",
    "emotionAdaptiveVoice": true,
    "dtmfInputEnabled": false,
    "voicemailAction": "HANGUP",
    "voicemailMessage": null,
    "midCallSmsEnabled": false,
    "midCallSmsTemplate": null,
    "sipTrunkIds": [3, 4],
    "enabled": true,
    "createdAt": "2026-09-06T09:12:00Z",
    "createdBy": 5,
    "createdByName": "Aziz Karimov"
  }
}
```

`disclosureEnabled` — hisoblanadigan maydon: `persona == AI_ASSISTANT`.

---

## 4. `POST /api/ai-agents/filter`

```json
{
  "page": 0,
  "size": 20,
  "orders": { "CREATED_AT": "DESC" },
  "search": "qarz",
  "scenarioId": 12,
  "enabled": true
}
```

Sort ustunlari: `ID`, `NAME`, `LANGUAGE`, `SCENARIO_ID`, `CREATED_AT`.
Javob — `PageableData<AiAgentRow>`.

---

## 5. `PUT /api/ai-agents/{id}`

`POST` bilan bir xil tana. `scenarioId` ni ham o'zgartirsa bo'ladi — agent "gapirish
uslubi", uni boshqa skriptga qaratish yangi agent yasamaydi. Shu agent bilan ishlab turgan
kampaniyalar keyingi qo'ng'iroqdan boshlab yangi skriptni oladi.

---

## 6. `DELETE /api/ai-agents/{id}`

Agentga hali biror kampaniya yoki kiruvchi marshrut bog'langan bo'lsa **o'chirilmaydi**:

```json
{
  "accept": false,
  "messageCode": "AI_AGENT_IN_USE",
  "message": "AI agent 7 is still used by 2 campaign(s) and 1 inbound route(s)"
}
```

Sabab: agentsiz qolgan kampaniya bitta ham qo'ng'iroq qila olmaydi va buni egasi faqat
"ishlayapti, lekin hech kimga qo'ng'iroq qilmayapti" holatidan bilib qoladi. Vaqtincha
to'xtatish uchun `enabled: false` ishlating.

---

## 7. Xato kodlari

| `messageCode` | Status | Qachon |
|---|---|---|
| `AI_AGENT_NOT_FOUND` | 404 | Agent yo'q yoki boshqa kompaniyaniki |
| `SCENARIO_NOT_FOUND` | 404 | `scenarioId` mavjud emas |
| `TTS_VOICE_UNKNOWN` | 400 | Ovoz katalogda yo'q |
| `TTS_VOICE_LANGUAGE_MISMATCH` | 400 | Ovoz o'ziga biriktirilgan tilda gapirmaydi |
| `AI_AGENT_TEMPERATURE_OUT_OF_RANGE` | 400 | `temperature` 0..2 dan tashqarida |
| `AI_AGENT_MAX_OUTPUT_TOKENS_OUT_OF_RANGE` | 400 | `maxOutputTokens` 1..4096 dan tashqarida |
| `AI_AGENT_IN_USE` | 409 | O'chirishda: kampaniya yoki marshrut hali foydalanmoqda |
