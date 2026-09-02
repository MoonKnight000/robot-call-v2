# Voice Agent Platform — To'liq REST API Hujjatlari

Ushbu katalog **Uysot Voice Platform (robot-call-v2)** backend tizimining barcha 26 ta REST kontrollerlari, mavjud endpointlari, ularning vazifalari, so'rov va javob strukturalari bo'yicha to'liq qo'llanmasidir.

---

## 1. Umumiy Arxitektura va Autentifikatsiya

Barcha REST API'lar `/api` prefiksi ostida ishlaydi (masalan, `https://voice.app.uysot.uz/api/campaigns`).

### Autentifikatsiya mexanizmlari:
1. **Bearer Token (JWT)**:
   - Panel foydalanuvchilari (ADMIN, OPERATOR, SUPERADMIN) uchun.
   - Sarlavha: `Authorization: Bearer <accessToken>`
   - Token muddati tugaganda: `POST /api/auth/refresh`
2. **API Key (M2M / Server-to-Server)**:
   - Tashqi xizmatlar, cron yoki integratsiyalar uchun.
   - `X-Api-Key: <TOKEN>`
3. **Multi-Tenancy**:
   - Har bir so'rov foydalanuvchining joriy kompaniyasi (`CurrentCompany` / `company_id`) kontekstida xavfsiz izolyatsiyalangan holda bajariladi.

---

## 2. Yagona Standart Javob Formati (`ResponseData<T>`)

Barcha REST javoblari quyidagi standart formatga o'ralgan:

```json
{
  "accept": true,
  "data": { ... },
  "message": "Operatsiya muvaffaqiyatli bajarildi",
  "messageCode": "SUCCESS",
  "errors": null
}
```

- `accept` (`boolean`): Amaliyot muvaffaqiyati (`true` yoki `false`).
- `data` (`T` | `null`): Asosiy ma'lumot obyekti yoki sahifalangan ro'yxat (`PageableData<T>`).
- `message` (`string` | `null`): Inson o'qiy oladigan xabar.
- `messageCode` (`string` | `null`): Mashina kodi (`SUCCESS`, `VALIDATION_FAILED`, `NOT_FOUND`, `UNAUTHORIZED`, `CONFLICT`).
- `errors` (`array` | `null`): Maydonlar validatsiyasi xatolari ro'yxati: `[{ "field": "phone", "message": "Noto'g'ri telefon formati" }]`.

---

## 3. Barcha API Modullari va Hujjatlar Xaritasi

| Modul Fayli | Asosiy Path | Ruxsat (Rol) | Asosiy Vazifasi |
|---|---|---|---|
| [auth.md](auth.md) | `/api/auth` | Ochiq / Auth | Kirish, refresh token, parolni tiklash, aktivatsiya, `/me`, kompaniyani almashtirish |
| [calls.md](calls.md) | `/api/calls` | OPERATOR / ADMIN | Tezkor qo'ng'iroq (`/instant`), jonli whisper/takeover, test qo'ng'iroq, audio tinglash |
| [campaigns.md](campaigns.md) | `/api/campaigns` | OPERATOR / ADMIN | Kampaniyalar CRUD, nishonlar, ko'p tilli aqlli CSV import, takroriy (CRON) dialer |
| [scenarios.md](scenarios.md) | `/api/scenarios` | ADMIN | Dialog stsenariylari, FSM bosqichlari, Built-in shablonlar, `{{var \| fallback}}` shablonlari |
| [scenario-testing.md](scenario-testing.md) | `/api/scenarios/test` | ADMIN | Web Simulator, AI Persona Benchmark sinovlari, Jonli audio test |
| [settings.md](settings.md) | `/api/settings` | ADMIN | Uysot CRM v1 Open API, amoCRM, Kommo, Bitrix24, STT/TTS dvigatellari va AI sozlamalari |
| [contacts.md](contacts.md) | `/api/contacts` | ADMIN / OPERATOR | Kontaktlar bazasi CRUD, CSV import/export, mijoz qo'ng'iroqlar tarixi timeline |
| [inbound-routes.md](inbound-routes.md) | `/api/inbound-routes` | OPERATOR / ADMIN | Kiruvchi DID raqamlarni stsenariylarga biriktirish, ish vaqti nazorati |
| [do-not-call.md](do-not-call.md) | `/api/donotcall` | ADMIN | Qora ro'yxat (Do Not Call), raqam qo'shish/o'chirish, avtomatik cheklov |
| [reports.md](reports.md) | `/api/reports` | OPERATOR / ADMIN | Qo'ng'iroqlar analitikasi, transkriptlar, audio yozuvlar (MinIO/S3), Excel eksport, AI QA ballari |
| [live.md](live.md) | `/api/live` | OPERATOR / ADMIN | Real-vaqt monitoringi, SSE live audio stream, faol kanallar holati |
| [sip-trunks.md](sip-trunks.md) | `/api/sip-trunks` | ADMIN / SUPERADMIN | Asterisk SIP trunklar CRUD, balans va ulanish holati tekshiruvi |
| [operator.md](operator.md) | `/api/operators` | ADMIN | Tirik operatorlar navbati, ichki raqamlar (extensions), agent transferlari |
| [users.md](users.md) | `/api/users` | ADMIN | Kompaniya xodimlarini taklif qilish (invite), rollar, bloklash |
| [profile.md](profile.md) | `/api/profile` | Barcha foydalanuvchilar | Shaxsiy profil tahrirlash, avatar yuklash, faol sessiyalar, shaxsiy statistika |
| [companies.md](companies.md) | `/api/companies` | ADMIN / SUPERADMIN | Kompaniya ma'lumotlari, logo, ish vaqti va til parametrlari |
| [voices.md](voices.md) | `/api/tts/voices` | Barcha foydalanuvchilar | Mavjud TTS ovozlar katalogi (O'zbek, Rus, Ingliz) va audio namunalar |
| [files.md](files.md) | `/api/files` | Auth | MinIO/S3 fayl yuklash, audio yozuvlar va avatarlar yuklab olish |
| [search.md](search.md) | `/api/search` | OPERATOR / ADMIN | Tizim bo'ylab global qidiruv (kontaktlar, kampaniyalar, qo'ng'iroqlar) |
| [notifications.md](notifications.md) | `/api/settings/notifications` | ADMIN | Telegram bot, Webhook va Email xabarnomalari matritsasi |
| [webhooks.md](webhooks.md) | `/api/webhooks` | Ochiq / Integratsiya | Tashqi tizimlardan keladigan kiruvchi webhooklar |
