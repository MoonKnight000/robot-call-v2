# Voice Agent Platform — To'liq REST API Hujjatlari

Ushbu katalog **Uysot Voice Platform (robot-call-v2)** backend tizimining barcha REST kontrollerlari, mavjud endpointlari, ularning vazifalari, so'rov va javob strukturalari bo'yicha to'liq qo'llanmasidir.

---

## 1. Umumiy Arxitektura va Autentifikatsiya

Barcha REST API'lar `/api` prefiksi ostida ishlaydi (masalan, `https://voice.app.uysot.uz/api/campaigns`).

### Autentifikatsiya mexanizmlari:
1. **Bearer Token (JWT)**:
   - Panel foydalanuvchilari uchun.
   - Sarlavha: `Authorization: Bearer <accessToken>`
   - Token muddati tugaganda: `POST /api/auth/refresh`
2. **API Key (M2M / Server-to-Server)**:
   - Tashqi xizmatlar, cron yoki integratsiyalar uchun.
   - `X-Api-Key: <TOKEN>`
3. **Multi-Tenancy**:
   - Har bir so'rov foydalanuvchining joriy kompaniyasi (`CurrentCompany` / `company_id`) kontekstida xavfsiz izolyatsiyalangan holda bajariladi.

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
| [ai-agents.md](ai-agents.md) | `/api/ai-agents` | AI_AGENT_READ / AI_AGENT_EDIT | AI agentlar CRUD: senariy + ovoz + persona + LLM model + SIP trunklar. Kampaniya ham, kiruvchi marshrut ham aynan shu agentga bog'lanadi |
| [scenarios.md](scenarios.md) | `/api/scenarios` | SCENARIO_READ / SCENARIO_EDIT | Dialog stsenariylari CRUD, FSM bosqichlari, Built-in shablonlar, `{{var | fallback}}` shablonlari, `factWebhook`, `sendDtmfTones` |
| [scenario-testing.md](scenario-testing.md) | `/api/scenarios/simulate`, `/api/scenarios/{id}/test-personas` | SCENARIO_EDIT | Web Simulator, AI Persona Benchmark sinovlari, Jonli audio test |
| [knowledge-base.md](knowledge-base.md) | `/api/knowledge-base` | KNOWLEDGE_BASE_READ / KNOWLEDGE_BASE_EDIT | Tez-tez so'raladigan savollarga uch tilli tayyor javoblar katalogi. Hozircha faqat CRUD — pipeline'ga ulanmagan |
| [settings.md](settings.md) | `/api/settings` | AI_MODEL_* · ENGINE_* · VOICE_* · NOTIFICATION_SETTINGS_* · INTEGRATION_* | Speech Engine (`CASCADE`/`REALTIME`), AI model sozlamalari, TTS tezlik/ohang, Bildirishnomalar matritsasi, CRM |
| [contacts.md](contacts.md) | `/api/contacts` | CONTACT_READ / CONTACT_EDIT | Kontaktlar bazasi CRUD, CSV import/export, mijoz qo'ng'iroqlar tarixi timeline |
| [memory.md](memory.md) | `/api/memory` | CONTACT_READ / CONTACT_EDIT | Mijoz xotirasi (kompaniya + telefon): avvalgi suhbatlar xulosasi, faktlar, operator eslatmalari — agent keyingi qo'ng'iroqda ishlatadi |
| [inbound-routes.md](inbound-routes.md) | `/api/inbound-routes` | INBOUND_ROUTE_READ / INBOUND_ROUTE_EDIT | Kiruvchi DID raqamlarni **AI agentga** biriktirish, ish vaqti nazorati |
| [do-not-call.md](do-not-call.md) | `/api/donotcall` | DO_NOT_CALL_READ / DO_NOT_CALL_EDIT | Qora ro'yxat (Do Not Call), raqam qo'shish/o'chirish, avtomatik cheklov |
| [reports.md](reports.md) | `/api/reports` | REPORT_* · DASHBOARD_READ · AUDIT_READ | Qo'ng'iroqlar analitikasi, transkriptlar, audio yozuvlar (MinIO/S3), Excel eksport, AI QA ballari |
| [live.md](live.md) | `/api/live` | LIVE_READ | Real-vaqt monitoringi, SSE live audio stream, faol kanallar holati |
| [sip-trunks.md](sip-trunks.md) | `/api/sip-trunks` | SIP_TRUNK_READ / SIP_TRUNK_EDIT | Asterisk SIP trunklar CRUD, balans va ulanish holati tekshiruvi |
| [operator.md](operator.md) | `/api/operator` | OPERATOR_READ / OPERATOR_EDIT | Operator ekrani: uzatilgan qo'ng'iroq konteksti, takeover, whisper |
| [users.md](users.md) | `/api/users` | USER_READ / USER_EDIT | Kompaniya xodimlarini taklif qilish (invite), rollar, bloklash |
| [roles.md](roles.md) | `/api/roles` | ROLE_READ / ROLE_EDIT | Rollar va huquqlar: tizim rollari (DEVELOPER, ADMIN, OPERATOR, VIEWER), kompaniyaning shaxsiy rollari (maks. 10 ta), permission katalogi |
| [profile.md](profile.md) | `/api/profile` | Barcha foydalanuvchilar | Shaxsiy profil tahrirlash, avatar yuklash, faol sessiyalar, shaxsiy statistika |
| [companies.md](companies.md) | `/api/companies` | COMPANY_READ / COMPANY_EDIT · PLATFORM_ADMIN | Kompaniya ma'lumotlari, logo, ish vaqti va til parametrlari |
| [voices.md](voices.md) | `/api/tts/voices` | Barcha foydalanuvchilar | Mavjud TTS ovozlar katalogi (O'zbek, Rus, Ingliz) va dinamik hissiyotlar |
| [speech-preview.md](speech-preview.md) | `/api/tts/voices/{id}/preview`, `/api/stt/preview` | VOICE_READ | Ovozni eshitib ko'rish (matn → WAV), STT provayderni brauzer yozuvi bilan sinash (audio → transkript) |
| [files.md](files.md) | `/api/files` | Auth | MinIO/S3 fayl yuklash, audio yozuvlar va avatarlar yuklab olish |
| [search.md](search.md) | `/api/search` | Auth | Tizim bo'ylab global qidiruv (kontaktlar, kampaniyalar, qo'ng'iroqlar) |
| [notifications.md](notifications.md) | `/api/notifications` | Auth | In-app bell bildirishnomalari ro'yxati, o'qilganlik belgisi, individual toggle'lar |
| [webhooks.md](webhooks.md) | REST endpoint yo'q | Konfiguratsiya (`voice-agent.webhook.*`) | **Chiquvchi** webhooklar: qo'ng'iroq hodisalarini tashqi tizimga POST qilish + suhbat davomidagi SMS (`sendSmsNotification`) |

---

## 4. Muhim Konfiguratsiyalar va Qo'shimcha Havolalar
- **Qo'ng'iroq senariylari oqimi**: [debt-collection-flow.http](debt-collection-flow.http)
- **Misol CSV fayllari**: [manual-campaign-target.csv](manual-campaign-target.csv)
