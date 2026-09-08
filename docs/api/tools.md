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

---

## 3. `POST /api/tools` — Tool yaratish

### Request Body:
```json
{
  "name": "check_delivery_status",
  "description": "Mijozning buyurtma raqami bo'yicha yetkazib berish holatini aniqlash",
  "apiUrl": "https://api.mycompany.uz/v1/orders/{order_id}/track",
  "apiMethod": "GET",
  "apiHeaders": [
    { "key": "Authorization", "value": "Bearer my-secret-api-token" },
    { "key": "X-Company-Id", "value": "{{company_id}}" }
  ],
  "apiQueryParams": [
    { "name": "phone", "type": "string", "valueType": "Dynamic Variable", "value": "phone" }
  ],
  "apiPathParams": [
    {
      "name": "order_id",
      "type": "string",
      "valueType": "LLM Prompt",
      "description": "Mijoz aytgan 6 xonali buyurtma raqami",
      "required": true
    }
  ],
  "apiBody": null,
  "responseTimeoutSecs": 5,
  "dynamicVariables": [
    { "key": "source", "value": "voice_bot_v2" }
  ],
  "disableInterruptions": true,
  "forcePreToolSpeech": true,
  "preToolSpeech": "Bir daqiqa kutasizmi, buyurtmangiz holatini bazadan tekshirib ko'raman."
}
```

### Maydonlar tavsifi:

| Maydon | Turi | Majburiylik | Tavsif |
|---|---|---|---|
| `name` | string | **Majburiy** | Tool nomi (kichik lotin harflari va pastki chiziq tavsiya etiladi) |
| `description` | string | **Majburiy** | Tool nima vazifa bajarishi. LLM ushbu tavsifga qarab uni qachon chaqirishni hal qiladi |
| `apiUrl` | string | **Majburiy** | Chaqiriladigan REST API manzili (masalan `https://api.crm.com/leads`) |
| `apiMethod` | string | **Majburiy** | HTTP metod: `GET`, `POST`, `PUT`, `DELETE`, `PATCH` |
| `apiHeaders` | array | Ixtiyoriy | Sarlavhalar ro'yxati (`[{"key": "Authorization", "value": "..."}]`) |
| `apiQueryParams` | array | Ixtiyoriy | URL query parametrlari — parametr obyektlari ro'yxati (pastga qarang) |
| `apiPathParams` | array | Ixtiyoriy | URL ichidagi `{param}` o'zgaruvchilari — parametr obyektlari ro'yxati |
| `apiBody` | array | Ixtiyoriy | `POST`/`PUT` tanasi — parametr obyektlari ro'yxati, JSON obyektga aylantiriladi |
| `responseTimeoutSecs`| int | Standart: 10 | Maksimal kutish vaqti (sekund) |
| `dynamicVariables` | array | Ixtiyoriy | Qo'shimcha o'zgaruvchilar (`[{"key": "...", "value": "..."}]`) |
| `disableInterruptions`| bool| Standart: false| Tool javobi kutilayotganda mijoz gapini to'xtatmaslik (barge-in block) |
| `forcePreToolSpeech` | bool | Standart: false| API chaqirishdan oldin `preToolSpeech` aytilishini majburiy qilish |
| `preToolSpeech` | string| Ixtiyoriy | Chaqiruv boshlanishida robot o'qiydigan jumla ("Bir daqiqa...") |

### Parametr obyekti (`apiQueryParams`, `apiPathParams`, `apiBody` elementi):

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

`apiUrl` va `apiHeaders[].value` ichida `{{secrets.KEY}}` yozish mumkin; qiymat chaqiruv
paytida kompaniyaning [secrets](secrets.md) omboridan olinadi. Noma'lum kalit
o'zgartirilmay qoladi — shunda so'rov muvaffaqiyatsiz bo'ladi va xato ko'rinadi.

Maxfiy qiymatlar **birinchi** ochiladi, faqat keyin qo'ng'iroq o'zgaruvchilari va model
argumentlari qo'yiladi — ya'ni argument ichidagi `{{secrets.X}}` matni shunchaki matn
bo'lib qoladi. Log'da va modelga qaytadigan xatoda ular `***` bilan yashiriladi.

---

## 4. `POST /api/tools/filter` — Qidiruv va sahifalash

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
        "apiUrl": "https://api.mycompany.uz/v1/orders/{order_id}/track",
        "apiMethod": "GET",
        "responseTimeoutSecs": 5,
        "disableInterruptions": true,
        "forcePreToolSpeech": true,
        "preToolSpeech": "Bir daqiqa kutasizmi, buyurtmangiz holatini bazadan tekshirib ko'raman.",
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

## 5. `DELETE /api/tools/{id}` — O'chirish

Agar ushbu tool qaysidir faol AI Agentga biriktirilgan bo'lsa, o'chirishga ruxsat berilmaydi va `409 TOOL_IN_USE` qaytariladi. Oldin agentdan ajratish (`DELETE /api/ai-agents/{agentId}/tools/{toolId}`) lozim.

---

## 6. Manzil va javob cheklovlari

### 6.1. `apiUrl` ommaviy bo'lishi shart

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

Ya'ni katta ro'yxat qaytaradigan endpoint tool sifatida yaramaydi — u modelga
sig'maydigan kontekst beradi va baribir kesiladi. Bunday holatda endpoint tomonida
filtr/limit qo'ying.
