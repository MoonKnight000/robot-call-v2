# Foydalanuvchi Ko'rsatmalari va Doimiy Xotira (Antigravity Memory)

Ushbu papka va fayl foydalanuvchi tomonidan berilgan barcha doimiy qoidalar, arxitektura talablari va eslatmalarni saqlaydi. Har bir vazifada ushbu qoidalar avtomatik inobatga olinadi.

---

## 1. 📝 API Hujjatlashtirish (Documentation) Qoidasi
* **API'da har qanday o'zgarish bo'lganda**: `docs/api/` papkasidagi barcha tegishli `.md` fayllar va `docs/api/README.md` bir vaqtning o'zida, to'liq, misollar va DTO namunalari bilan parallel yangilab chiqilishi shart.
* Hech qanday endpoint yoki parametr chala qoldirilmasligi kerak.

---

## 2. 📞 Qo'ng'iroq va Ovoz Standartlari
* **Ulanish ohangi (Gudok / Connection Chime)**: Qo'ng'iroq ulanganda (`StasisStart`), bot gap boshlashidan oldin Telegram/VoIP kabi yoqimli ulanish "chime / gudok" ohangini chaladi.
* **Hissiyot (Sentiment Detector)**: Mijoz noroziligi yoki asabiylashishi avtomatik aniqlanib, moslashtirilgan xushmuomala javob qaytariladi yoki operatorga uzatiladi.

---

## 3. 🧠 Mijoz Xotirasi (Memory & Context Management)
* Qo'ng'iroqlar xotirasi ham avtomatik (CallSummary orqali), ham qo'lda (Operator eslatmalari, qo'shimcha faktlar) boshqariladi (`GET/PUT /api/campaigns/{id}/targets/{id}/memory`).
* Yangilangan xotira va eslatmalar keyingi qo'ng'iroqlarda to'g'ridan-to'g'ri AI System Promptiga beriladi.

---

## 4. 🔄 Takroriy Kampaniyalar (Campaign Recurrence)
* Kampaniyalar davriy takrorlanuvchi (`DAILY`, `WEEKLY`, `MONTHLY`, `CRON`) shaklda sozlanadi va `RecurringCampaignScheduler` orqali avtomatik ishga tushadi.

---

## 5. 🧪 Ssenariylarni Testlash
* Har bir ssenariy 3 xil usulda testlanishi ta'minlangan:
  1. **Interaktiv Veb Simulyator** (`POST /api/scenarios/simulate`) — brauzerda chat orqali qadamma-qadam.
  2. **AI vs AI Ko'p Personali Test** (`POST /api/scenarios/{id}/test-personas`) — 3 xil virtual mijoz orqali stress-test.
  3. **Jonli Telefon Sinovi** (`POST /api/calls/test`) — qoralamani real telefonga ulab tekshirish.

---

## 6. ⚙️ Arxitektura va Kodlash Qoidalari (`CLAUDE.md`)
* Bitta faylda bitta public type.
* Feature-first + layered package arxitekturasi (`controller`, `service`, `repository`, `dto`, `entity`, `enums`).
* Lombok ishlatilmaydi.
* Java 21, Spring Boot 3.4.5, Asterisk 20.
* Gradle build/testlarni sun'iy intellekt o'zi ishga tushirmaydi (foydalanuvchi qo'lda yurgizadi).
