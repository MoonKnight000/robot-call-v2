# Voice Agent Platform — To'liq REST API Hujjatlari

Ushbu katalog **Uysot Voice Platform (robot-call-v2)** backend tizimining barcha REST kontrollerlari, mavjud endpointlari, ularning vazifalari, so'rov va javob strukturalari bo'yicha to'liq qo'llanmasidir.

---

## 1. Umumiy Arxitektura va Autentifikatsiya

Barcha REST API'lar `/api` prefiksi ostida ishlaydi (masalan, `https://voice.app.uysot.uz/api/campaigns`).

### Autentifikatsiya mexanizmlari:
1. **Bearer Token (JWT)** — yagona usul:
   - Sarlavha: `Authorization: Bearer <accessToken>`
   - Token `POST /api/auth/login` dan olinadi; muddati tugaganda `POST /api/auth/refresh`.
   - **Tashqi xizmat / cron uchun — `X-Api-Key`** ([api-keys.md](api-keys.md)).
     Kalit o'z kompaniyasi nomidan ishlaydi, huquqlari cheklangan ro'yxatdan beriladi
     (konfiguratsiyani yoki billingni o'zgartira olmaydi). Ikkalasi yuborilsa Bearer
     ustun.
2. **Multi-Tenancy**:
   - Kompaniya har doim tokendan olinadi (`@CurrentCompanyId`); so'rovdagi
     `companyId` parametriga ishonilmaydi.

### Ruxsatlar (permission):
Har bir sahifaning o'z permissioni bor: `<SAHIFA>_READ` (ko'rish) va `<SAHIFA>_EDIT`
(qo'shish/tahrirlash/o'chirish). Har bir endpoint aynan bitta permissionga bog'langan;
huquq yetmasa — `403`, `messageCode: "PERMISSION_DENIED"`. Permissionlar rollar orqali
beriladi (tizim rollari: `DEVELOPER`, `ADMIN`, `OPERATOR`, `VIEWER`, `SUPERADMIN` +
kompaniyaning shaxsiy rollari) va access token ichida qisqa kod bilan yuriladi.
To'liq model va permission ro'yxati: [roles.md](roles.md).

---

## 2. Yagona Standart Javob Formati (`ResponseData<T>`)

Barcha REST javoblari quyidagi standart formatga o'ralgan:

