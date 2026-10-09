# Soha paketlari (Vertical Pack) — `/api/packs`

Paket — bitta soha uchun tayyor operator: agent (ichida ssenariy), tashqi API tool'lari, mahsulot
katalogi, test holatlari va konversiya maqsadi. Bitta so'rov bilan kompaniyaga o'rnatiladi. Yangi soha —
yangi paket fayli (`resources/packs/<key>.json`), Java kod emas (UNIVERSAL-OPERATOR-PLAN C.1).

Paketlar build bilan birga keladi va hamma kompaniya uchun bir xil. API orqali tahrirlanmaydi —
o'rnatilgandan keyin hosil bo'lgan agent va tool'lar oddiy agent/tool kabi tahrirlanadi.

## Endpointlar

| Metod | URL | Permission | Tavsif |
|---|---|---|---|
| `GET` | `/api/packs` | `AI_AGENT_READ` | Paketlar katalogi |
| `POST` | `/api/packs/{key}/install` | `AI_AGENT_EDIT` | Paketni kompaniyaga o'rnatish |
| `POST` | `/api/packs/generate` | `AI_AGENT_EDIT` | Biznes tavsifidan yangi paket qoralamasi (hech narsa yaratmaydi) |
| `POST` | `/api/packs/install-draft` | `AI_AGENT_EDIT` | Qoralamani o'rnatish |
| `GET` | `/api/packs/agents/{agentId}/export?version=` | `AI_AGENT_READ` | Mavjud agentni paket JSON'i sifatida olish (config-as-code) |

---

## `GET /api/packs` — katalog

```json
{
  "accept": true,
  "data": [
    {
      "key": "ecommerce",
      "name": "Internet-magazin: buyurtma qabul qilish",
      "description": "Mijoz buyurtmani og'zaki aytadi: ...",
      "industry": "ecommerce",
      "toolNames": ["create_order", "check_order_status"],
      "hasCatalogFeed": true,
      "testCaseCount": 5,
      "secrets": [
        { "key": "SHOP_API_URL", "description": "Magazin API'sining asosiy manzili, oxirida / belgisiz: https://shop.uz/api" },
        { "key": "SHOP_API_TOKEN", "description": "Magazin API tokeni — so'rovlarga 'Authorization: Bearer <token>' bo'lib qo'yiladi" }
      ]
    }
  ],
  "message": null, "messageCode": null, "errors": null
}
```

| Maydon | Ma'nosi |
|---|---|
| `toolNames` | O'rnatilganda yaratiladigan tool'lar ([tools.md](tools.md)) |
| `hasCatalogFeed` | Agent mahsulot katalogidan qidiradi — o'rnatishda `catalogUrl` berish mantiqli |
| `testCaseCount` | Agentga qo'shiladigan eval test holatlari soni ([ai-agents.md §15](ai-agents.md)) |
| `secrets` | Tool'lar o'qiydigan `{{secrets.KEY}}` kalitlari. O'rnatish ularni **to'ldirmaydi** |

---

## `POST /api/packs/{key}/install` — o'rnatish

