# API hujjatlari — frontend uchun

Bu papka frontend (Nido paneli) ishlatishi mumkin bo'lgan **hozirda mavjud, real
ishlaydigan** endpointlarni hujjatlaydi. Rejalashtirilgan-u hali yozilmagan
endpointlar uchun `docs/API-REQUIREMENTS.md`ga qarang (holat: ✅/⚠️/❌ jadvali).

## Fayllar

| Fayl | Nima haqida |
|---|---|
| [auth.md](auth.md) | Login/logout/me, Uysot OAuth stub, kompaniya tanlagich (ROADMAP E.1) |
| [users.md](users.md) | Foydalanuvchi boshqaruvi: invite, rol, block/unblock (ROADMAP E.1) |
| [profile.md](profile.md) | O'z profili: umumiy ma'lumot, parol, faol sessiyalar, shaxsiy bildirishnoma matritsasi, ish jadvali, bugungi statistika (§15) |
| [notifications.md](notifications.md) | Topbar bildirishnomalar + preference toggle |
| [search.md](search.md) | Command palette (⌘K) qidiruv backend'i |
| [companies.md](companies.md) | Kompaniya (tenant) CRUD: yaratish, sozlamalarni tahrirlash (ROADMAP B.1) |
| [sip-trunks.md](sip-trunks.md) | Kompaniyaga xos SIP trunklar: CRUD, default trunkni belgilash (ROADMAP B.3) |
| [settings.md](settings.md) | O'z kompaniyasi sozlamalari: engine (STT/TTS/realtime tanlovi), ovoz, AI model, bildirishnoma matritsasi, integratsiyalar (§11) |
| [campaigns.md](campaigns.md) | Kampaniyalar: yaratish, tahrirlash, arxivlash, nishonlar (targets), CSV import, start/pause, takroriy davr (recurrence), ilg'or sozlamalar (ambient sound, mid-call SMS, voicemail/AMD, DTMF, adaptive voice) va mijoz xotirasi (memory) |
| [scenarios.md](scenarios.md) | Ssenariy CRUD, validatsiya, klonlash |
| [scenario-testing.md](scenario-testing.md) | Ssenariylarni testlash: Web Simulator, AI vs AI Persona Benchmark, jonli telefon sinovi |
| [inbound-routes.md](inbound-routes.md) | Kiruvchi DID marshrutlash: raqam → ssenariy/til/ish vaqti (ROADMAP C.1) |
| [contacts.md](contacts.md) | Kontaktlar: CRUD, CSV import, qo'ng'iroqlar tarixi |
| [do-not-call.md](do-not-call.md) | "Qo'ng'iroq qilinmasin" (DNC) ro'yxati |
| [calls.md](calls.md) | Qo'lda qo'ng'iroq boshlash / play / say (test-tekshiruv uchun) |
| [live.md](live.md) | Jonli qo'ng'iroqlar ro'yxati va SSE push kanali |
| [operator.md](operator.md) | Operatorga uzatilgan qo'ng'iroq konteksti |
| [reports.md](reports.md) | Dashboard KPI/grafiklar, qo'ng'iroqlar tarixi + filtr/eksport/ommaviy amal, texnik tafsilot, transkript TXT, audit jurnali, recording |
| [webhooks.md](webhooks.md) | Qo'ng'iroq life-cycle webhook hodisalari va SMS integratsiyasi |
| [voices.md](voices.md) | TTS ovozlar katalogi |
| [files.md](files.md) | Fayllarni (logo, avatar, qo'ng'iroq yozuvi) MinIO'dan streamlab beruvchi umumiy endpoint |