```json
{
  "accept": true,
  "data": { ... },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

- `accept` (`boolean`): Amaliyot muvaffaqiyati (`true` yoki `false`).
- `data` (`T` | `null`): Asosiy ma'lumot obyekti yoki sahifalangan ro'yxat (`PageableData<T>`).
- `message` (`string` | `null`): Inson o'qiy oladigan xabar. **Muvaffaqiyatli javobda har doim
  `null`** — `ResponseData.ok(data)` matn yozmaydi.
- `messageCode` (`string` | `null`): Mashina kodi. **Muvaffaqiyatli javobda `null`**
  (`"SUCCESS"` degan kod yo'q); xatoda esa `ErrorCode` enum nomi — `VALIDATION_FAILED`,
  `NOT_FOUND`, `PERMISSION_DENIED`, `CAMPAIGN_NOT_FOUND` va h.k. Frontend tarjimani
  aynan shu nom bo'yicha qiladi.
- `errors` (`array<string>` | `null`): Validatsiya xatolari ro'yxati, matn ko'rinishida:
  `["phone: must not be blank"]`.

### Sahifalangan javob (`PageableData<T>`)

Ro'yxat qaytaruvchi endpointda `data` shu shaklda bo'ladi — maydon nomlari aynan
shunday (`items`/`page`/`size`/`total` **emas**):

```json
{
  "accept": true,
  "data": {
    "totalPages": 5,
    "currentPage": 0,
    "totalElements": 94,
    "data": [ /* T[] */ ]
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

So'rov tomonida esa har bir filtr `FilterInterface` ni bajaradi: `page` (standart `0`),
`size` (standart `20`, maksimum `500`) va `orders` — ustun enum'idan yo'nalishga xarita
(`{ "CREATED_AT": "DESC", "NAME": "ASC" }`). Har bir filtr o'z ruxsat etilgan ustunlar
ro'yxatiga ega, u shu modulning hujjatida ko'rsatilgan.

---

## 3. Barcha API Modullari va Hujjatlar Xaritasi

| Modul Fayli | Asosiy Path | Ruxsat (Permission) | Asosiy Vazifasi |
|---|---|---|---|
| [auth.md](auth.md) | `/api/auth` | Ochiq / Auth | Kirish, refresh token, parolni tiklash, aktivatsiya, `/me`, kompaniyani almashtirish |
| [billing.md](billing.md) | `/api/billing` | BILLING_READ / BILLING_EDIT | Tarif rejasi, balans, daqiqalar va AI token sarfi, fakturalar PDF, Payme/Click to'lov |
| [calls.md](calls.md) | `/api/calls` | CALL_* / LIVE_* | Tezkor qo'ng'iroq (`/instant`), jonli whisper/takeover, test qo'ng'iroq, brauzerdan test (`/web-test`), audio tinglash |
| [campaigns.md](campaigns.md) | `/api/campaigns` | CAMPAIGN_READ / CAMPAIGN_EDIT | Kampaniyalar CRUD (kimga va qachon qo'ng'iroq), nishonlar, CSV import, **API target source** (`/target-source`, `/targets/sync`), takroriy (CRON/DAILY) dialer, A/B variantlar va hisoboti |
| [ai-agents.md](ai-agents.md) | `/api/ai-agents` | AI_AGENT_READ / AI_AGENT_EDIT | AI agentlar CRUD: Behavior (prompt/firstMessage) + Voice/STT/TTS + Analysis + Tools + SIP trunklar |
| [tools.md](tools.md) | `/api/tools` | AI_AGENT_READ / AI_AGENT_EDIT | Agent qo'ng'iroq paytida chaqiradigan tashqi REST API Tool'lari: dinamik parametrlar, path/query/header/body, preToolSpeech |
| [secrets.md](secrets.md) | `/api/secrets` | SUPER_ADMIN / COMPANY_ADMIN | Kompaniya maxfiy kalitlari ombori (CRM tokenlar, API kalitlar). Qiymat hech qachon ochiq qaytmaydi, faqat maskalanadi |
| [widgets.md](widgets.md) | `/api/ai-agents/{id}/widgets`, `/api/widgets`, `/api/public/widgets` | AI_AGENT_* / oxirgisi ochiq | Web-saytlarga joylashtiriladigan AI ovozli vidjetlar: embed script (`/widgets/v1/widget.js`), mavzu, rozilik matni, WebRTC orqali jonli qo'ng'iroq |
| [scenarios.md](scenarios.md) | `/api/scenarios` | SCENARIO_READ / SCENARIO_EDIT | Dialog stsenariylari CRUD, FSM bosqichlari, Built-in shablonlar, `{{var | fallback}}` shablonlari, `factWebhook`, `sendDtmfTones` |
| [scenario-testing.md](scenario-testing.md) | `/api/scenarios/simulate`, `/api/scenarios/{id}/test-personas` | SCENARIO_EDIT | Web Simulator, AI Persona Benchmark sinovlari, Jonli audio test |
| [knowledge-base.md](knowledge-base.md) | `/api/knowledge-base`, `/api/knowledge-base/sources` | KNOWLEDGE_BASE_READ / KNOWLEDGE_BASE_EDIT | Bilim bazasi: qo'lda yoziladigan uch tilli javoblar va agentga biriktirilgan hujjat/URL manbalari (PDF/DOCX/XLSX/CSV/TXT/URL → indekslash → qo'ng'iroqda semantik qidiruv, `useRag` agentlar uchun) |
| [settings.md](settings.md) | `/api/settings` | AI_MODEL_* · VOICE_* · NOTIFICATION_SETTINGS_* · INTEGRATION_* | Speech Engine variantlari katalogi (pipeline'ning o'zi endi agent darajasida), AI model sozlamalari, TTS tezlik/ohang, Bildirishnomalar matritsasi, CRM |
| [memory.md](memory.md) | `/api/memory` | CONTACT_READ / CONTACT_EDIT | Mijoz xotirasi (kompaniya + telefon): avvalgi suhbatlar xulosasi, faktlar, operator eslatmalari — agent keyingi qo'ng'iroqda ishlatadi |
| [inbound-routes.md](inbound-routes.md) | `/api/inbound-routes` | INBOUND_ROUTE_READ / INBOUND_ROUTE_EDIT | Kiruvchi DID raqamlarni **AI agentga** biriktirish, ish vaqti nazorati |
| [do-not-call.md](do-not-call.md) | `/api/donotcall` | DO_NOT_CALL_READ / DO_NOT_CALL_EDIT | Qora ro'yxat (Do Not Call), raqam qo'shish/o'chirish, avtomatik cheklov |
| [reports.md](reports.md) | `/api/reports` | REPORT_* · DASHBOARD_READ · AUDIT_READ | Birlashgan Dashboard Summary (/dashboard/summary), Qo'ng'iroqlar analitikasi, transkriptlar, audio yozuvlar (MinIO/S3), Excel eksport, AI QA ballari |
| [live.md](live.md) | `/api/live` | LIVE_READ | Real-vaqt monitoringi, SSE live audio stream, faol kanallar holati |
| [sip-trunks.md](sip-trunks.md) | `/api/sip-trunks` | SIP_TRUNK_READ / SIP_TRUNK_EDIT | Asterisk SIP trunklar CRUD, balans va ulanish holati tekshiruvi |
| [operator.md](operator.md) | `/api/operator` | OPERATOR_READ / OPERATOR_EDIT | Operator ekrani: uzatilgan qo'ng'iroq konteksti, takeover, whisper |
| [users.md](users.md) | `/api/users` | USER_READ / USER_EDIT | Kompaniya xodimlarini taklif qilish (invite), rollar, bloklash |
| [roles.md](roles.md) | `/api/roles` | ROLE_READ / ROLE_EDIT | Rollar va huquqlar: tizim rollari (DEVELOPER, ADMIN, OPERATOR, VIEWER), kompaniyaning shaxsiy rollari (maks. 10 ta), permission katalogi |
| [api-keys.md](api-keys.md) | `/api/api-keys` | API_KEY_READ / API_KEY_EDIT | Tashqi tizimlar uchun `X-Api-Key` kalitlari: yaratish (kalit bir marta ko'rinadi), bekor qilish, kalit ololmaydigan huquqlar ro'yxati |
| [conversions.md](conversions.md) | `/api/conversions` | CAMPAIGN_READ / CAMPAIGN_EDIT | Konversiya maqsadlari va hodisalari: CRM "mijoz to'ladi" deb xabar beradi, tizim qaysi qo'ng'iroq/kampaniya/A-B variant keltirganini aniqlaydi; A/B hisobotiga xarajat va ROI ustunlari |
| [mcp.md](mcp.md) | `/api/mcp` | AI_AGENT_READ / AI_AGENT_EDIT | Kompaniyaning MCP serverlarini ulash: tool'lar saqlash paytida o'qiladi, buzuvchi tool'lar modelga umuman berilmaydi, har chaqiruv latency bilan loglanadi |
| [profile.md](profile.md) | `/api/profile` | Barcha foydalanuvchilar | Shaxsiy profil tahrirlash, avatar yuklash, faol sessiyalar, shaxsiy statistika |
| [companies.md](companies.md) | `/api/companies` | COMPANY_READ / COMPANY_EDIT · PLATFORM_ADMIN | Kompaniya ma'lumotlari, logo, ish vaqti va til parametrlari |
| [voices.md](voices.md) | `/api/tts/voices` | Barcha foydalanuvchilar | Mavjud TTS ovozlar katalogi (O'zbek, Rus, Ingliz) va dinamik hissiyotlar |
| [ai-models.md](ai-models.md) | `/api/ai-models` | Barcha foydalanuvchilar | Model katalogi (`kind` = `LLM`, `STT`, `TTS`) — kompaniya sozlamasi va agent `llmModel` / `fastLlmModel` / `sttModel` / `ttsModel` uchun tanlanadigan id'lar |
| [speech-preview.md](speech-preview.md) | `/api/tts/voices/{id}/preview`, `/api/stt/preview` | VOICE_READ | Ovozni eshitib ko'rish (matn → WAV), STT provayderni brauzer yozuvi bilan sinash (audio → transkript) |
| [files.md](files.md) | `/api/files` | Auth | MinIO/S3 fayl yuklash, audio yozuvlar va avatarlar yuklab olish |
| [search.md](search.md) | `/api/search` | Auth | Tizim bo'ylab global qidiruv (kontaktlar, kampaniyalar, qo'ng'iroqlar) |
| [notifications.md](notifications.md) | `/api/notifications` | Auth | In-app bell bildirishnomalari ro'yxati, o'qilganlik belgisi, individual toggle'lar |
| [webhooks.md](webhooks.md) | REST endpoint yo'q | Konfiguratsiya (`voice-agent.webhook.*`) | **Chiquvchi** webhooklar: qo'ng'iroq hodisalarini tashqi tizimga POST qilish + suhbat davomidagi SMS (`sendSmsNotification`) |

---

## 4. Muhim Konfiguratsiyalar va Qo'shimcha Havolalar
- **Qo'ng'iroq senariylari oqimi**: [debt-collection-flow.http](debt-collection-flow.http)
- **Misol CSV fayllari**: [manual-campaign-target.csv](manual-campaign-target.csv)
