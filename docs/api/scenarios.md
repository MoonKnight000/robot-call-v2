## Built-in (Tayyor) Ssenariy Shablonlari

Tizimda biznesning eng ko'p talab qilinadigan sohalari uchun 10 ta built-in stsenariy mavjud:

1. **`debt-collection`** — Qarzdorlikni eslatish, to'lov sanasi va summasini kelishish (`promisedDate`, `promisedAmount`), rad etish sababini yozib olish.
2. **`order-confirmation`** — Yangi buyurtmani tasdiqlash, yetkazib berish manzili va qulay vaqtini aniqlash yoki bekor qilish sababini qayd qilish.
3. **`welcome-onboarding`** — Yangi ro'yxatdan o'tgan mijoz bilan salomlashish, tizimdan foydalanishda yordam ko'rsatish va demo uchrashuv belgilash.
4. **`appointment-reminder`** — Shifokor qabuli, klinika yoki servis uchrashuvlarini eslatish va tasdiqlash.
5. **`lead-qualification`** — Savdo so'rovlarini saralash, qiziqish darajasi va byudjetni aniqlab, mutaxassisga yo'naltirish.
6. **`survey`** — CSAT / NPS xizmat ko'rsatish sifati so'rovnomasi.
7. **`notification`** — Muhim yangilik yoki shaxsiy bildirishnomalarni yetkazish.
8. **`reception`** — Kiruvchi qo'ng'iroqlarni qabul qiluvchi aqlli AI kotiba (FAQ va tegishli bo'limga uzatish).
9. **`inbound-lead`** — Reklamadan kirib kelgan qo'ng'iroqlarni qabul qilish va kontakt ma'lumotlarini bazaga saqlash.
10. **`callback-request`** — Operatorlar band bo'lganda kiruvchi mijozdan qulay qayta qo'ng'iroq vaqtini so'rab olish.

---

## 🪄 Dinamik Prompt Shablon Sintaksisi (Double Curly Braces)

Stsenariylar matnlarida (`rolePrompt`, `stages.purpose`, `disclosureText`) CSV yoki API orqali keladigan har qanday faktlarni dinamik ineksiya qilish mumkin:
- Oddiy o'zgaruvchi: `{{clientName}}`, `{{debtAmount}}`, `{{orderNumber}}`, `{{deliveryAddress}}`
- Zaxira qiymat (fallback): `{{clientName | "Hurmatli mijoz"}}`, `{{currency | "so'm"}}`

---

## `POST /api/scenarios/list` — Ro'yxat

Body — `ScenarioFilter` (`page`/`size`/`orders`). Saralanadigan ustunlar: `ID`, `NAME`, `UPDATED_AT`. Standart: `ID ASC`.
Filter parametri `activeOnly: true` berilsa — faqat joriy versiyalar qaytariladi.
