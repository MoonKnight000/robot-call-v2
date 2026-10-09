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
   - Refresh token `refresh_token` HttpOnly cookie'da ham keladi (`Path=/api/auth`,
     `SameSite=Strict`); boshqa origin'dan `credentials: 'include'` bilan — [auth.md](auth.md).
     Body'dagi `refreshToken` deprecated.
   - Sarlavha qo'ya olmaydigan oqimlar (SSE, jonli audio) uchun URL'da bir martalik `?ticket=`
     ([auth.md](auth.md#post-apiauthstream-ticket--oqim-uchun-bir-martalik-ticket));
     `?token=<accessToken>` deprecated.
2. **Multi-Tenancy**:
   - Kompaniya har doim tokendan olinadi (`@CurrentCompanyId`); so'rovdagi
     `companyId` parametriga ishonilmaydi.
3. **CORS** — `CORS_ALLOWED_ORIGINS` ro'yxatidagi origin'lar uchun, credentials bilan:
   - Ruxsat etilgan so'rov sarlavhalari: `Authorization`, `Content-Type`, `X-Api-Key`, `Range`,
     `Accept`, `Accept-Language`, `Last-Event-ID`. Ro'yxatda yo'q maxsus sarlavha preflight'da
     rad etiladi — yangi sarlavha kerak bo'lsa backend'ga qo'shiladi.
   - JavaScript o'qiy oladigan javob sarlavhalari (`Access-Control-Expose-Headers`):
     `Content-Disposition`, `Content-Range`, `Accept-Ranges`, `Content-Length`, `Retry-After`
     (+ CORS doim ochiq qoldiradigan `Content-Type` va h.k.).
   - Vidjet yo'llari (`/api/public/widgets/**`, `/widgets/**`) — istalgan origin, credentials'siz
     ([widgets.md](widgets.md)).

### Ruxsatlar (permission):
Har bir sahifaning o'z permissioni bor: `<SAHIFA>_READ` (ko'rish) va `<SAHIFA>_EDIT`
(qo'shish/tahrirlash/o'chirish). Har bir endpoint aynan bitta permissionga bog'langan;
huquq yetmasa — `403`, `messageCode: "PERMISSION_DENIED"`. Permissionlar rollar orqali
beriladi (tizim rollari: `DEVELOPER`, `ADMIN`, `OPERATOR`, `SUPERVISOR`, `VIEWER`, `SUPERADMIN` +
kompaniyaning shaxsiy rollari) va access token ichida qisqa kod bilan yuriladi.
To'liq model va permission ro'yxati: [roles.md](roles.md).

Kontakt-markaz sahifalarining permissionlari: `CALL_FLOW_READ` / `CALL_FLOW_EDIT` (call flow,
ish jadvallari, bayramlar, ovozli pochta qutilari), `CALL_QUEUE_READ` / `CALL_QUEUE_EDIT`
(operator navbatlari), `MISSED_CALL_READ` / `MISSED_CALL_EDIT` (o'tkazib yuborilgan qo'ng'iroqlar
va ovozli xabarlar). Istisno — **`CALL_MONITOR`**: `_READ`/`_EDIT` juftisiz yakka huquq.
U supervayzerga boshqa odamning jonli qo'ng'irog'ini tinglash, pichirlash (whisper) yoki unga
qo'shilish (barge) imkonini beradi ([calls.md](calls.md)). `SUPERVISOR` roli — operator
huquqlari + `CALL_MONITOR`, navbatlarni tahrirlash (`CALL_QUEUE_EDIT`), call flow'ni ko'rish
(`CALL_FLOW_READ`) va xodimlarni ko'rish (`USER_READ`).

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
  `["phone: must not be blank"]`. Faqat `@Valid` emas — bir vaqtda bir nechta muammo topadigan
  domen validatorlari ham (ssenariy `definition`i, call flow grafi va h.k.) `400` bilan har bir
  muammoni shu ro'yxatga alohida qator qilib yozadi; bunda `messageCode` nima tekshirilganini
  aytadi (`SCENARIO_DEFINITION_INVALID`, `CALL_FLOW_INVALID`), `message` esa muammolar sonini.
  Boshqa xatolarda `errors` — `null`.

HTTP status → `messageCode` oilasi: `400` (validatsiya), `401` (token yo'q/yaroqsiz/bekor
qilingan), `403` (ruxsat yo'q), `404` (topilmadi yoki boshqa kompaniyaniki), `409` (holat mos
emas — serverda kerakli sozlama yo'qligi ham shu yerga kiradi: `ENCRYPTION_KEY_NOT_SET`,
`JWT_KEY_NOT_SET`, `WEB_TEST_NOT_CONFIGURED`, ular avval `502` edi), **`429`** (`RATE_LIMITED` — bitta IP dan juda ko'p so'rov; `LOGIN_TEMPORARILY_LOCKED` —
username shu IP uchun vaqtincha qulflangan; javobda `Retry-After` sarlavhasi bor), `502` (tashqi xizmat).
`429` faqat tokensiz endpointlarda: `/api/auth/login|refresh|forgot-password|reset-password`
va `/api/public/widgets/**` ([auth.md](auth.md), [widgets.md](widgets.md)).

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
| [auth.md](auth.md) | `/api/auth` | Ochiq / Auth | Kirish, refresh token (cookie), parolni tiklash, aktivatsiya, `/me`, oqim ticket'i (`/stream-ticket`), kompaniyani almashtirish |
| [billing.md](billing.md) | `/api/billing` | BILLING_READ / BILLING_EDIT | Tarif rejasi, balans, daqiqalar va AI token sarfi, fakturalar PDF, Payme/Click to'lov |
| [billing.md §7](billing.md#7-post-apibillingpaymecallback--payme-merchant-api) | `/api/billing/payme/callback` | Ochiq · HTTP Basic `Paycom:<key>` | **Payme serverlari chaqiradi**, frontend emas. JSON-RPC 2.0 (CheckPerform/Create/Perform/Cancel/CheckTransaction, GetStatement); javob `ResponseData` emas — Payme formatida, HTTP har doim 200 |
| [billing.md §8](billing.md#8-post-apibillingclickprepare--complete--click-shop-api) | `/api/billing/click/prepare`, `/api/billing/click/complete` | Ochiq · md5 `sign_string` | **Click serverlari chaqiradi**, frontend emas. Form-urlencoded Prepare/Complete; javob `ResponseData` emas — Click formatida |
| [concurrency.md](concurrency.md) | `/api/concurrency` | LIVE_READ | Kompaniyaning parallel dialer qo'ng'iroqlari: limit (tarif yoki platforma standarti), band va bo'sh slotlar; limitga yetganda `CONCURRENCY_LIMIT_REACHED` bildirishnomasi |
| [calls.md](calls.md) | `/api/calls` | CALL_* / LIVE_* · CALL_MONITOR · OPERATOR_EDIT | Tezkor qo'ng'iroq (`/instant`), jonli whisper/takeover, test qo'ng'iroq, brauzerdan test (`/web-test`), audio tinglash; operator boshqaruvi: transfer (navbat / hamkasb / tashqi raqam), hold/unhold, konsultatsiya (`/consult`, `/consult/complete`, `/consult/cancel`), supervayzer monitoringi (`/monitor` — listen/whisper/barge), click-to-call (`/click-to-call`) |
| [campaigns.md](campaigns.md) | `/api/campaigns` | CAMPAIGN_READ / CAMPAIGN_EDIT | Kampaniyalar CRUD (kimga va qachon qo'ng'iroq), nishonlar, CSV import, **API target source** (`/target-source`, `/targets/sync`), takroriy (CRON/DAILY) dialer, A/B variantlar va hisoboti |
| [ai-agents.md](ai-agents.md) | `/api/ai-agents` | AI_AGENT_READ / AI_AGENT_EDIT | AI agentlar CRUD: Behavior (prompt/firstMessage) + Voice/STT/TTS + Analysis + Tools + SIP trunklar; versiyalar, diff, rollout (nomzod versiya, trafik foizi, staging raqamlari, promote/cancel) |
| [tools.md](tools.md) | `/api/tools` | AI_AGENT_READ / AI_AGENT_EDIT | Agent qo'ng'iroq paytida chaqiradigan tashqi REST API Tool'lari: dinamik parametrlar, path/query/header/body, preToolSpeech, OAuth2 client credentials (`auth`), OpenAPI 3 hujjatidan import (`/import`), so'rovlar jurnali va statistika (`/executions/filter`, `/executions/stats`) |
| [packs.md](packs.md) | `/api/packs` | AI_AGENT_READ / AI_AGENT_EDIT | Soha paketlari: tayyor agent + ssenariy + tool'lar + katalog + test holatlari bitta so'rov bilan o'rnatiladi (`/{key}/install`); hozircha `ecommerce` (magazin buyurtmasi), `clinic` (klinika bron), `restaurant` (menyu + yetkazib berish), `lead` (kvalifikatsiya, OAuth CRM), `support` (1-liniya + tiket), `nps` (so'rovnoma, sozlashsiz). Katalogda yo'q biznes uchun — `/generate` (biznes tavsifi + OpenAPI → paket qoralamasi) va `/install-draft`. Mavjud agentni paket JSON'i sifatida olish — `/agents/{agentId}/export` (config-as-code) |
| [secrets.md](secrets.md) | `/api/secrets` | INTEGRATION_READ / INTEGRATION_EDIT | Kompaniya maxfiy kalitlari ombori (CRM tokenlar, API kalitlar). Qiymat hech qachon ochiq qaytmaydi, faqat maskalanadi |
| [widgets.md](widgets.md) | `/api/ai-agents/{id}/widgets`, `/api/widgets`, `/api/public/widgets` | AI_AGENT_* / oxirgisi ochiq | Web-saytlarga joylashtiriladigan AI ovozli vidjetlar: embed script (`/widgets/v1/widget.js`), mavzu, rozilik matni, WebRTC orqali jonli qo'ng'iroq |
| [chat.md](chat.md) | `/api/public/widgets/{key}/chat`, `/api/telegram-bots`, `/api/public/telegram/{botId}`, `/api/chats` | Ochiq (Origin / secret header) · AI_AGENT_* · REPORT_READ | Vidjetning matn kanali: o'sha agent bilan yozishma — bir xil ssenariy, tool'lar va guardrail'lar, audiosiz. Suhbat bazada saqlanadi, har xabarda qayta yig'iladi. Panel suhbatlarni `/api/chats` orqali o'qiydi (ro'yxat + transkript) |
| [scenarios.md](scenarios.md) | `/api/scenarios` | SCENARIO_READ / SCENARIO_EDIT | Dialog stsenariylari CRUD, FSM bosqichlari, Built-in shablonlar, `{{var | fallback}}` shablonlari, `factWebhook`, `sendDtmfTones` |
| [scenario-testing.md](scenario-testing.md) | `/api/scenarios/simulate`, `/api/scenarios/{id}/test-personas` | SCENARIO_EDIT | Web Simulator, AI Persona Benchmark sinovlari, Jonli audio test |
| [knowledge-base.md](knowledge-base.md) | `/api/knowledge-base`, `/api/knowledge-base/sources` | KNOWLEDGE_BASE_READ / KNOWLEDGE_BASE_EDIT | Bilim bazasi: qo'lda yoziladigan uch tilli javoblar va agentga biriktirilgan hujjat/URL manbalari (PDF/DOCX/XLSX/CSV/TXT/URL → indekslash → qo'ng'iroqda semantik qidiruv, `useRag` agentlar uchun) |
| [settings.md](settings.md) | `/api/settings` | AI_MODEL_* · VOICE_* · NOTIFICATION_SETTINGS_* · INTEGRATION_* | Speech Engine variantlari katalogi (pipeline'ning o'zi endi agent darajasida), AI model sozlamalari (kompaniyaning o'z OpenAI-compatible LLM'i — `own-llm`), TTS tezlik/ohang, Bildirishnomalar matritsasi, CRM |
| [uysot-oauth.md](uysot-oauth.md) | `/api/settings/integrations/uysot/*` | INTEGRATION_READ / INTEGRATION_EDIT · callback ochiq | **Frontend qo'llanmasi:** Uysot CRM'ni OAuth orqali ulash oqimi — `authorize-url` → Uysot → frontend `?code&state` → backend callback → `?crm=connected` |
| [memory.md](memory.md) | `/api/memory` | CONTACT_READ / CONTACT_EDIT | Mijoz xotirasi (kompaniya + telefon): avvalgi suhbatlar xulosasi, faktlar, operator eslatmalari — agent keyingi qo'ng'iroqda ishlatadi |
| [inbound-routes.md](inbound-routes.md) | `/api/inbound-routes`, `/api/inbound-caller-rules` | INBOUND_ROUTE_READ / INBOUND_ROUTE_EDIT | Kiruvchi DID raqamlarni **AI agentga** biriktirish, ish vaqti nazorati; kiruvchi caller qoidalari — qora ro'yxat va VIP (`/api/inbound-caller-rules`) |
| [call-flows.md](call-flows.md) | `/api/call-flows` | CALL_FLOW_READ / CALL_FLOW_EDIT | Kiruvchi qo'ng'iroq oqimi (IVR / routing grafi) CRUD: qoralamani saqlash, tekshirish (`/{id}/validate`), nashr qilish (`/{id}/publish`) — qo'ng'iroqlar faqat nashr qilingan versiyada yuradi |
| [schedules.md](schedules.md) | `/api/schedules`, `/api/holidays` | CALL_FLOW_READ / CALL_FLOW_EDIT | Ish jadvallari va bayram kunlari: jadvalning hozirgi holati (`/schedules/{id}/status`), yilning belgilangan sanali bayramlarini qo'shish (`/holidays/fixed`). Flow'ning SCHEDULE tuguni va route ish vaqti shularga qaraydi |
| [voicemail.md](voicemail.md) | `/api/voicemail-boxes`, `/api/voicemails` | CALL_FLOW_* (qutilar) · MISSED_CALL_* (xabarlar) | Ovozli pochta qutilari CRUD; qoldirilgan xabarlar ro'yxati, holatini o'zgartirish, audio tinglash (WAV, `Range`) |
| [call-queues.md](call-queues.md) | `/api/call-queues` | CALL_QUEUE_READ / CALL_QUEUE_EDIT | Operator navbatlari va ularning a'zolari CRUD, jonli holat (`/live`) va wallboard SSE (`/live/stream`, har 2 soniyada `QUEUE_STATS`) |
| [wrap-up-codes.md](wrap-up-codes.md) | `/api/wrap-up-codes`, `/api/calls/{callId}/wrap-up` | OPERATOR_READ · CALL_QUEUE_EDIT · OPERATOR_EDIT | Operator qo'ng'iroqdan keyin tanlaydigan natija kodlari (kompaniya bo'yicha bitta ro'yxat, har biri `DONE` / `RETRY` / `EXHAUSTED`) va qo'ng'iroqni shu kod bilan yopish; `WRAP_UP` holatini darhol tugatadi |
| [preview-dialing.md](preview-dialing.md) | `/api/operator/campaigns/{campaignId}` | OPERATOR_EDIT | PREVIEW kampaniya: operator navbatdagi mijoz kartasini oladi (`next`), teradi (`dial`) yoki qaytaradi (`skip`); qo'ng'iroq wrap-up bilan yopiladi va kartani yopadi |
| [missed-calls.md](missed-calls.md) | `/api/missed-calls` | MISSED_CALL_READ / MISSED_CALL_EDIT | O'tkazib yuborilgan va tashlab ketilgan qo'ng'iroqlar — operatorlarning ish ro'yxati: tahrirlash, operatorga biriktirish (`/{id}/assign`), qayta qo'ng'iroq (`/{id}/call-back` — avval operator softfoni jiringlaydi) |
| [do-not-call.md](do-not-call.md) | `/api/donotcall` | DO_NOT_CALL_READ / DO_NOT_CALL_EDIT | Qora ro'yxat (Do Not Call), raqam qo'shish/o'chirish, avtomatik cheklov |
| [reports.md](reports.md) | `/api/reports` | REPORT_* · DASHBOARD_READ · AUDIT_READ · CALL_READ | Birlashgan Dashboard Summary (/dashboard/summary), Qo'ng'iroqlar analitikasi, transkriptlar, audio yozuvlar (MinIO/S3), Excel eksport, AI QA ballari; kontakt-markaz: navbatlar (`/queues` — SL, ASA, abandon rate, overflow) va operatorlar (`/operators` — RONA, suhbat vaqti, occupancy) hisoboti, qo'ng'iroq timeline'i (`/calls/{callId}/events`); PREDICTIVE kampaniya natijalari (`/campaigns/{campaignId}/predictive` — operatorga ulangan, AI overflow, abandon); operator qo'ng'iroqlari sifati (`/quality/*` — operatorlar reytingi, mezonlar trendi, eng ko'p buzilgan mezonlar, mavzular) |
| [scorecards.md](scorecards.md) | `/api/scorecard-criteria`, `/api/calls/{callId}/scorecard`, `/api/call-scorecards` | QUALITY_REVIEW_READ / QUALITY_REVIEW_EDIT | Operator qo'ng'iroqlarini baholash: kompaniya mezonlari (og'irlik 1–10), har bir operator qo'ng'irog'iga LLM javobi (`YES` / `NO` / `NOT_APPLICABLE` + dalil), 0–100 ball va mijoz kayfiyati; past ball yoki norozi mijoz — tekshiruv inbox'i, supervisor tasdiqlaydi yoki tuzatadi |
| [call-reviews.md](call-reviews.md) | `/api/call-reviews` | QUALITY_REVIEW_READ / QUALITY_REVIEW_EDIT | Review inbox: sifat hakami shubhali deb topgan AI qo'ng'iroqlar, supervayzer bahosi (`AGENT_CORRECT` / `AGENT_WRONG`), shu qo'ng'iroqdan KB javobi yoki test holati; har qo'ng'iroqning sifat qatori (baho + LLM'siz signallar) |
| [agent-insights.md](agent-insights.md) | `/api/agent-insights` | AI_AGENT_READ / AI_AGENT_EDIT | Yaxshilanish inbox'i: kunlik job qo'ng'iroqlardan topgan takliflar (`PROMPT_FIX`, `KB_GAP`, `TOOL_FIX`, …), qabul/rad; qabul qilingan prompt o'zgarishi eval gate orqali o'tadi |
| [call-reanalyses.md](call-reanalyses.md) | `/api/call-reanalyses` | AI_AGENT_READ / AI_AGENT_EDIT | Eski qo'ng'iroqlarni agentning hozirgi `dataNeeded` / `dataEvaluation` sozlamalari bilan qayta tahlil qilish: taxminiy narx (`/preview`), fon ishi, progress, bekor qilish. Pullik — har qo'ng'iroq tokenlari yechiladi |
| [alert-rules.md](alert-rules.md) | `/api/alert-rules` | NOTIFICATION_SETTINGS_READ / NOTIFICATION_SETTINGS_EDIT | Kompaniya o'zi sozlaydigan alert qoidalari: metrika (success/transfer/xato/norozi mijoz ulushi, latency, tool xatolari, qo'ng'iroqlar soni), chegara, oyna, cooldown; buzilsa `ALERT_TRIGGERED` bildirishnomasi (bell + Telegram/email/webhook), `/preview` |
| [live.md](live.md) | `/api/live` | LIVE_READ | Real-vaqt monitoringi, SSE live audio stream, faol kanallar holati |
| [sip-trunks.md](sip-trunks.md) | `/api/sip-trunks` | SIP_TRUNK_READ / SIP_TRUNK_EDIT | Asterisk SIP trunklar CRUD, balans va ulanish holati tekshiruvi |
| [operator.md](operator.md) | `/api/operator` | OPERATOR_READ / OPERATOR_EDIT | Operator ekrani: uzatilgan qo'ng'iroq konteksti, takeover, whisper |
| [operators.md](operators.md) | `/api/operators` | OPERATOR_READ / OPERATOR_EDIT · USER_EDIT (SIP akkaunt) | Operatorlar: foydalanuvchiga SIP akkaunt berish / parolni tiklash / o'chirish (USER_EDIT), operatorlar ro'yxati, softfon uchun o'z SIP ma'lumotlari (`/me/sip-credentials`), o'z holati (`/me/status`), SSE hodisalar (`/me/events` — `CALL_OFFER`, `CALL_CONNECTED`, `CALL_ENDED`, `STATUS_CHANGED`, `MISSED_CALL`, `VOICEMAIL`) |
| [users.md](users.md) | `/api/users` | USER_READ / USER_EDIT | Kompaniya xodimlarini taklif qilish (invite), rollar, bloklash |
| [roles.md](roles.md) | `/api/roles` | ROLE_READ / ROLE_EDIT | Rollar va huquqlar: tizim rollari (DEVELOPER, ADMIN, OPERATOR, SUPERVISOR, VIEWER), kompaniyaning shaxsiy rollari (maks. 10 ta), permission katalogi |
| [api-keys.md](api-keys.md) | `/api/api-keys` | API_KEY_READ / API_KEY_EDIT | Tashqi tizimlar uchun `X-Api-Key` kalitlari: yaratish (kalit bir marta ko'rinadi), bekor qilish, kalit ololmaydigan huquqlar ro'yxati |
| [conversions.md](conversions.md) | `/api/conversions` | CAMPAIGN_READ / CAMPAIGN_EDIT | Konversiya maqsadlari va hodisalari: CRM "mijoz to'ladi" deb xabar beradi, tizim qaysi qo'ng'iroq/kampaniya/A-B variant keltirganini aniqlaydi; A/B hisobotiga xarajat va ROI ustunlari |
| [payment-promises.md](payment-promises.md) | `/api/payment-promises` | CALL_READ / CALL_EDIT / CAMPAIGN_EDIT | To'lov va'dalari sikli: qo'ng'iroqdagi va'da avtomatik ochiladi, sanadan oldin eslatma SMS, conversion hodisasi kelsa `KEPT`, muddat o'tsa `BROKEN` va nishonni qayta qo'ng'iroqqa qo'yish; kept-PTP statistikasi, qo'lda belgilash, kompaniya sozlamalari |
| [mcp.md](mcp.md) | `/api/mcp` | AI_AGENT_READ / AI_AGENT_EDIT | Kompaniyaning MCP serverlarini ulash: tool'lar saqlash paytida o'qiladi, buzuvchi tool'lar modelga umuman berilmaydi, har chaqiruv latency bilan loglanadi |
| [copilot.md](copilot.md) | `/api/copilot` | AI_AGENT_READ yoki REPORT_READ | Panel AI yordamchisi: oddiy tilda savol — qo'ng'iroqlar, transkriptlar va agentlarni MCP tool'lari orqali o'qib javob beradi, agent o'zgarishini faqat qoralamaga yozadi (publish — odam). Suhbat tarixi saqlanadi, tokenlar balansdan yechiladi |
| [mcp-server.md](mcp-server.md) | `/api/mcp-server` | har tool o'z huquqi | Platformaning o'z MCP serveri (Streamable HTTP, API kalit): Claude Code/Cursor agent yaratadi, eval yurgizadi, qo'ng'iroq va transkriptlarni o'qiydi, agentni paket sifatida export/import qiladi. `AGENT_DRAFT_EDIT` — faqat qoralama, publish panelda |
| [profile.md](profile.md) | `/api/profile` | Barcha foydalanuvchilar | Shaxsiy profil tahrirlash, avatar yuklash, faol sessiyalar, shaxsiy statistika |
| [companies.md](companies.md) | `/api/companies` | COMPANY_READ / COMPANY_EDIT · PLATFORM_ADMIN | Kompaniya ma'lumotlari, logo, ish vaqti va til parametrlari |
| [voices.md](voices.md) | `/api/tts/voices` | Barcha foydalanuvchilar | Mavjud TTS ovozlar katalogi (O'zbek, Rus, Ingliz) va dinamik hissiyotlar |
| [ai-models.md](ai-models.md) | `/api/ai-models` | Barcha foydalanuvchilar | Model katalogi (`kind` = `LLM`, `STT`, `TTS`) — kompaniya sozlamasi va agent `llmModel` / `fastLlmModel` / `sttModel` / `ttsModel` uchun tanlanadigan id'lar |
| [speech-preview.md](speech-preview.md) | `/api/tts/voices/{id}/preview`, `/api/stt/preview` | VOICE_READ | Ovozni eshitib ko'rish (matn → WAV), STT provayderni brauzer yozuvi bilan sinash (audio → transkript) |
| [files.md](files.md) | `/api/files` | Auth | MinIO/S3 fayl yuklash, audio yozuvlar va avatarlar yuklab olish |
| [search.md](search.md) | `/api/search` | CALL_READ / CAMPAIGN_READ | Tizim bo'ylab global qidiruv (kontaktlar, kampaniyalar, qo'ng'iroqlar) |
| [notifications.md](notifications.md) | `/api/notifications` | Auth | In-app bell bildirishnomalari ro'yxati, o'qilganlik belgisi, individual toggle'lar |
| [webhooks.md](webhooks.md) | `/api/webhook-deliveries` | AI_AGENT_READ / AI_AGENT_EDIT | **Chiquvchi** webhooklar (sozlanishi agent / bildirishnoma / call-flow da); yiqilgan yetkazishlar jurnali va `FAILED` ni qo'lda qayta yuborish (`/{id}/resend`): qayerda sozlanadi, imzo (`X-RobotCall-Signature`), `X-RobotCall-Delivery`, qayta urinish, SSRF himoyasi + suhbat davomidagi SMS |

---

## 4. Muhim Konfiguratsiyalar va Qo'shimcha Havolalar
- **Qo'ng'iroq senariylari oqimi**: [debt-collection-flow.http](debt-collection-flow.http)
- **Misol CSV fayllari**: [manual-campaign-target.csv](manual-campaign-target.csv)
