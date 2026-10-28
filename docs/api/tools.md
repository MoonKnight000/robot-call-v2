# Tools (Tashqi API amallari) — `/api/tools`

**Permission:** `AI_AGENT_READ` (ko'rish) · `AI_AGENT_EDIT` (yaratish/tahrirlash/o'chirish) — tool'lar agentga tegishli, alohida ruxsat yo'q

---

## 1. QuickVoice Tools Arxitekturasi

Tizimda AI Agentlar uchun mustaqil **Tools** mexanizmi joriy qilindi. Agent qo'ng'iroq paytida mijozning so'rovi yoki stsenariy talabiga binoan tashqi REST API'larni (CRM, ERP, Billing, Buyurtma tizimlari va boshqalar) chaqirishi mumkin.

```
┌─────────────────────────────────────────────────────────────┐
│                       TOOL EXECUTION                        │
│                                                             │
│  1. LLM Tool chaqirish qaroriga keladi                     │
│     • Masalan: check_order_status(order_id="12345")         │
│                                                             │
│  2. HttpToolExecutor parametr va dinamik o'zgaruvchilarni   │
│     interpolatsiya qiladi:                                  │
│     • {{channel_id}}, {{phone}}, {{company_id}}             │
│     • Path parametrlar: /orders/{order_id}                  │
│     • Query / Header / Body parametrlar                     │
│                                                             │
│  3. preToolSpeech (ixtiyoriy ovozli xabar):                 │
│     • "Bir daqiqa, buyurtmangiz holatini tekshirmoqdaman..."│
│     • TTS orqali darhol so'zlanadi (foydalanuvchi kutadi)   │
│                                                             │
│  4. HTTP so'rov yuboriladi va javob olinadi                 │
│     • Natija JSON formatda LLM ga qaytariladi               │
│     • DialogOutcomeSink ga 'tool:<nomi>' sifatida saqlanadi │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Endpointlar

| Metod | URL | Permission | Tavsif |
|---|---|---|---|
| `POST` | `/api/tools` | `AI_AGENT_EDIT` | Yangi tool yaratish |
| `GET` | `/api/tools` | `AI_AGENT_READ` | Kompaniyaning barcha tool'lari ro'yxati |
| `POST` | `/api/tools/filter` | `AI_AGENT_READ` | Sahifalangan va saralangan qidiruv |
| `GET` | `/api/tools/{id}` | `AI_AGENT_READ` | Bitta tool tafsilotlarini olish |
| `PUT` | `/api/tools/{id}` | `AI_AGENT_EDIT` | Tool ma'lumotlarini yangilash |
| `DELETE` | `/api/tools/{id}` | `AI_AGENT_EDIT` | Toolni o'chirish (agentlarga biriktirilgan bo'lsa `409`) |
| `POST` | `/api/tools/{id}/test` | `AI_AGENT_EDIT` | Toolni bir marta sinab ko'rish: modelga nima qaytishini ko'rsatadi (§4.1) |
| `POST` | `/api/tools/import/operations` | `AI_AGENT_EDIT` | OpenAPI 3 hujjatidagi amallar ro'yxati — qaysilari tool bo'lishi mumkin (§7). Hech narsa yaratmaydi |
| `POST` | `/api/tools/import` | `AI_AGENT_EDIT` | Tanlangan amallardan tool'lar yaratish — hammasi yoki hech biri (§7) |
| `POST` | `/api/tools/executions/filter` | `AI_AGENT_READ` | So'rovlar jurnali — sahifalangan, filtrli (§8) |
| `GET` | `/api/tools/executions/stats` | `AI_AGENT_READ` | Har bir tool bo'yicha statistika: natijalar soni, o'rtacha va p95 latency (§8) |

---

## 3. `POST /api/tools` — Tool yaratish

### Request Body:
```json
{
  "name": "check_delivery_status",
  "description": "Mijozning buyurtma raqami bo'yicha yetkazib berish holatini aniqlash",
  "http": {
    "url": "https://api.mycompany.uz/v1/orders/{order_id}/track",
    "method": "GET",
    "headers": [
      { "key": "Authorization", "value": "Bearer my-secret-api-token" },
      { "key": "X-Company-Id", "value": "{{company_id}}" }
    ],
    "queryParams": [
      { "name": "phone", "type": "string", "valueType": "Dynamic Variable", "value": "phone" }
    ],
    "pathParams": [
      {
        "name": "order_id",
        "type": "string",
        "valueType": "LLM Prompt",
        "description": "Mijoz aytgan 6 xonali buyurtma raqami",
        "required": true
      }
    ],
    "body": null,
    "bodySchema": null,
    "responseTimeoutSecs": 5,
    "retryAttempts": 1,
    "auth": null
  },
  "dynamicVariables": [
    { "key": "source", "value": "voice_bot_v2" }
  ],
  "speech": {
    "disableInterruptions": true,
    "forcePreToolSpeech": true,
    "preToolSpeech": "Bir daqiqa kutasizmi, buyurtmangiz holatini bazadan tekshirib ko'raman."
  },
  "responseMapping": { "order_status": "$.data.status", "eta": "$.data.eta" },
  "maxCallsPerCall": 3
}
```

> **Maydon guruhlari (2026-10-28 dan, breaking change).** So'rov qanday yuborilishi `http`
> ichida, tool ishlayotganda mijoz nima eshitishi `speech` ichida. Shakl `POST`, `PUT` va javob
> (`ToolRow`) da bir xil. Eski → yangi: `apiUrl` → `http.url`, `apiMethod` → `http.method`,
> `apiHeaders` → `http.headers`, `apiBody` → `http.body`, `apiQueryParams` → `http.queryParams`,
> `apiPathParams` → `http.pathParams`, `bodySchema`, `responseTimeoutSecs`, `retryAttempts`, `auth` →
> `http.*`; `disableInterruptions`, `forcePreToolSpeech`, `preToolSpeech` → `speech.*`. Yuqori
> darajada qoladi: `name`, `description`, `dynamicVariables`, `responseMapping`, `maxCallsPerCall`,
> `effect`, `requiresConfirmation`, `readbackTemplate`. `POST` da `http` majburiy (berilmasa —
> `400 VALIDATION_FAILED`, `http.url` bo'sh bo'lsa — `400 TOOL_URL_REQUIRED`). `PUT` da guruhni yoki
> guruh ichidagi maydonni tashlab ketish o'sha qiymatni o'zgartirmaydi.
> **Pack fayllari o'zgarmaydi:** `packs/*.json` va eksport qilingan pack'lardagi tool'lar avvalgi
> tekis shaklda qoladi ([packs.md](packs.md)).

### Maydonlar tavsifi:

| Maydon | Turi | Majburiylik | Tavsif |
|---|---|---|---|
| `name` | string | **Majburiy** | Tool nomi (kichik lotin harflari va pastki chiziq tavsiya etiladi) |
| `description` | string | **Majburiy** | Tool nima vazifa bajarishi. LLM ushbu tavsifga qarab uni qachon chaqirishni hal qiladi |
| `http.url` | string | **Majburiy** (`POST`) | Chaqiriladigan REST API manzili (masalan `https://api.crm.com/leads`) |
| `http.method` | string | Standart: `POST` | HTTP metod: `GET`, `POST`, `PUT`, `DELETE`, `PATCH` |
| `http.headers` | array | Ixtiyoriy | Sarlavhalar ro'yxati (`[{"key": "Authorization", "value": "..."}]`) |
| `http.queryParams` | array | Ixtiyoriy | URL query parametrlari — parametr obyektlari ro'yxati (pastga qarang) |
| `http.pathParams` | array | Ixtiyoriy | URL ichidagi `{param}` o'zgaruvchilari — parametr obyektlari ro'yxati |
| `http.body` | array | Ixtiyoriy | `POST`/`PUT` tanasi — parametr obyektlari ro'yxati, JSON obyektga aylantiriladi. Ichma-ich obyekt yoki ro'yxat kerak bo'lsa — `bodySchema` (§3.1) |
| `http.responseTimeoutSecs` | int | Standart: 10 | Maksimal kutish vaqti (sekund) |
| `dynamicVariables` | array | Ixtiyoriy | Qo'shimcha o'zgaruvchilar (`[{"key": "...", "value": "..."}]`) |
| `speech.disableInterruptions` | bool| Standart: false| Tool javobi kutilayotganda mijoz gapini to'xtatmaslik (barge-in block) |
| `speech.forcePreToolSpeech` | bool | Standart: `true` | API chaqirishdan oldin `preToolSpeech` aytilishini majburiy qilish |
| `speech.preToolSpeech` | string| Ixtiyoriy | Chaqiruv boshlanishida robot o'qiydigan jumla ("Bir daqiqa...") |
| `responseMapping` | object | Ixtiyoriy | Nom → JSONPath (`$.data.status`, `$.items[0].eta`). Berilsa modelga **faqat shu maydonlar** boradi (`{"order_status": "shipped"}`), butun javob emas. Javobda yo'q yo'l tushib qoladi. `$` bilan boshlanmagan yo'l — `400 TOOL_RESPONSE_MAPPING_INVALID`. Bo'sh — butun javob (4000 belgigacha). 2xx javobda mapping maydonlari qo'ng'iroq natijasiga ham shu nomlar bilan yoziladi — ssenariyning `goal` maydoni shu nomda bo'lsa, qo'ng'iroq `GOAL_REACHED` bilan tugaydi ([scenarios.md](scenarios.md)). 2xx javobda modelga borgan raqamlar (narx, jami, buyurtma raqami) agent ovoz chiqarib aytishi mumkin bo'lgan raqamlarga qo'shiladi — fact guard ularni to'qilgan deb bloklamaydi |
| `http.retryAttempts` | int | Standart: 0 | 5xx yoki tarmoq xatosidan keyin qayta urinishlar soni (0–3), kutish 300 ms, 600 ms, … 4xx qayta yuborilmaydi. Standart 0: yarim bajarilgan `POST` so'ralmasdan ikki marta ketmasin |
| `maxCallsPerCall` | int | Standart: 3 | Bitta qo'ng'iroqda tool necha marta chaqirilishi mumkin; oshsa so'rov yuborilmaydi va modelga `status: 429` bilan "qayta chaqirmang" deyiladi. `0` — cheklovsiz |
| `http.bodySchema` | object | Ixtiyoriy | Ichma-ich body uchun JSON Schema (`{"type": "object", "properties": {...}}`) — §3.1. `http.body` bilan birga berilsa `400 TOOL_BODY_AMBIGUOUS` |
| `effect` | string | Standart: `GET` → `READ`, boshqasi → `WRITE` | `READ` / `WRITE` / `DESTRUCTIVE` — §3.2. `DESTRUCTIVE` modelga hech qachon berilmaydi |
| `requiresConfirmation` | bool | Standart: `WRITE` uchun `true` | Chaqiruv mijozga o'qib beriladi va faqat aniq "ha"dan keyin yuboriladi — §3.2 |
| `readbackTemplate` | string | Ixtiyoriy (≤500) | Tasdiq jumlasi, har argument `{{nomi}}` bilan: `"{{items}}, manzil {{address}}. Tasdiqlaysizmi?"`. Bo'sh — jumlani model o'zi tuzadi |
| `http.auth` | object | Ixtiyoriy | Tool'ning o'z OAuth mijozi — §3.4. `PUT` da `null` — o'zgarmaydi, `{}` — olib tashlanadi |

### Parametr obyekti (`http.queryParams`, `http.pathParams`, `http.body` elementi):

| Maydon | Turi | Majburiylik | Tavsif |
|---|---|---|---|
| `name` | string | **Majburiy** | Parametr nomi. Path uchun URL ichidagi `{name}` bilan bir xil bo'lishi shart |
| `type` | string | Standart: `string` | `string`, `number`, `integer`, `boolean` |
| `valueType` | string | Standart: `LLM Prompt` | Qiymat qayerdan olinishi — pastdagi jadval |
| `value` | any | `valueType` ga bog'liq | `Static Value` uchun qiymatning o'zi, `Dynamic Variable` uchun o'zgaruvchi nomi |
| `description` | string | Ixtiyoriy | `LLM Prompt` uchun: model nima kiritishini shundan tushunadi |
| `allowedValues` | array | Ixtiyoriy | `LLM Prompt` uchun ruxsat etilgan qiymatlar (JSON schema `enum`) |
| `required` | bool | Standart: false | `LLM Prompt` uchun majburiy argument |

**`valueType` qiymatlari:**

| Qiymat | Qiymat qayerdan olinadi |
|---|---|
| `LLM Prompt` | Model qo'ng'iroq paytida o'zi to'ldiradi. Faqat shu turdagi parametrlar tool sxemasiga chiqadi |
| `Static Value` | `value` maydonidagi o'zgarmas qiymat |
| `Dynamic Variable` | Qo'ng'iroq o'zgaruvchilaridan, nomi `value` da (bo'sh bo'lsa `name`) |

Noma'lum yoki bo'sh `valueType` — `LLM Prompt` deb qabul qilinadi.

### `{{secrets.KEY}}` — maxfiy qiymatlar

`http.url` va `http.headers[].value` ichida `{{secrets.KEY}}` yozish mumkin; qiymat chaqiruv
paytida kompaniyaning [secrets](secrets.md) omboridan olinadi. Noma'lum kalit
o'zgartirilmay qoladi — shunda so'rov muvaffaqiyatsiz bo'ladi va xato ko'rinadi.

Maxfiy qiymatlar **birinchi** ochiladi, faqat keyin qo'ng'iroq o'zgaruvchilari va model
argumentlari qo'yiladi — ya'ni argument ichidagi `{{secrets.X}}` matni shunchaki matn
bo'lib qoladi. Log'da va modelga qaytadigan xatoda ular `***` bilan yashiriladi.

### 3.1. `bodySchema` — ichma-ich body

`http.body` faqat yassi obyektni ta'riflay oladi. Haqiqiy buyurtma esa
`{"items": [{"sku", "qty"}], "address": {...}}` ko'rinishida bo'ladi. Bunday body JSON Schema
bilan beriladi, va model butun obyektni bitta chaqiruvda to'ldiradi:

```json
"bodySchema": {
  "type": "object",
  "required": ["items", "phone"],
  "properties": {
    "items": { "type": "array", "description": "Buyurtma qatorlari",
      "items": { "type": "object", "properties": {
        "sku": { "type": "string", "description": "Katalogdagi mahsulot kodi" },
        "qty": { "type": "integer" } } } },
    "address": { "type": "object", "properties": {
      "street": { "type": "string" },
      "channel": { "type": "string", "x-source": "static", "x-value": "voice" } } },
    "phone": { "type": "string", "x-source": "variable", "x-value": "phone" },
    "token": { "type": "string", "x-source": "secret", "x-value": "SHOP_TOKEN" }
  }
}
```

Maydon qiymati qayerdan kelishini `x-source` belgilaydi. `x-source` bo'lmasa, maydonni model to'ldiradi:

| `x-source` | Qiymat | `x-value` |
|---|---|---|
| — | Model to'ldiradi, u yuborgan ko'rinishda (ichma-ich obyekt va ro'yxat ham) | — |
| `static` | O'zgarmas qiymat | Qiymatning o'zi (istalgan JSON) |
| `variable` | Qo'ng'iroq o'zgaruvchisi (`phone`, CRM fakti, …) | O'zgaruvchi nomi; bo'sh bo'lsa — maydon nomi |
| `secret` | Kompaniyaning [secrets](secrets.md) omboridan | Kalit; bo'sh bo'lsa — maydon nomi |

- Modelga faqat u to'ldiradigan maydonlar ko'rsatiladi: `x-source` maydonlari sxemadan va `required`
  ro'yxatidan olib tashlanadi. Model shu maydonga biror qiymat yuborsa ham, e'tiborga olinmaydi, shuning
  uchun secret faqat sxemadan keladi, hech qachon argumentdan kelmaydi.
- So'rovga **faqat sxemada nomlangan** maydonlar ketadi. Model o'ylab topgan qo'shimcha argument yuborilmaydi.
- Modelga sxemaning faqat `type`, `description`, `properties`, `required`, `items`, `enum`, `format`,
  `minimum`, `maximum`, `minItems`, `maxItems`, `nullable` kalit so'zlari boradi. Qolganlari
  (`$schema`, `additionalProperties`, …) saqlanadi, lekin model deklaratsiyasiga kirmaydi.
- O'zgaruvchi yoki secret topilmasa, maydon body'dan tushib qoladi.
- Sinash: `POST /api/tools/{id}/test` ga `"args": {"items": [{"sku": "A1", "qty": 2}], "address": {"street": "Chilonzor 5"}}`
  berilsa, endpoint'ga aynan shu ichma-ich JSON va `x-source` maydonlari ketadi.
- `PUT` da `http.body` dan `bodySchema` ga o'tishda `"http": {"body": []}` ham yuboring. `bodySchema` ni o'chirish uchun `{}` yuboriladi.

| Xato | Qachon |
|---|---|
| `400 TOOL_BODY_AMBIGUOUS` | `http.body` ham, `bodySchema` ham berilgan |
| `400 TOOL_BODY_SCHEMA_ROOT_INVALID` | Ildiz `{"type": "object", "properties": {...}}` emas |
| `400 TOOL_BODY_SOURCE_INVALID` | `x-source` — `static`/`variable`/`secret` dan boshqa narsa |
| `400 TOOL_BODY_SOURCE_IN_ARRAY` | `x-source` ro'yxat elementlari (`items`) ichida — ularni har doim model to'ldiradi |

### 3.2. Yozuvchi tool'lar — `effect`, tasdiq, `Idempotency-Key`

Model notanish odam bilan jonli suhbatda. Shuning uchun tool nima qila olishi prompt bilan emas,
kod bilan cheklanadi — MCP'dagi `McpToolGuard` qoidasining xuddi o'zi.

| `effect` | Ma'nosi | Qo'ng'iroqda |
|---|---|---|
| `READ` | Faqat o'qiydi (holat, katalog) | Oddiy chaqiriladi |
| `WRITE` | Yozadi, lekin tuzatib bo'ladi (buyurtma, bron, lead) | `Idempotency-Key` bilan; `requiresConfirmation` bo'lsa avval tasdiqlatiladi |
| `DESTRUCTIVE` | Qaytarib bo'lmaydigan o'chirish/bekor qilish | Modelga **hech qachon** berilmaydi. Faqat §4.1 sinovidan chaqirish mumkin |

**Tasdiq (`requiresConfirmation: true`).** Tasdiq prompt'da emas, kodda tekshiriladi:

1. Model tool'ni chaqiradi. So'rov **yuborilmaydi**: argumentlar saqlab qo'yiladi, modelga
   `{"status": "CONFIRMATION_REQUIRED", "note": "TASDIQ KERAK: ..."}` qaytadi. `readbackTemplate` bo'lsa,
   jumla argumentlar bilan to'ldiriladi (ro'yxat elementma-element: `"A1 2, B7 1"`) va mijozga o'qiladi.
2. Mijoz javob beradi.
3. Model tool'ni **aynan shu argumentlar bilan** qayta chaqiradi. So'rov faqat quyidagi uch shart
   bajarilganda ketadi: argumentlar bir xil, tasdiq so'ralgandan keyin mijoz gapirgan, va mijozning
   oxirgi gapi aniq "ha" ("ha", "to'g'ri", "xo'p", "tasdiqlayman", "да", "верно", …). Qisqa javob bo'lishi
   va unda inkor ("yo'q", "emas", "нет") bo'lmasligi kerak.
4. Aks holda, masalan mijoz "3 ta emas, 2 ta" deb o'zgartirsa, model yangi argumentlar bilan chaqiradi.
   Yangi argumentlar ham qayta tasdiqlanadi, so'rov esa bitta ketadi — oxirgi tasdiqlangani.

Bunday tool'ning tavsifiga modelga "ma'lumot to'liq bo'lishi bilan chaqiring, tizim o'zi tasdiqlatadi"
degan izoh avtomatik qo'shiladi. §4.1 sinovida tasdiq so'ralmaydi — u yerda tasdiqlaydigan mijoz yo'q.

**`Idempotency-Key`.** Har `WRITE` so'roviga avtomatik qo'shiladi (tool'ning o'zida bu header bo'lmasa):
`<call_attempt_id>:<tool_nomi>:<shu qo'ng'iroqdagi chaqiruv tartibi>`. Bitta chaqiruvning barcha
retry'larida kalit bir xil, shuning uchun kalitni hurmat qiladigan endpoint takroriy `POST` ni ikkinchi
buyurtma qilmaydi. Sinovda kalit har safar yangi bo'ladi (`test-<uuid>:...`).

**Mavjud tool'lar.** Migratsiya (V10) ularga `effect` ni metoddan qo'ydi (`GET` → `READ`, boshqasi →
`WRITE`) va `requiresConfirmation: false` qildi, ya'ni bugun ishlayotgan qo'ng'iroq o'zgarmaydi.
Tasdiqni yoqish uchun `PUT` da `"requiresConfirmation": true` yuboring.

Kompaniya tool'lari hozircha faqat cascade pipeline'da chaqiriladi — realtime engine ularni taklif qilmaydi.

### 3.3. `tool_execution` — so'rovlar jurnali

Tarmoqqa chiqqan har bir so'rov uchun bitta qator yoziladi: `tool_id`, `call_attempt_id`, `tool_name`,
`status` (`OK` — 2xx, `HTTP_ERROR` — boshqa javob, `FAILED` — javob kelmadi), `http_status`,
`latency_ms`, `args_hash`, `error`.

- Argumentlar matn sifatida emas, `args_hash` (kanonik JSON'ning SHA-256) sifatida saqlanadi. Ularda
  mijozning ismi, telefoni va manzili bo'ladi, jurnalga esa bir xil so'rovlarni ajrata olish yetarli.
- `HTTP_ERROR` da javobning dastlabki 500 belgisi `error` ga yoziladi (`{{secrets.*}}` yashirilgan) —
  endpoint nimani yoqtirmagani aynan o'sha yerda yoziladi.
- Yuborilmagan chaqiruvlar yozilmaydi: tasdiq kutilayotgani, `maxCallsPerCall` oshgani va manzil
  rad etilgani.
- §4.1 sinovlari ham yoziladi, `call_attempt_id` bo'sh qoladi.
- Panel jurnalni §8 dagi endpointlar orqali o'qiydi; undan qo'ng'iroq sifati (`toolErrorCount`,
  [call-reviews.md](call-reviews.md)) va kunlik tahlil (D.3) ham foydalanadi.

### 3.4. `auth` — OAuth 2.0 client credentials

API kalit (header yoki query'da) va doimiy Bearer token uchun alohida sozlama kerak emas: `{{secrets.KEY}}` ni
`http.headers` ga (`"Authorization": "Bearer {{secrets.CRM_TOKEN}}"`) yoki `http.url` ga (`...?api_key={{secrets.CRM_KEY}}`)
yozish yetadi. Alohida tur faqat tool **o'zi token olishi** kerak bo'lganda — OAuth 2.0 `client_credentials`:

```json
"auth": {
  "type": "OAUTH2_CLIENT_CREDENTIALS",
  "tokenUrl": "https://crm.example.uz/oauth/token",
  "clientIdSecret": "CRM_CLIENT_ID",
  "clientSecretSecret": "CRM_CLIENT_SECRET",
  "scope": "leads.write"
}
```

| Maydon | Ma'nosi |
|---|---|
| `type` | Hozircha faqat `OAUTH2_CLIENT_CREDENTIALS` |
| `tokenUrl` | Token endpoint'i; `{{secrets.KEY}}` bo'lishi mumkin. `http.url` bilan bir xil tekshiriladi (ommaviy manzil, §6.1) |
| `clientIdSecret` / `clientSecretSecret` | Client id va secret saqlangan [secret](secrets.md) **kalitlari** — qiymatlarning o'zi emas |
| `scope` | So'raladigan scope'lar, bo'shliq bilan; ixtiyoriy |

- Token `POST tokenUrl` bilan olinadi: `grant_type=client_credentials`, client id/secret — `Authorization: Basic`
  (RFC 6749 §2.3.1). Javobdagi `access_token` har so'rovga `Authorization: Bearer ...` bo'lib qo'yiladi; tool'ning
  `http.headers` dagi `Authorization` bu holda e'tiborga olinmaydi.
- Token instance xotirasida keshlanadi va `expires_in` tugashidan 60 soniya oldin yangilanadi (`expires_in`
  bo'lmasa — 1 soat). API `401` qaytarsa token tashlanadi, keyingi chaqiruv yangisini oladi.
- Token olinmasa (secret yo'q, `4xx`, javobda `access_token` yo'q) so'rov yuborilmaydi: qo'ng'iroqda model oddiy
  xato natijasini oladi, §4.1 sinovida — `502 TOOL_EXECUTION_FAILED` sababi bilan, `tool_execution` ga `FAILED`.

| Xato | Qachon |
|---|---|
| `400 TOOL_AUTH_TYPE_REQUIRED` | `auth` berilgan, `type` yo'q |
| `400 TOOL_AUTH_TOKEN_URL_REQUIRED` | `tokenUrl` bo'sh |
| `400 TOOL_AUTH_SECRET_REQUIRED` | `clientIdSecret` yoki `clientSecretSecret` bo'sh |
| `400 TOOL_URL_INVALID` | `tokenUrl` ommaviy manzil emas |

---

## 4. `POST /api/tools/filter` — Qidiruv va sahifalash

`orders` kalitlari: `CREATED_AT`, `UPDATED_AT`, `NAME` (katta-kichik harf va `_` farqi yo'q —
`createdAt` ham bo'ladi). Boshqa maydon — `400 SORT_FIELD_UNKNOWN` (ilgari `500` edi, hatto
hujjatdagi `CREATED_AT` misoli ham).

```json
{
  "page": 0,
  "size": 20,
  "orders": { "CREATED_AT": "DESC" },
  "search": "delivery",
  "method": "GET"
}
```

**Response:**
```json
{
  "accept": true,
  "data": {
    "data": [
      {
        "id": 1,
        "name": "check_delivery_status",
        "description": "Mijozning buyurtma raqami bo'yicha yetkazib berish holatini aniqlash",
        "http": {
          "url": "https://api.mycompany.uz/v1/orders/{order_id}/track",
          "method": "GET",
          "responseTimeoutSecs": 5,
          "...": "..."
        },
        "speech": {
          "disableInterruptions": true,
          "forcePreToolSpeech": true,
          "preToolSpeech": "Bir daqiqa kutasizmi, buyurtmangiz holatini bazadan tekshirib ko'raman."
        },
        "createdAt": "2026-09-06T12:00:00Z",
        "updatedAt": "2026-09-06T12:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "total": 1,
    "totalPages": 1
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

---

## 4.1. `POST /api/tools/{id}/test` — Sinab ko'rish

Toolni **haqiqatan** bir marta chaqiradi (yozuvchi tool, masalan lead yaratuvchi `POST`, testda ham yozadi)
va modelga qanday natija qaytishini ko'rsatadi: `responseMapping`, tozalash va 4000 belgi chegarasi
qo'llangan holda. Manzil tekshiruvi (§6.1) va retry ham xuddi qo'ng'iroqdagidek; `maxCallsPerCall` hisoblanmaydi,
`requiresConfirmation` so'ralmaydi (§3.2), `bodySchema` bo'lsa body undan yig'iladi (§3.1). Har bir sinov `tool_execution` ga yoziladi (§3.3).

```json
{
  "args": { "order_id": "481516" },
  "variables": { "phone": "+998901234567" }
}
```

`args` — `LLM Prompt` parametrlari (model nima yuborardi), `variables` — `Dynamic Variable` parametrlari
o'qiydigan qo'ng'iroq o'zgaruvchilari. Body ixtiyoriy.

```json
{
  "accept": true,
  "data": {
    "result": { "status": 200, "data": { "order_status": "shipped", "eta": "2026-09-15" } }
  }
}
```

Topilmasa `404 TOOL_NOT_FOUND`. Endpoint xato qaytsa ham `200` — `result.status` endpoint javobi.
So'rovning o'zi yetib bormasa (ulanib bo'lmadi, timeout, noto'g'ri manzil) — `502 TOOL_EXECUTION_FAILED`,
xabarda sabab bilan (`{{secrets.*}}` qiymatlari yashirilgan). Qo'ng'iroq paytida esa model buni ko'rmaydi —
unga `{"status":500,"error":"Tool execution failed"}` boradi.

---

## 5. `DELETE /api/tools/{id}` — O'chirish

Agar ushbu tool qaysidir faol AI Agentga biriktirilgan bo'lsa, o'chirishga ruxsat berilmaydi va `409 TOOL_IN_USE` qaytariladi. Oldin agentdan ajratish (`DELETE /api/ai-agents/{agentId}/tools/{toolId}`) lozim.

---

## 6. Manzil va javob cheklovlari

### 6.1. `http.url` ommaviy bo'lishi shart

Tool manzili faqat ommaviy `http(s)` manzil bo'la oladi. Loopback (`127.0.0.1`,
`localhost`), link-local (`169.254.0.0/16`), xususiy tarmoq (`10/8`, `172.16/12`,
`192.168/16`), IPv6 unique-local (`fc00::/7`) va multicast manzillar rad etiladi —
aks holda model o'z parametri bilan platformani ichki xizmatlarga so'rov yuborishga
majburlashi mumkin edi (SSRF).

- **Saqlashda** (`POST` / `PUT /api/tools/{id}`): xususiy manzil `400 TOOL_URL_INVALID`
  beradi. Ichida `{{secrets.KEY}}` yoki `{param}` bo'lgan manzil bu bosqichda
  tekshirilmaydi — u hali URL emas.
- **Chaqirilganda:** path/query parametrlari qo'yilgandan keyin manzil qayta
  tekshiriladi. O'tmasa so'rov **yuborilmaydi** va modelga oddiy xato natijasi
  qaytariladi (qaysi manzil rad etilgani modelga aytilmaydi).
- **Redirect kuzatilmaydi** — 302 orqali tekshiruvdan keyin boshqa hostga o'tish
  filtrni aylanib o'tishning odatiy yo'li.

### 6.2. Javob hajmi

| Chegara | Qiymat | Nima bo'ladi |
|---|---|---|
| O'qiladigan hajm | 256 KB | Undan keyingi oqim o'qilmasdan tashlanadi |
| Modelga beriladigan hajm | 4000 belgi | Natija qisqartiriladi va `"truncated": true` qo'shiladi |

**Tozalash.** Modelga ketadigan har bir matn qiymati (mapping natijasi yoki butun javob) `PromptSafeText`dan
o'tadi: qator uzilishlari va `[TIZIM` belgisi olib tashlanadi. Tashqi API javobi — bu tizim yozmagan matn,
va u modelning qoidalari yonida turadi; shu bilan javob ichidagi "ko'rsatma" ko'rsatma shakliga kira olmaydi.

Ya'ni katta ro'yxat qaytaradigan endpoint tool sifatida yaramaydi — u modelga
sig'maydigan kontekst beradi va baribir kesiladi. Bunday holatda endpoint tomonida
filtr/limit qo'ying.

---

## 7. OpenAPI'dan import — `/api/tools/import`

Deyarli har platformaning OpenAPI hujjati bor. Import undagi amallardan oddiy tool'lar yasaydi — keyin ular
qo'lda tahrirlanadi (`responseMapping`, `readbackTemplate`, tavsif).

**Request** (ikkala endpoint uchun bir xil):

```json
{
  "openApiUrl": "https://shop.example.uz/openapi.json",
  "openApiSpec": null,
  "operationIds": ["listProducts", "createOrder", "getOrder"],
  "baseUrl": null,
  "secretPrefix": "SHOP"
}
```

| Maydon | Ma'nosi |
|---|---|
| `openApiUrl` / `openApiSpec` | Hujjat manzili **yoki** matni (JSON yoki YAML, ≤ 10 MB) — aynan bittasi. Manzil ommaviy bo'lishi shart va faqat SSRF himoyali client orqali yuklanadi |
| `operationIds` | Qaysi amallar tool bo'ladi (≤ 100). `/import/operations` da e'tiborga olinmaydi |
| `baseUrl` | So'rovlar yuboriladigan manzil, hujjatdagi `servers` o'rniga |
| `secretPrefix` | Tool'lar o'qiydigan secret kalitlari boshi (`SHOP` → `SHOP_TOKEN`). Bo'lmasa — hujjat `info.title` idan (`Shop API` → `SHOP_API`) |

### `POST /api/tools/import/operations`

Hech narsa yaratmaydi — ro'yxat, tanlash uchun:

```json
{
  "accept": true,
  "data": [
    { "operationId": "getOrder", "method": "GET", "path": "/orders/{id}", "summary": "Order status",
      "toolName": "getOrder", "importable": true, "unsupportedReason": null, "secretKeys": ["SHOP_TOKEN"] },
    { "operationId": "upload", "method": "POST", "path": "/uploads", "summary": null,
      "toolName": null, "importable": false, "unsupportedReason": "the request body is not JSON", "secretKeys": [] }
  ]
}
```

### `POST /api/tools/import`

Tanlangan har bir amaldan bitta tool, bitta tranzaksiyada — biri o'tmasa hech biri yaratilmaydi. Tool'lar
`POST /api/tools` bilan bir xil tekshiriladi. Agentga biriktirilmaydi — `POST /api/ai-agents/{id}/tools/{toolId}`.

```json
{
  "accept": true,
  "data": {
    "tools": [ { "id": 101, "name": "getOrder", "http": { "url": "https://api.shop.example.uz/v1/orders/{id}", "...": "..." } } ],
    "secretKeys": ["SHOP_TOKEN"]
  }
}
```

`secretKeys` — tool'lar o'qiydigan secret'lar. Import ularni **yaratmaydi**: `POST /api/secrets` bilan qo'ying.

### Nima qanday o'giriladi

| Hujjatda | Tool'da |
|---|---|
| `servers[0].url` (o'zgaruvchilar `default` bilan; nisbiy bo'lsa hujjat manziliga nisbatan) + path | `http.url`. Absolyut manzil chiqmasa — `baseUrl` bering |
| `operationId` | `name` (harf/raqam/`_` dan boshqasi `_` ga). Yo'q bo'lsa — `<method>_<path>`: `get_catalog` |
| `summary` + `description` | `description` (≤ 500) |
| `in: path` / `in: query` parametrlar | `http.pathParams` / `http.queryParams`, `LLM Prompt` — model to'ldiradi; `enum` → `allowedValues`. `header`/`cookie` parametrlar olinmaydi |
| `requestBody` → `application/json` (yoki `+json`) sxemasi | `bodySchema`: lokal `$ref` lar ichiga qo'yiladi, `allOf` birlashtiriladi, `oneOf`/`anyOf` dan birinchisi, `readOnly` maydonlar (id kabi) olib tashlanadi. JSON bo'lmagan yoki obyekt bo'lmagan body — amal import qilinmaydi |
| `GET` / `POST`, `PUT`, `PATCH` / `DELETE` | `effect`: `READ` / `WRITE` (tasdiq bilan, §3.2) / `DESTRUCTIVE` (modelga berilmaydi) |
| `security` → `http bearer` | `Authorization: Bearer {{secrets.<P>_TOKEN}}` |
| `http basic` | `Authorization: Basic {{secrets.<P>_BASIC_AUTH}}` — secret'ga `base64(login:parol)` yoziladi |
| `apiKey` (header / query / cookie) | shu nomli header / `http.url` ga `?name={{secrets.<P>_API_KEY}}` / `Cookie` |
| `oauth2` `clientCredentials` | `auth` (§3.4), `<P>_CLIENT_ID`, `<P>_CLIENT_SECRET`, scope'lar talabdan |
| boshqa `oauth2` oqimlari, `openIdConnect` | `Authorization: Bearer {{secrets.<P>_TOKEN}}` — tokenni kompaniya o'zi qo'yadi |

Boshqa fayldagi `$ref` **yuklanmaydi** (tekshirilmagan manzilga so'rov bo'lardi) — u sxema ochiq obyekt bo'lib
qoladi. `responseMapping` qo'yilmaydi: javob 4000 belgigacha to'liq modelga boradi (§6.2).

| Xato | Qachon |
|---|---|
| `400 OPENAPI_SOURCE_INVALID` | `openApiUrl` va `openApiSpec` ikkalasi ham bor yoki ikkalasi ham yo'q |
| `400 OPENAPI_URL_INVALID` | Manzil ommaviy `http(s)` emas |
| `400 OPENAPI_SPEC_UNREADABLE` | Hujjat JSON ham, YAML ham emas |
| `400 OPENAPI_VERSION_UNSUPPORTED` | OpenAPI 3.x emas. Swagger 2.0 ni avval 3.x ga o'giring (masalan `converter.swagger.io`) |
| `400 OPENAPI_SERVER_MISSING` | Absolyut server manzili chiqmadi — `baseUrl` bering |
| `400 OPENAPI_OPERATIONS_REQUIRED` | `/import` da `operationIds` bo'sh |
| `400 OPENAPI_OPERATION_UNKNOWN` / `OPENAPI_OPERATION_UNSUPPORTED` | Bunday amal yo'q / tool bo'la olmaydi (sababi bilan) |
| `502 OPENAPI_FETCH_FAILED` | Hujjatni yuklab bo'lmadi (tarmoq, `non-2xx`, 10 MB dan katta) |

---

## 8. So'rovlar jurnali va statistika — `/api/tools/executions`

Jurnal qanday yozilishi — §3.3.

### `POST /api/tools/executions/filter`

Permission: `AI_AGENT_READ`. Barcha maydonlar ixtiyoriy; `null` — "istalgan".

```json
{
  "page": 0,
  "size": 50,
  "orders": { "CREATED_AT": "DESC" },
  "toolId": 12,
  "callAttemptId": 9876,
  "status": "HTTP_ERROR",
  "from": "2026-10-01T00:00:00Z",
  "to": "2026-10-08T00:00:00Z"
}
```

| Maydon | Izoh |
|---|---|
| `orders` | Ustunlar: `CREATED_AT`, `LATENCY_MS`. Default — `CREATED_AT: DESC` |
| `toolId` | Bitta tool. O'chirilgan tool qatorlarida `toolId` — `null`, ularni `toolId` bilan topib bo'lmaydi |
| `callAttemptId` | Bitta qo'ng'iroqning so'rovlari |
| `status` | `OK` / `HTTP_ERROR` / `FAILED` |
| `from` / `to` | `createdAt` oralig'i: `from` kiradi, `to` kirmaydi |

Javob — `PageableData<ToolExecution>`:

```json
{
  "id": 501,
  "companyId": 1,
  "toolId": 12,
  "callAttemptId": 9876,
  "toolName": "create_order",
  "status": "HTTP_ERROR",
  "httpStatus": 422,
  "latencyMs": 840,
  "argsHash": "9f2c…e1",
  "error": "{\"error\":\"phone is required\"}",
  "createdAt": "2026-10-07T09:12:44Z"
}
```

`callAttemptId` — `null`: so'rov §4.1 sinovidan. `httpStatus` — `null`: javob kelmagan (`FAILED`).

### `GET /api/tools/executions/stats?days=7`

Permission: `AI_AGENT_READ`. `days` — 1 dan 90 gacha, default `7`. Oxirgi `days` kun ichida biror
so'rov yuborgan har bir tool uchun bitta qator, eng ko'p so'rov yuborgani birinchi:

```json
[
  {
    "toolId": 12,
    "toolName": "create_order",
    "total": 320,
    "ok": 301,
    "httpError": 15,
    "failed": 4,
    "avgLatencyMs": 410,
    "p95LatencyMs": 1180
  }
]
```

`p95LatencyMs` — so'rovlarning 95% shu vaqtdan tez tugagan (taymaut bo'lganlari ham hisobga kiradi).
O'chirilgan tool'larning so'rovlari `toolId: null` bilan alohida qator bo'ladi.

| Xato | Qachon |
|---|---|
| `400 TOOL_STATS_DAYS_INVALID` | `days` 1–90 oralig'ida emas |
