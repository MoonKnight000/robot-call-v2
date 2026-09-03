# Kampaniyalar API

`uz.murodjon.robotcallv2.campaign` · rol: **OPERATOR / ADMIN**

Avtomatlashtirilgan ommaviy qo'ng'iroq kampaniyalarini boshqarish, nishonlar yuklash, ko'p tilli audio konfiguratsiyasi, SIP trunklarni taqsimlash va dialer tezligini sozlash.

---

## 1. Kampaniyalar CRUD

### `POST /api/campaigns` — Yangi kampaniya yaratish

Yangi chiquvchi qo'ng'iroq kampaniyasini yaratadi.

**Request Body** (`CreateCampaignRequest`):
```json
{
  "name": "Kechikkan to'lovlar - Mart 2026",
  "scenarioId": 4,
  "defaultLanguage": "uz-UZ",
  "defaultVoice": "dilnavoz",
  "languageVoices": {
    "uz-UZ": "dilnavoz",
    "ru-RU": "mariya",
    "en-US": "jennifer"
  },
  "sipTrunkIds": [1, 2],
  "disclosureEnabled": true,
  "ambientSound": "OFFICE_BACKGROUND",
  "midCallSmsEnabled": true,
  "midCallSmsTemplate": "To'lov havolasi: https://pay.uysot.uz/bill/{{orderNumber}}",
  "voicemailAction": "LEAVE_MESSAGE",
  "voicemailMessage": "Hurmatli mijoz, sizga Uysot kompaniyasidan qo'ng'iroq qildik. Iltimos qayta bog'laning.",
  "dtmfInputEnabled": true,
  "emotionAdaptiveVoice": true,
  "cronExpression": "0 0 9 ? * MON-FRI",
  "dailyStartTime": "09:00:00",
  "dailyEndTime": "18:00:00"
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `name` | `string` | ✅ | Kampaniya nomi |
| `scenarioId` | `number` | ✅ | Bog'langan stsenariy ID raqami |
| `defaultLanguage` | `string` | ❌ | Standart til (`uz-UZ`, `ru-RU`, `en-US`). Sukut bo'yicha `uz-UZ` |
| `defaultVoice` | `string` | ❌ | Standart TTS ovoz |
| `languageVoices` | `Map<string, string>` | ❌ | Har bir til uchun alohida TTS ovoz xaritasi |
| `sipTrunkIds` | `array<number>` | ❌ | **Chiquvchi SIP trunklar ID ro'yxati**. Agar tanlanmasa (`null` yoki `[]`), kompaniyaning barcha faol trunklari bo'yicha Round-Robin yuklama taqsimlanadi. Bir yoki bir nechta trunk tanlansa, qo'ng'iroqlar faqat o'sha tanlangan trunklar bo'yicha navbatma-navbat amalga oshiriladi. Tanlanganlarning bir qismi o'chirilgan bo'lsa, qolganlari ishlatiladi (warn log); **hammasi o'chirilgan yoki o'chirib tashlangan bo'lsa, dialer kampaniyani avtomatik `PAUSED` ga o'tkazadi** va `SIP_TRUNK_SELECTION_UNAVAILABLE` xatosini logga yozadi. Trunk qayta yoqilgach, kampaniya `POST /start` bilan qo'lda qayta ishga tushiriladi. |
| `disclosureEnabled` | `boolean` | ❌ | Sun'iy intellekt ekanligini oshkor qilish (Disclosure) |
| `ambientSound` | `string` | ❌ | Fon shovqini (`OFF`, `OFFICE_BACKGROUND`, `CALL_CENTER_AMBIENCE`) |
| `midCallSmsEnabled` | `boolean` | ❌ | Suhbat davomida SMS yuborish imkoniyati |
| `midCallSmsTemplate` | `string` | ❌ | SMS shabloni (`{{varName}}` dinamik parametrlar bilan) |
| `voicemailAction` | `string` | ❌ | Avtootvetchik aniqlangandagi amal (`HANGUP`, `LEAVE_MESSAGE`, `IGNORE`) |
| `voicemailMessage` | `string` | ❌ | Avtootvetchikka qoldiriladigan xabar |
| `dtmfInputEnabled` | `boolean` | ❌ | DTMF raqam terishni qabul qilish |
| `emotionAdaptiveVoice` | `boolean` | ❌ | Mijoz kayfiyatiga qarab ovoz ohangini moslashtirish |
| `cronExpression` | `string` | ❌ | Takroriy avtomatik boshlash jadvali (CRON) |
| `dailyStartTime` | `string` (time) | ❌ | Kunlik ruxsat etilgan boshlanish vaqti (masalan `09:00:00`) |
| `dailyEndTime` | `string` (time) | ❌ | Kunlik ruxsat etilgan tugash vaqti (masalan `18:00:00`) |

---

### `PUT /api/campaigns/{id}` — Kampaniyani tahrirlash

Mavjud kampaniya ma'lumotlarini (jumladan `sipTrunkIds` tanlovini) yangilaydi.

**Request Body** (`UpdateCampaignRequest`):
```json
{
  "name": "Kechikkan to'lovlar - Mart (Yangilangan)",
  "scenarioId": 4,
  "defaultLanguage": "uz-UZ",
  "defaultVoice": "dilnavoz",
  "languageVoices": {
    "uz-UZ": "dilnavoz"
  },
  "sipTrunkIds": [2, 3],
  "disclosureEnabled": true,
  "ambientSound": "OFF",
  "midCallSmsEnabled": false,
  "midCallSmsTemplate": null,
  "voicemailAction": "HANGUP",
  "voicemailMessage": null,
  "dtmfInputEnabled": false,
  "emotionAdaptiveVoice": true,
  "cronExpression": null,
  "dailyStartTime": "09:00:00",
  "dailyEndTime": "19:00:00"
}
```

---

### `GET /api/campaigns/{id}` — Kampaniya tafsilotlari

Bitta kampaniya ma'lumotlarini, unga biriktirilgan `sipTrunkIds`, nishonlar statistikasi (`totalTargets`, `calledTargets`, `pendingTargets`, `completedTargets`) va joriy holatini qaytaradi.

**Response** (`CampaignRow`):
```json
{
  "accept": true,
  "data": {
    "id": 12,
    "name": "Kechikkan to'lovlar - Mart 2026",
    "status": "ACTIVE",
    "scenarioId": 4,
    "scenarioName": "Qarz undirish v2",
    "defaultLanguage": "uz-UZ",
    "defaultVoice": "dilnavoz",
    "sipTrunkIds": [1, 2],
    "totalTargets": 1200,
    "calledTargets": 750,
    "pendingTargets": 450,
    "completedTargets": 700,
    "disclosureEnabled": true,
    "ambientSound": "OFFICE_BACKGROUND",
    "cronExpression": "0 0 9 ? * MON-FRI",
    "createdAt": "2026-03-01T08:30:00Z"
  },
  "errors": null
}
```

---

### `POST /api/campaigns/list` va `POST /api/campaigns/filter` — Kampaniyalar ro'yxati

Filtrlash, qidirish va sahifalash bilan kampaniyalar ro'yxatini oladi. Har bir elementda nishonlar statistikasi (`totalTargets`, `calledTargets`, `pendingTargets`, `completedTargets`) mavjud bo'ladi.

---\n\n## 2. Nishonlarni CSV'dan yuklash\n\n### `POST /api/campaigns/{id}/targets/csv`\n\n`Content-Type: text/csv` (yoki `text/plain`), body — CSV faylning o'zi:\n\n```csv\nclientId,phone,language,clientName,debtAmount,debtDay,currency,dueDate,orderNumber,deliveryAddress\n1001,+998901234567,uz-UZ,Aziz Karimov,1500000,15,so'm,2026-07-01,ORD-1029,Toshkent Chilonzor\n```\n\n### 🪄 Aqlli Ko'p Tilli CSV Avto-Moslashuvi\nTizim CSV sarlavhalarini o'zbek, rus va ingliz tillarida avtomatik tushunadi va moslashtiradi:\n- **Telefon**: `phone`, `tel`, `raqam`, `telefon`, `nomer`, `contact`\n- **Ism**: `clientName`, `name`, `fio`, `ism`, `mijoz`, `imya`\n- **Qarz summasi / Summa**: `debtAmount`, `qarz`, `summa`, `dolg`, `amount`, `total`\n- **Kechikkan kunlar soni**: `debtDay`, `kechikish_kunlari`, `kechikish`, `prosrochka`, `overdue_days`, `days`\n- **Muddati**: `dueDate`, `muddat`, `srok`, `sana`, `date`\n- **Istalgan qo'shimcha ustunlar**: CSV dagi har qanday qo'shimcha ustun (masalan `orderNumber`, `deliveryAddress`, `company`) avtomatik ravishda `contextData` JSON ga olinadi va stsenariy promptida `{{orderNumber}}`, `{{deliveryAddress}}` kabi to'g'ridan-to'g'ri ishlatilishi mumkin!\n\n### 📝 Dinamik Prompt Shablon Sintaksisi (Double Curly Braces)\nStsenariy matnlarida istalgan o'zgaruvchini `{{varName}}` yoki sukut bo'yicha qiymat bilan `{{varName | "Standart qiymat"}}` ko'rinishida yozish mumkin:\n```text\n"Assalomu alaykum {{clientName | "Hurmatli mijoz"}}! Sizning {{orderNumber}} raqamli buyurtmangiz {{deliveryAddress}} manziliga yetkazilmoqda."\n```\n\n---\n\n## 3. Kampaniyani boshqarish amallari\n\n- `POST /api/campaigns/{id}/start?immediate=false` — Kampaniyani ishga tushirish (`ACTIVE`).\n  `immediate=false` (sukut bo'yicha): har bir nishon o'ziga belgilangan vaqtni kutadi — hozirgi xatti-harakat.\n  `immediate=true`: kutayotgan (`PENDING`) nishonlarning `nextAttemptAt` i tozalanadi va ular keyingi dialer tickda navbatga tushadi.\n  Urinishlar soni (`attempts`) saqlanadi — limitini tugatgan nishon qayta chaqirilmaydi. Har ikki holatda ham\n  `dialWindowStart/End` va `dialDays` cheklovi kuchida qoladi: `immediate` "hoziroq", emas, "ruxsat etilgan eng yaqin paytda" degani.\n- `POST /api/campaigns/{id}/pause` — Kampaniyani vaqtincha to'xtatish (`PAUSED`).\n- `POST /api/campaigns/{id}/cancel` — Kampaniyani bekor qilish (`CANCELLED`).\n- `POST /api/campaigns/{id}/clone` — Kampaniyadan nusxa olish (barcha sozlamalar va `sipTrunkIds` bilan birga).\n- `POST /api/campaigns/{id}/archive` — Kampaniyani arxivlash.\n- `POST /api/campaigns/{id}/speed?callsPerMinute=N` — Qo'ng'iroqlar tezligini sozlash.\n