Body ixtiyoriy (umuman yuborilmasa ham bo'ladi):

```json
{
  "catalogUrl": "https://shop.example.uz/api/products",
  "catalogItemsPath": "$.data"
}
```

| Maydon | Ma'nosi |
|---|---|
| `catalogUrl` | Kompaniyaning mahsulot feed'i (CSV yoki JSON URL, ≤ 2048). Berilsa, agentga `STRUCTURED_FEED` manba yaratiladi va indekslash boshlanadi ([knowledge-base.md §Katalog](knowledge-base.md)). Berilmasa, katalog keyin `POST /api/knowledge-base/sources` bilan `agentId` ko'rsatib qo'shiladi |
| `catalogItemsPath` | JSON feed'da qatorlar qayerda (`$.data`, `$.products`); `null` — javobning o'zi massiv. CSV uchun kerak emas |

**Nima yaratiladi** (hammasi bitta tranzaksiyada — biror qadam yiqilsa, hech narsa qolmaydi):

1. **Agent** — qoralama (`draft`), `pipelineMode: CASCADE` (kompaniya tool'lari faqat cascade'da ishlaydi).
   Ssenariy agentning o'zida saqlanadi (`scenarioDefinition`), umumiy ssenariy qatori yaratilmaydi —
   shuning uchun paketni ikki marta o'rnatish ikkita mustaqil agent beradi. Ovoz, engine, limitlar —
   agentning standart qiymatlari. Bilimlar bazasidan qidirish (`useRag`) paketda belgilanadi: support,
   klinika, magazin, restoran va lead paketlarida yoqilgan, `nps` da esa yo'q — qidiradigan narsasi yo'q.
   **Ma'lumot siyosati** (`dataPolicy`) ham paketda bo'lishi mumkin — hozircha faqat `clinic` da
   (UNIVERSAL-OPERATOR-PLAN E.3, pastda). Paket uni bermasa, agentning standart qiymatlari qoladi:
   `zeroPiiRetention: false`, `storeCallAudio: true`, transkript 30 kun.
2. **Tool'lar** — har biri yaratiladi va agentga biriktiriladi. Oddiy tool kabi tekshiriladi (`bodySchema`, `effect`).
3. **Katalog manbasi** — faqat `catalogUrl` berilganda. Birinchi yuklash tranzaksiya commit bo'lgach boshlanadi.
4. **Test holatlari** — agentning eval to'plamiga.
5. **Konversiya maqsadi** — faqat kompaniyada shu `goalKey` bilan maqsad **yo'q** bo'lsa. Mavjud maqsad
   o'zgartirilmaydi.

Secrets hech qachon yaratilmaydi — ular kompaniyaning o'z tokeni va manzili.

```json
{
  "accept": true,
  "data": {
    "agentId": 42,
    "toolIds": [101, 102],
    "catalogSourceId": 17,
    "testCaseIds": [301, 302, 303, 304, 305],
    "conversionGoalKey": "order_paid",
    "missingSecrets": [
      { "key": "SHOP_API_TOKEN", "description": "Magazin API tokeni — ..." }
    ]
  },
  "message": null, "messageCode": null, "errors": null
}
```

| Maydon | Ma'nosi |
|---|---|
| `catalogSourceId` | `null` — `catalogUrl` berilmagan |
| `conversionGoalKey` | Hozir yaratilgan maqsad; `null` — paketda maqsad yo'q yoki kompaniyada allaqachon bor edi |
| `missingSecrets` | Kompaniya hali qo'ymagan secret'lar. Ular qo'yilmaguncha tool chaqiruvlari yiqiladi — `POST /api/secrets` ([secrets.md](secrets.md)) |

**Xatolar**

| Status | `messageCode` | Qachon |
|---|---|---|
| `404` | `PACK_NOT_FOUND` | Bunday `key` li paket yo'q |
| `400` | `KNOWLEDGE_SOURCE_URL_INVALID` | `catalogUrl` http(s) manzil emas |
| `400` / `409` | agent yoki tool yaratish xatolari | Masalan, kompaniya paket tilini (`uz-UZ`) qo'llamasa — `POST /api/ai-agents` bilan bir xil ([ai-agents.md §4](ai-agents.md)) |

**O'rnatgandan keyin:** `missingSecrets` ni to'ldiring → katalog `INDEXED` bo'lishini kuting
(`GET /api/knowledge-base/sources/{id}`) → `POST /api/ai-agents/{id}/evaluate` → web-test qo'ng'iroq →
agentni kampaniya yoki kiruvchi yo'nalishga ulang.

---

## `ecommerce` — Internet-magazin

Kiruvchi va chiquvchi qo'ng'iroq uchun bitta agent: buyurtma qabul qiladi (katalogdan mahsulot →
manzil → o'qib tasdiqlatish → `create_order`) va buyurtma holatini aytadi (`check_order_status`).
Maqsad maydoni — `orderId`: `create_order` 2xx javobida `orderId` kelsa, qo'ng'iroq `GOAL_REACHED`
bilan tugaydi.

**Magazin API shartnomasi.** Paket quyidagi ko'rinishdagi API'ni kutadi. Magazin API'si boshqacha
bo'lsa, o'rnatilgandan keyin tool'ning `apiUrl`, `bodySchema` va `responseMapping` maydonlari
`PUT /api/tools/{id}` bilan moslanadi.

| Tool | So'rov | Kutilgan javob |
|---|---|---|
| `create_order` (`WRITE`, tasdiq bilan) | `POST {SHOP_API_URL}/orders`, `Authorization: Bearer {SHOP_API_TOKEN}`, `Idempotency-Key` | `{"orderId": "10245", "total": 250000}` |
| `check_order_status` (`READ`) | `GET {SHOP_API_URL}/orders/{order_id}` | `{"status": "...", "deliveryDate": "..."}` |

`create_order` body'si:

```json
{
  "items": [{ "sku": "KR-42-BLK", "qty": 2 }],
  "customerName": "Aziza",
  "phone": "+998901234567",
  "address": "Toshkent, Chilonzor tumani, 5-kvartal, 12-uy, 34-xonadon",
  "comment": null,
  "channel": "voice"
}
```

- `sku` — faqat `searchCatalog` qaytargan qiymat (katalog feed'ining `sku` ustuni).
- `phone` — qo'ng'iroqdagi mijoz raqami (`x-source: variable`): kiruvchida qo'ng'iroq qiluvchining raqami,
  chiquvchida terilgan raqam. Model uni to'ldirmaydi; raqam yashirilgan qo'ng'iroqda maydon body'dan tushib qoladi.
- `Idempotency-Key` `WRITE` tool'ga avtomatik qo'shiladi — endpoint uni hurmat qilsa, takroriy so'rov ikkinchi
  buyurtma bo'lmaydi.
- **Manzil readback.** `create_order` ning `readbackTemplate` i yuboriladigan manzil va qabul qiluvchini
  mijozga so'zma-so'z o'qib beradi; mahsulotlarni agent o'zi sanab aytadi (SKU ovozda hech narsa tekshirmaydi).
  Mijoz "ha" demaguncha so'rov ketmaydi (B.3).

**Konversiya maqsadi:** `order_paid` ("Buyurtma to'landi", oyna 168 soat) — magazin to'langan buyurtmani
`POST /api/conversions` bilan xabar qiladi ([conversions.md](conversions.md)).

**Test holatlari (5):** oddiy buyurtma, chegirma talab qiladi, karta ma'lumotini aytmoqchi, boshqa odamning
buyurtmasi, ruscha gapiradigan mijoz. Eval'da kompaniya tool'lari va katalog yo'q, shuning uchun holatlar agent
**nima qilmasligini** tekshiradi: tool javobisiz "buyurtmangiz qabul qilindi" demaydi, chegirma va'da qilmaydi,
karta ma'lumotini so'ramaydi, begona buyurtma holatini to'qimaydi. To'qilgan raqamni FactGuard alohida ushlaydi.

**Cheklovlar**

- Faqat `CASCADE` — realtime engine kompaniya tool'larini chaqirmaydi.
- Bilimlar bazasi (yetkazib berish narxi, qaytarish shartlari) paketda yo'q: bu har magazinning o'z faktlari,
  umumiy matn agentga yolg'on gapirtiradi. Ular [knowledge-base.md](knowledge-base.md) orqali qo'shiladi.
- Faqat holat so'ragan qo'ng'iroq ham `GOAL_NOT_REACHED` bo'lib hisoblanadi — maqsad buyurtma.

---

## `clinic` — Klinika / salon bron

Kiruvchi va chiquvchi qo'ng'iroq uchun bitta agent: qabulga yozadi (xizmat → bo'sh vaqtlar → o'qib
tasdiqlatish → `book_slot`) va yozuvni bekor qiladi (`cancel_slot`). Maqsad maydoni — `slotBooked`:
`book_slot` 2xx javobida qabul raqami kelsa, qo'ng'iroq `GOAL_REACHED` bilan tugaydi.

**Klinika API shartnomasi.** Boshqacha bo'lsa, o'rnatilgandan keyin `PUT /api/tools/{id}` bilan
moslanadi ([tools.md](tools.md)).

| Tool | So'rov | Kutilgan javob |
|---|---|---|
| `list_slots` (`READ`) | `GET {CLINIC_API_URL}/slots?date=&from=&to=&service=` | `[{"slotId": "SL-1740", "startsAt": "2026-09-17T14:00:00", "doctor": "Nodira Karimova"}]` |
| `book_slot` (`WRITE`, tasdiq bilan) | `POST {CLINIC_API_URL}/appointments`, `Idempotency-Key` | `{"appointmentId": "4821", "startsAt": "2026-09-17T14:00:00"}` |
| `cancel_slot` (`WRITE`, tasdiq bilan) | `POST {CLINIC_API_URL}/appointments/{appointment_id}/cancel` | `{"cancelled": true}` |

`book_slot` body'si:

```json
{
  "slotId": "SL-1740",
  "slotTime": "2026-09-17, soat 14:00",
  "patientName": "Dilnoza Rasulova",
  "phone": "+998901234567",
  "service": "Terapevt",
  "comment": null,
  "channel": "voice"
}
```

- `slotId` — faqat `list_slots` qaytargan qiymat.
- `slotTime` — **o'sha slotning kuni va soati odam tushunadigan ko'rinishda**. U API uchun emas,
  tasdiq uchun: `readbackTemplate` shu maydonni mijozga o'qib beradi (`slotId` ni o'qish hech narsa
  tekshirmaydi). Tool javobidagi `startsAt` ovozga chiqarilmaydi — to'liq ISO vaqt ovozda xunuk o'qiladi.
- `phone` — qo'ng'iroqdagi mijoz raqami (`x-source: variable`), model to'ldirmaydi.

**Sana va vaqt.** "Ertaga tushdan keyin" degan mijoz `list_slots` ga `date=<ertangi kun>`,
`from=13:00`, `to=17:00` bo'lib boradi: ikkala qiymat ham system prompt'dagi tayyor `SANALAR` va
`VAQT` ro'yxatlaridan olinadi (UNIVERSAL-OPERATOR-PLAN B.7), model hisoblamaydi. Kun qismlari:
ertalab 09:00–12:00, tushlik 12:00–13:00, tushdan keyin 13:00–17:00, kechqurun 17:00–20:00.

**Konversiya maqsadi:** `appointment_attended` ("Bemor qabulga keldi", oyna 720 soat) — klinika
bemor kelganini `POST /api/conversions` bilan xabar qiladi ([conversions.md](conversions.md)).

**Test holatlari (5):** ertaga tushdan keyin yozilish, tashxis so'raydi, dori va doza so'raydi, boshqa
odamning yozuvi, ruscha shoshilinch holat. Eval'da kompaniya tool'lari yo'q, shuning uchun holatlar
agent **nima qilmasligini** tekshiradi: tool javobisiz "yozib qo'ydim" demaydi, tashxis qo'ymaydi,
dori aytmaydi, begona odamning qabuli va tahlili haqida gapirmaydi; shoshilinch holatda 103 ni aytadi.

**Cheklovlar**

- Faqat `CASCADE` — realtime engine kompaniya tool'larini chaqirmaydi.
- Katalog feed'i yo'q: `catalogUrl` berilsa ham e'tiborga olinmaydi. Xizmatlar ro'yxati `list_slots`
  javobidan, narx va tayyorgarlik qoidalari esa [knowledge-base.md](knowledge-base.md) orqali qo'shiladi.
- Agent shifokor emas: tashxis, dori va tahlil izohi guardrail bilan taqiqlangan, bunday savol
  operatorga o'tadi.

**Ma'lumot siyosati — bu paketda maxsus** (UNIVERSAL-OPERATOR-PLAN E.3). Klinika qo'ng'irog'ida
mijoz nima uchun shifokorga yozilayotganini aytadi; bu — sog'liq ma'lumoti, va u standart holatda
saqlanmaydi:

```json
"dataPolicy": { "zeroPiiRetention": true, "storeCallAudio": false, "conversationRetentionDays": 7 }
```

- `zeroPiiRetention` — qo'ng'iroq natijasidan **matn olib tashlanadi**: qisqacha mazmun, izoh va
  `dataNeeded` qiymatlari saqlanmaydi, mijoz xotirasiga (`ClientMemory`) ham shikoyat tushmaydi.
  **Lekin maqsad maydoni (`slotBooked`) saqlanadi** — aks holda "shikoyatni saqlamaslik" "qabulni
  yo'qotish" degani bo'lardi. Kompaniyaning o'z CRM'i va post-call webhook'lari to'liq mazmunni
  oladi: u yerga yuborish — kompaniyaning o'z qarori.
- `storeCallAudio: false` — yozuv umuman saqlanmaydi.
- Transkript 7 kun (standart 30).

Buning narxi bor: review inbox'da sifat sudyasining izohi bo'lmaydi va transkript tez o'chadi
([call-reviews.md](call-reviews.md)). Kompaniyaning qoidalari ruxsat bersa, o'rnatgandan keyin
`PUT /api/ai-agents/{id}/advanced` bilan o'zgartiriladi — lekin bu ongli qaror bo'lsin.

---

## `restaurant` — Restoran yetkazib berish

Kiruvchi qo'ng'iroq: menyudan buyurtma qabul qiladi va buyurtma holatini aytadi. Maqsad maydoni —
`orderId`, `ecommerce` dagi kabi. Tool shartnomasi ham `ecommerce` bilan bir xil
(`create_order`, `check_order_status`) — farq **menyuda, manzilda va tarkib savolida**.

**Menyu — katalog feed'i.** `catalogFeed` majburiy emas, lekin usiz agent hech qanday taomni topa
olmaydi: o'rnatishda `catalogUrl` bering. Yangilanish oralig'i **15 daqiqa** (magazinda 60) — stop-list
kun davomida o'zgaradi, kechagi narx bilan qabul qilingan buyurtmani oshxona rad etadi.

`create_order` body'sida `ecommerce` dan farqli ikki maydon:

```json
{
  "items": [{ "sku": "LAVASH-01", "qty": 2 }],
  "customerName": "Sardor",
  "phone": "+998901234567",
  "address": "Yunusobod tumani, 12-kvartal, 5-uy, 42-xonadon, 3-podezd, mo'ljal — Korzinka",
  "wantedAt": "soat 19:30",
  "comment": "achchiq bo'lmasin",
  "channel": "voice"
}
```

- `address` — podezd va mo'ljal bilan: kuryerga shu yetmaydi, DELIVERY bosqichi ularni alohida so'raydi.
- `wantedAt` — "hozir" yoki aniq soat; u API uchun emas, **tasdiq uchun**: `readbackTemplate` manzil
  bilan birga shuni o'qiydi. Soat B.7 dagi VAQT ro'yxati bo'yicha aniqlashtiriladi.

**Allergiya — alohida guardrail.** Mijoz tarkib haqida so'rasa va javob menyuda yoki bilimlar bazasida
aniq yozilmagan bo'lsa, agent **taxmin qilmaydi**: oshpazdan aniqlatishni aytib operatorga o'tkazadi.
Bu boshqa paketlardagi "to'qima" qoidasidan kuchliroq — bu sog'liq masalasi, shuning uchun alohida test
holati ham bor.

**Konversiya maqsadi:** `order_paid` (oyna **24 soat** — magazinda 168; ovqat o'sha kuni to'lanadi).
Kompaniyada shu kalit bilan maqsad bo'lsa (masalan `ecommerce` dan) yangisi yaratilmaydi va ikkala
agent bitta maqsadni bo'lishadi.

**Test holatlari (5):** oddiy buyurtma, allergiya haqida so'raydi, menyuda yo'q taom + chegirma talab
qiladi, buyurtma kech keldi (kuryer qayerdaligini va pul qaytarishni talab qiladi), ruscha yetkazib
berish narxi va vaqti. Hammasi agentning **vaqt va tarkibni to'qimasligini** tekshiradi.

**Cheklovlar**

- Faqat `CASCADE`.
- Yetkazib berish narxi, hududlari va ish vaqti paketda yo'q — har restoranning o'ziniki, bilimlar
  bazasiga qo'shiladi (`useRag: true`).
- Kuryer qayerdaligi (real-time tracking) yo'q: agent faqat `check_order_status` qaytargan holatni aytadi.

---

## `lead` — Lead kvalifikatsiya (ko'chmas mulk, ta'lim)

Chiquvchi qo'ng'iroq: saytda ariza qoldirgan mijozga qo'ng'iroq qilib **uchta narsani** aniqlaydi
(nima izlayapti, byudjeti, muddati), CRM ga lead yozadi va qiziqsa ko'rikka yozib qo'yadi. Maqsad
maydoni — `leadQualified`: `create_lead` javobida `leadId` kelsa qo'ng'iroq `GOAL_REACHED`.

**CRM shartnomasi.** Bu paket **OAuth 2.0 client credentials** ishlatadi (B.5): tool o'zi token oladi,
`Authorization` sarlavhasi qo'lda yozilmaydi, `401` kelsa token bekor qilinib qayta olinadi.

| Tool | So'rov | Kutilgan javob |
|---|---|---|
| `create_lead` (`WRITE`, tasdiq bilan) | `POST {CRM_API_URL}/leads` | `{"leadId": "3412"}` |
| `schedule_visit` (`WRITE`, tasdiq bilan) | `POST {CRM_API_URL}/visits` | `{"visitId": "V-77", "startsAt": "2026-09-20T15:00:00"}` |

```json
{
  "fullName": "Jasur",
  "phone": "+998901234567",
  "interest": "2 xonali, Chilonzor",
  "budget": "600-700 million",
  "timeframe": "3-4 oy ichida",
  "comment": null,
  "source": "voice"
}
```

- `schedule_visit` `leadId` ni `create_lead` javobidan oladi — shuning uchun tartib qat'iy: avval lead,
  keyin ko'rik.
- `visitTimeText` — mijozga o'qib tasdiqlatiladigan matn ("2026-09-18, soat 15:00"); `visitTime` esa
  tizimga ketadigan qiymat.

**Secrets (4 ta):** `CRM_API_URL`, `CRM_TOKEN_URL`, `CRM_CLIENT_ID`, `CRM_CLIENT_SECRET`.

**Konversiya maqsadi:** `deal_won` ("Shartnoma tuzildi", oyna 2160 soat = 90 kun) — savdo sikli uzun,
shuning uchun oyna boshqa paketlarnikidan katta.

**Test holatlari (5):** qiziqqan mijoz, "qo'ng'iroq qilmang" (`DO_NOT_CALL` kutiladi), narx kafolatini
talab qiladi, ipoteka shartlarini so'raydi, ruscha "vaqtim yo'q". Holatlar agentning **bosim
o'tkazmasligini** va narx/foizni to'qimasligini tekshiradi.

**Cheklovlar**

- Narx va shartlar bilimlar bazasidan o'qiladi (`useRag: true`) — KB bo'sh bo'lsa agent har safar
  "menejer aytadi" deydi. Bu ataylab: to'qib aytgandan ko'ra o'tkazib yuborgan yaxshi.
- Agent sotmaydi va shartnoma tuzmaydi — u faqat kvalifikatsiya qiladi.

---

## `support` — 1-liniya qo'llab-quvvatlash

Kiruvchi qo'ng'iroq: savolga **bilimlar bazasidan** javob beradi, javob topilmasa tiket ochadi.
Maqsad maydoni — `resolvedOrTicketed` (boolean): mijoz javob oldi **yoki** tiket ochildi.

Bu paketda maqsadni HTTP tool emas, **ssenariy tool'i** yozadi: `recordResolution(resolvedOrTicketed,
topic)`. Sabab — "hal qilindi" ikki xil yo'l bilan bo'ladi (javob berildi yoki tiket ochildi), va
javob berilgan qo'ng'iroqda hech qanday yozuv yaratilmaydi. Shuning uchun agent har qo'ng'iroq oxirida
shu tool'ni chaqiradi; uni chaqirmagan qo'ng'iroq `GOAL_NOT_REACHED` bo'ladi.

| Tool | So'rov | Kutilgan javob |
|---|---|---|
| `create_ticket` (`WRITE`, tasdiq bilan) | `POST {HELPDESK_API_URL}/tickets` | `{"ticketId": "8120"}` |
| `check_ticket_status` (`READ`) | `GET {HELPDESK_API_URL}/tickets/{ticket_id}` | `{"status": "...", "updatedAt": "..."}` |

**Secrets:** `HELPDESK_API_URL`, `HELPDESK_API_TOKEN`.

**Bilimlar bazasi shart.** `useRag: true` — paket KB'siz ham o'rnatiladi, lekin unda agent deyarli har
savolga tiket ochadi. O'rnatgandan keyin FAQ va siyosatlarni [knowledge-base.md](knowledge-base.md)
orqali qo'shing.

**Test holatlari (5):** javob KB'da yo'q (muddat to'qimasligi kerak), pul qaytarishni talab qiladi,
parolini aytmoqchi, boshqa odamning tiketi, ruscha "operator bering" (`TRANSFERRED` kutiladi).

**Cheklovlar**

- Konversiya maqsadi yo'q: support qo'ng'irog'i sotuv emas, uning natijasi `resolvedOrTicketed` ning
  o'zida.
- `recordResolution` qiymatini model yozadi — u yolg'on yozsa sifat sudyasi va review inbox ushlaydi
  ([call-reviews.md](call-reviews.md)).

---

## `nps` — Eslatma / NPS so'rovnoma

Chiquvchi qo'ng'iroq, **tashqi tool'siz va secrets'siz**: bitta savol (0-10 baho), sababi, tamom.
Maqsad maydoni — `answered` (boolean).

Natija `recordSurvey(answered, npsScore, comment)` ssenariy tool'i orqali qo'ng'iroqning o'z natijasiga
yoziladi; hech qayerga so'rov ketmaydi. Shuning uchun bu paket **hech qanday sozlashsiz** ishlaydi —
`POST /api/packs/nps/install` dan keyin darhol kampaniyaga ulash mumkin.

| Maydon | Ma'nosi |
|---|---|
| `answered` | Mijoz baho aytdimi (maqsad) |
| `npsScore` | 0-10, faqat mijoz aytgan raqam |
| `comment` | Sababi, mijoz so'zlari bilan |

**Test holatlari (5):** baho beradi, past baho va shikoyat (kompensatsiya va'da qilmaslik), vaqti yo'q,
raqam o'rniga so'z aytadi, ruscha narx haqida savol beradi.

**Cheklovlar**

- Agent so'rovnomadan boshqa hech narsa qilmaydi: xizmat haqidagi savolga javob bermaydi, muammoni hal
  qilmaydi — bunday holatda operatorga o'tkazadi.
- `npsScore` faqat mijoz aytgan raqamdan yoziladi; mijoz raqam aytmasa maydon bo'sh qoladi va
  `answered=false` bo'ladi.

---

## `debt_collection` — Bank/MFO muddati o'tgan qarz (Soft Collection)

Chiquvchi qo'ng'iroq, **tashqi tool'siz va secrets'siz**. Bosqichlar: `RPC_VERIFY` (o'zi ekanini
tasdiqlash — begonaga qarz haqida hech narsa aytilmaydi, `recordWrongPerson`) → `IDENTITY_CHECK`
(faqat `birthYear` fakti bo'lsa) → `DEBT_NOTICE` → `PTP_NEGOTIATION` / `SETTLEMENT` /
`OBJECTION_HANDLING` → `CONFIRMATION` → `CLOSING`. Maqsad maydoni — `promisedDate`.

| Ssenariy tool'i | Natija |
|---|---|
| `recordPaymentPromise(promisedDate, amount, paymentChannel, note)` | `PROMISE_TO_PAY` — nishon yopiladi |
| `recordPaymentClaim(paidDate, paymentChannel)` | `PAYMENT_CLAIMED` — nishon 3 kunga muzlatiladi ([calls.md](calls.md)) |
| `recordRefusalReason(reason, detail)` | `REFUSED` |
| `requestPaymentExtension(extensionDays, reason)` | faqat natijaga yoziladi |
| `scheduleCallback(callbackTime, reason)` | `CALLBACK_REQUESTED` |
| `sendSmsPaymentLink(note)` | `paymentLink` fakti bo'lsa SMS ketadi ([webhooks.md](webhooks.md)) |

`paymentChannel` qiymatlari: `CLICK`, `PAYME`, `UZUM`, `BANK_BRANCH`, `TERMINAL`, `OTHER`.

**Faktlar:** majburiy — `clientName`, `contractNumber`, `debtAmount`, `currency`, `dueDate`. Ixtiyoriy —
`birthYear` (identifikatsiya), `settlementAmount` + `settlementDeadlineHours` (chegirma),
`installmentMonths` + `installmentMonthly` (bo'lib to'lash), `paymentLink`. Chegirma yoki bo'lib
to'lash fakti berilmasa bot ularni **taklif qilmaydi**; berilgan summani FactGuard nazorat qiladi.

**Test holatlari (6):** turmush o'rtog'i ko'tardi (`WRONG_NUMBER`, "qarz" so'zi aytilmasligi kerak),
ertaga Click orqali (`PROMISE_TO_PAY`), kecha to'lagan (`PAYMENT_CLAIMED`), ishsiz qolgan,
kreditni olmaganini aytadi (`TRANSFERRED`), ruscha "boshqa qo'ng'iroq qilmang" (`DO_NOT_CALL`).

---

## `POST /api/packs/generate` — tavsifdan paket qoralamasi

Katalogda paketi yo'q biznes uchun paketni **model yozadi** (UNIVERSAL-OPERATOR-PLAN E.1). Hech narsa
yaratilmaydi: javob — qoralama, uni odam o'qib, tahrirlab, keyin `/install-draft` ga yuboradi.

```json
{
  "business": "Men gul do'koniman. Mijozlar qo'ng'iroq qilib buket buyurtma qiladi, yetkazib berish Toshkent bo'ylab. Ish vaqti 9 dan 21 gacha.",
  "openApiUrl": "https://gullar.example.uz/openapi.json",
  "baseUrl": null,
  "secretPrefix": "FLOWERS",
  "language": "uz-UZ"
}
```

| Maydon | Ma'nosi |
|---|---|
| `business` | Biznesning o'z so'zlari bilan tavsifi (majburiy, ≤ 4000). Qancha aniq bo'lsa, model shuncha kam narsa o'ylab topadi |
| `openApiUrl` / `openApiSpec` | API hujjati — manzil yoki matn, **ikkisidan bittasi**. Berilmasa paket tool'siz chiqadi |
| `baseUrl`, `secretPrefix` | [tools.md](tools.md) dagi import bilan bir xil ma'noda |
| `language` | Qo'ng'iroq tili; `null` — kompaniyaning o'z tili |

**Mehnat taqsimoti — bu yerda eng muhim narsa.** Model **faqat suhbatni** yozadi: `firstMessage`,
bosqichlar, guardrail'lar, `outcomeSchema`, test holatlari. **Tool'larni model yozmaydi** — ular API
hujjatidan `POST /api/tools/import` ishlatadigan o'sha importer orqali quriladi (B.2). Sabab oddiy:
model o'ylab topgan endpoint — bu hech kim tekshirmagan manzilga ketadigan so'rov. `secrets` ro'yxati
ham qo'lda emas, tool'larning o'zidan (`{{secrets.KEY}}` va OAuth maydonlari) yig'iladi.

Javob — to'liq `VerticalPack` (katalogdagi paketlar bilan bir xil shakl). U qaytarilishidan oldin
ssenariy **saqlangan ssenariy kabi tekshiriladi**: bosqich id'lari takrorlanmasin, har bir bosqich
`END_CALL` ga yeta olsin, `outcomeSchema` da bitta `goal` bo'lsin. Tekshiruvdan o'tmasa `400`
`PACK_DRAFT_INVALID` va muammolar `errors` ro'yxatida — qayta generatsiya qilinadi.

**Xatolar**

| Status | `messageCode` | Qachon |
|---|---|---|
| `409` | `PACK_GENERATOR_NOT_CONFIGURED` | Serverda chat model sozlanmagan (`GEMINI_API_KEY` / `OPENAI_API_KEY`) |
| `502` | `PACK_GENERATION_FAILED` | Model javob bermadi yoki o'qib bo'lmaydigan JSON qaytardi |
| `400` | `PACK_DRAFT_INVALID` | Qoralamaning ssenariysi yaroqsiz — `errors` da har bir muammo |
| `400` | `OPENAPI_*` | API hujjati o'qilmadi ([tools.md](tools.md) bilan bir xil kodlar) |

Sozlash — `voice-agent.pack-generator.*` (`config/misc.yml`): `PACK_GENERATOR_MODEL`
(standart `gemini-3.8-pro` — qoralama butun ssenariy, kichik model uni uyushtira olmaydi),
`PACK_GENERATOR_MAX_TOKENS` (8192).

## `POST /api/packs/install-draft` — qoralamani o'rnatish

```json
{
  "pack": { "...": "generate qaytargan paket, tahrirlangan holicha" },
  "catalogUrl": null,
  "catalogItemsPath": null
}
```

Paket katalogdagi paket kabi o'rnatiladi (bitta tranzaksiya, xuddi shu javob). Farqi — paket kalit
bo'yicha topilmaydi, **body'dan keladi**: qoralama serverda ikki so'rov orasida saqlanmaydi, chunki
o'rnatiladigan narsa odam ko'rgan narsa bo'lishi kerak, model birinchi yozgan narsa emas.

Ssenariy bu yerda ham tekshiriladi (`PACK_DRAFT_INVALID`), agent/tool/test holatlari esa o'z
feature'lari orqali — qo'lda yaratilgani bilan bir xil.

---

## `GET /api/packs/agents/{agentId}/export` — agentni paketga aylantirish

Mavjud agentni `install-draft` qabul qiladigan **aynan o'sha** `VerticalPack` JSON'i ko'rinishida
qaytaradi. Maqsad — agentni git'da saqlash (config-as-code) va boshqa kompaniya yoki muhitga
(staging → prod) ko'chirish: JSON'ni `POST /api/packs/install-draft` ga `{"pack": …}` qilib yuborsangiz,
yangi agent yaratiladi.

| Parametr | Ma'nosi |
|---|---|
| `version` | E'lon qilingan versiya raqami; berilmasa — qoralama |

Paketga nimalar kiradi:

| Qism | Qayerdan |
|---|---|
| `agent` | Nom, tavsif, til, `firstMessage`, `useRag`, `dataPolicy`, `dataNeeded`, `dataEvaluation` va agent **ishlatadigan** ssenariy. Umumiy ssenariy (`scenarioId`) bo'lsa, uning ta'rifi paket ichiga ko'chiriladi. Ovoz, engine, limitlar va webhook'lar kirmaydi — ular kompaniyaniki |
| `tools` | Agentga hozir ulangan tool'lar (versiyaga bog'liq emas — tool'lar versiyalanmaydi) |
| `testCases` | Agentning eval test holatlari, `toolMocks`/`expectTools` bilan |
| `secrets` | Tool'lar o'qiydigan `{{secrets.KEY}}` kalitlari — faqat **kalit**, qiymat hech qachon chiqmaydi |
| `catalogFeed`, `conversionGoal` | Har doim `null`: feed manzili va konversiya maqsadi kompaniyaniki |

`key` — `agent-{agentId}`, `industry` — `null`. `id`, `companyId`, `createdAt` kabi saqlash
maydonlari tool va test holatlarida qoladi, lekin o'rnatishda e'tiborga olinmaydi. Har bir export
audit'ga `PACK_EXPORT` bo'lib yoziladi.

| Status | `messageCode` | Qachon |
|---|---|---|
| `404` | `AI_AGENT_NOT_FOUND` | Agent yo'q yoki boshqa kompaniyaniki |
| `404` | `AI_AGENT_VERSION_NOT_FOUND` | Bunday `version` yo'q |

MCP server orqali ham olinadi: `export_agent` va `import_agent_pack` ([mcp-server.md](mcp-server.md)).
