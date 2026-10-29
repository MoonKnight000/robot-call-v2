# Retell uslubi + Uysot integratsiyasi — bajarilgan ish (2026-10-29)

Foydalanuvchi so'rovi: barcha bosqichlarni so'ramasdan birin-ketin bajarish, savollarni keyinga qoldirish.
Build/test Claude tomonidan ishga tushirilmagan (CLAUDE.md §8.6) — quyidagi tekshiruvlarni foydalanuvchi bajaradi.

## Backend (`robot-call-v2`, branch `claude/hopeful-cori-i1u9dg`)

| Bosqich | Nima qilindi | Tekshiruv |
|---|---|---|
| 0 | `crm_settings` jadvali (V2 migratsiya): har kompaniyaning o'z status ID'lari va AI xodimi. `CrmService` endi avval kompaniya sozlamasini, keyin `voice-agent.crm.*` ni oladi. `GET/PUT /api/settings/crm` | `CrmServiceTest`; ikki kompaniyaga turli ID saqlab, natija bo'yicha lid to'g'ri bosqichga o'tishi |
| 1 | `UysotCatalogPort` + `HttpClientUysotCatalogAdapter`: uylar, xonadonlar, shartnoma (raqam/telefon bo'yicha), to'lov jadvali, xodimlar, voronkalar. Qisqa kesh. `CrmConnectionResolver` (CrmService'dan ajratildi). `/api/crm/uysot/{pipes,employees,houses,flats/search}` | `crm.http` dagi so'rovlar dev Uysot'ga qarshi |
| 2 | `RealEstateTools` (agent/dialog): `searchFlats`, `listResidentialComplexes`, `getContractBalance`, `getPaymentSchedule`, `requestManagerCallback`. Uysot ulangan kompaniyaning cascade, realtime va chat agentlariga avtomatik beriladi. Menejer callback → qo'ng'iroqdan keyin CRM task | `RealEstateToolsTest`; web-test qo'ng'iroqda "3 xonali 60 minggacha bormi?" |
| 3–4 | Telegram'da "📱 Raqamimni yuborish" tugmasi → raqam suhbatga biriktiriladi, CRM'dan ism/shartnoma/lid ID olinadi, xotira va Uysot tool'lari ishlaydi; chat tugaganda lidga izoh. `GET /api/crm/uysot/clients/{phone}` (Client 360) | `TelegramUpdatesTest`, `ChatConversationsTest`; botda raqam ulashib "qarzim qancha?" deb yozish |

API hujjatlari: `docs/api/crm.md` (yangi), `docs/api/chat.md` (raqam ulashish bo'limi).

## Frontend (`robot-call-front`, branch `claude/hopeful-cori-i1u9dg`)

- Sozlamalar → Integratsiyalar → Uysot: "Voronka va AI xodimi" (natija → bosqich, xodim tanlash) va
  "Agent ko'radigan katalog" (xonadon qidiruvi preview).
- Kontakt kartochkasi: "Uysot CRM profili" (lid + faol shartnomalar, qoldiq, kechikish, mas'ul).
- `src/api/modules/crm.ts`, `types.ts`, yangi xato kodlari tarjimasi.

## 2-bosqich (foydalanuvchi javoblaridan keyin)

| Ish | Natija |
|---|---|
| Uysot Open API hujjati o'qildi | `lead-task/{leadId}/save` (task), `POST lead` (bosqich o'zgartirish va lid yaratish), note ≤500 belgi, `house/all`, `pipe/all`, `flat/filter` `orders`, `dd.MM.yyyy`. Mavjud kod shunga tuzatildi |
| Qarzdorlar (`contract/filter`) | `debtAmount` = muddati o'tgan to'lanmagan summa (to'lov jadvalidan), `remainingAmount`, navbatdagi to'lov, mas'ul menejer; `GET /api/campaigns/uysot-debtors/preview`; Uysot sahifasida "Bugungi qarzdorlar" |
| Xonadon/mahsulot xotirasi | `flatSearch`, `offeredFlats`, `interestedFlat` (`rememberFlatInterest` tool), `productInterest`/`productSkus` — mijoz xotirasiga |
| Operator takeover | `POST /api/chats/{id}/takeover|messages|release`, widget polling, frontend "Suhbatlar (chat)" ekrani |
| Dizayn | Menyu Retell tartibida: Qurish → Ishga tushirish → Kuzatuv. Diqqat: `src/ds/components/shell/Sidebar.jsx` design-system sync bilan ustiga yozilishi mumkin — dizayn loyihasiga ham ko'chirish kerak |
| Frontend tekshiruvi | `tsc -b`, `vitest` (26/26), `vite build` — o'tdi |

## Ochiq savollar (foydalanuvchiga)

1. ~~Uysot Open API yo'llari tasdiqlanmagan.~~ Hujjat bo'yicha tuzatildi (2-bosqich). Eski izoh: `apidoc.app.uysot.uz` sessiyadan ochilmadi. Taxminiy yo'llar
   `voice-agent.crm.catalog.*` da (env bilan o'zgartiriladi): `v1/open-api/house`, `flat/filter`,
   `employee/filter`, `pipe`, `contract/{id}/monthly-payment`. Shartnomani telefon bo'yicha qidirish
   `contract/filter` ning `search` maydoniga telefonning oxirgi 9 raqamini beradi — Uysot buni qo'llashini tekshirish kerak.
   OAuth grant ro'yxatiga (FLAT/HOUSE/EMPLOYEE/PIPE) yangi permission qo'shilmadi — Uysot nomlarini bilish kerak.
2. **"Xodimlarni memory qilish"** — hozircha xodimlar ro'yxati jonli o'qiladi (AI xodimi tanlash, mas'ul menejer
   nomi agentga). Xodim haqida alohida xotira (ish vaqti, qaysi majmua) kerakmi — aniqlash kerak.
3. **Qarz summasini aytish** — agent `getContractBalance` orqali CRM'dagi aniq qoldiqni aytadi (FactGuard bilan).
   Faqat "muddati o'tgan" deyish kerak bo'lsa, tool'ni cheklash kerak.
4. ~~Chatdan yangi lid yaratish~~ qilindi: `newLeadStatusId` sozlansa, raqam topilmagan chatdan `POST /v1/open-api/lead` bilan lid ochiladi.
5. ~~Frontend navigatsiyasi~~ qilindi. Eski izoh: **Frontend navigatsiyasini Retell uslubida qayta guruhlash** (Build / Deploy / Monitor) — Sidebar `src/ds`
   (design-system sync) ichida, shuning uchun tegilmadi; dizayn tizimida qilinishi kerak.
6. ~~Operator takeover~~ qilindi.

## Ma'lum cheklov

Qo'ng'iroq davomida tool yozgan outcome (`responsibleEmployeeId`, `managerCallbackRequested`) post-call
xulosaga avtomatik tushmaydi — xulosa transkriptdan `outcomeSchema` bo'yicha qayta chiqariladi. Shuning uchun
`managerCallbackRequested`/`managerCallbackReason` ko'chmas mulk paketlarining `outcomeSchema`siga qo'shildi;
mas'ul menejerga task biriktirish esa faqat outcome'da `responsibleEmployeeId` bo'lsa ishlaydi (hozircha
amalda AI xodimiga tushadi).
