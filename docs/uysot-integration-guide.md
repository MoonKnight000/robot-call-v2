# UySot CRM va AI Voice Call Center Integratsiya Qo'llanmasi

Ushbu qo'llanma **UySot CRM** (`https://uysot.uz`, API: `https://apidoc.app.uysot.uz/`) va **Robot Call v2 (AI Voice Agent)** platformasi o'rtasidagi to'liq avtomatlashtirilgan integratsiyani sozlash va ishlatish bo'yicha qo'llanmadir.

---

## 1. Umumiy Arxitektura va Oqim

```
[Mijoz (Instagram/Web/Reklama)]
              │
              ▼
       [UySot CRM]  ──(1. Webhook: LEAD_CREATED)──►  [Robot Call v2 Platform]
              ▲                                                │ (2. <10s Instant Call)
              │                                                ▼
              │                                      [Mijoz bilan AI Suhbat]
              │                                                │
              │  ◄──(3. Function Calling: Shourum/SMS)─────────┤
              │                                                │ (4. Call Finalized)
              │  ◄──(5. POST /v1/open-api/task)────────────────┤
              │  ◄──(6. POST /v1/open-api/lead/{id}/status)────┤
              │  ◄──(7. POST /v1/open-api/call-history)────────┤
              │  ◄──(8. POST /v1/open-api/lead/note + Audio)───┘
```

### Asosiy Imkoniyatlar:
1. **Speed-to-Lead (< 10 soniya):** Yangi lid tushishi bilanoq tizim unga avtomatik qo'ng'iroq qiladi.
2. **Jonli Funksiyalar (Live Tool Calling):** Suhbat jarayonida sun'iy intellekt mijozga lokatsiya SMS yuborishi, katalog taqdim etishi yoki shourumga uchrashuv yozishi mumkin.
3. **CRM Bosqichini Avtomatik O'zgartirish (Pipeline Stage Sync):** Suhbat natijasiga qarab (uchrashuv belgilandi, qayta qo'ng'iroq, rad etildi) lid avtomatik tarzda kerakli voronka bosqichiga ko'chiriladi.
4. **Topshiriq va Uchrashuv Yaratish:** Muvaffaqiyatli kelishuv bo'lganda, sotuv menejeri uchun CRM'da avtomatik topshiriq ochiladi.
5. **Audio Yozuv va Xulosa:** Har bir suhbat matni, tahlili va audio yozuv manzili to'g'ridan-to'g'ri lid kartochkasiga izoh va qo'ng'iroqlar tarixi sifatida yoziladi.
6. **Shartnomalar va Qarzdorlik Monitoringi:** UySot shartnomalari bo'yicha to'lovi kechikkan mijozlarga avtomatik eslatma qo'ng'iroqlari amalga oshiriladi.

---

## 2. Inbound Webhook Sozlamasi (Yangi Lidlar Uchun)

UySot CRM administrator panelida Webhook bo'limiga o'ting va quyidagi manzilni kiriting:

- **Webhook URL:** `POST https://<sizning-domen>/api/public/uysot/webhook`
- **Hodisa turi (EventType):** `LEAD_CREATED`
- **Autentifikatsiya:** Ochiq havola (yoki `X-Webhook-Secret` sarlavhasi orqali).

### Webhook Payload namunasi:
```json
{
  "eventId": "7c1f4b2a-9d38-4f61-b0e2-5a8c3d17e904",
  "eventType": "LEAD_CREATED",
  "occurredAt": 1766131200,
  "companyId": 34,
  "lead": {
    "id": 99436,
    "name": "Ali Valiyev",
    "statusId": 11,
    "statusName": "New contact",
    "pipeId": 4,
    "pipeName": "Sales",
    "responsibleId": 252,
    "responsibleName": "Dilshod Karimov",
    "balance": 0,
    "currency": "UZS",
    "phone": "+998901234567",
    "attributions": [
      {
        "channelId": 77,
        "source": "INSTAGRAM",
        "channelValue": "uysot_official",
        "attachedTimestamp": 1766131199
      }
    ],
    "uuid": "1f0c8a52-6b3d-4a71-9c2e-0d5b8e4a7c31",
    "createdTimestamp": 1766131199,
    "updatedTimestamp": 1766131199
  }
}
```

Ushbu so'rov kelishi bilan backend darhol:
1. Telefon raqamini normallashtiradi (`+998901234567`).
2. Agar telefon Do-Not-Call ro'yxatida bo'lmasa, navbatga qo'yadi va 5-10 soniya ichida qo'ng'iroqni boshlaydi.
3. Suhbat jarayonida `real_estate.json` ssenariysini yuklaydi.

---

## 3. Backend Konfiguratsiyasi (`application.yml`)

Platformaning `application.yml` faylida quyidagi parametrlarni sozlang:

```yaml
voice-agent:
  crm:
    enabled: true
    base-url: "https://api.service.app.uysot.uz"  # Test muhiti uchun dev server URL kiritiladi
    api-token: "${UYSOT_OPEN_API_TOKEN:your-uysot-token-here}"
    call-history-employee-id: 252                # AI qo'ng'iroqlari biriktiriladigan Uysot xodimi ID raqami
    
    # Open API yo'llari
    lead-path: "/v1/open-api/lead/{id}"
    lead-filter-path: "/v1/open-api/lead/filter"
    lead-note-path: "/v1/open-api/lead/note"
    contract-path: "/v1/open-api/contract/{id}"
    contract-filter-path: "/v1/open-api/contract/filter"
    call-history-path: "/v1/open-api/call-history"
    task-path: "/v1/open-api/task"
    lead-status-path: "/v1/open-api/lead/{id}/status"

    # Pipeline Status ID moslamalari (UySot voronkangizdagi status ID lari)
    meeting-booked-status-id: 15     # "Uchrashuv belgilandi" status ID
    not-interested-status-id: 19     # "Rad etdi / Qiziqmadi" status ID
    callback-status-id: 12           # "Qayta aloqa / Bog'lanib bo'lmadi" status ID
    promise-to-pay-status-id: 22     # "To'lov va'dasi olindi" status ID
```

> [!TIP]
> Status ID raqamlarini UySot CRM dasturining **Sozlamalar -> Voronkalar va Bosqichlar** bo'limidan yoki `GET /v1/open-api/lead/filter` orqali olishingiz mumkin.

---

## 4. Jonli Funksiyalar (Real Estate Function Calling)

Muloqot davomida AI operator quyidagi ichki vositalardan (tools) foydalanadi:

| Funksiya Nomi | Tavsifi | Yuboriladigan Argumentlar |
|---|---|---|
| `bookShowroomVisit` | Sotuv ofisi / shourumga mijoz tashrifini tasdiqlaydi va maqsadga erishadi | `visitTime` (masalan: `2026-09-20 15:00`), `visitorCount`, `notes` |
| `sendLocationSms` | Shourum manzili yoki Google/Yandex Maps lokatsiya linkini SMS orqali yuboradi | `phone` (ixtiyoriy, avtomatik olinadi), `locationLink`, `address` |
| `sendCatalogSms` | Turar-joy majmuasi narxlari va loyiha taqdimot fayli linkini SMS orqali yuboradi | `phone`, `catalogUrl` |

Ushbu vositalar chaqirilganda mijozga darhol tegishli ma'lumot SMS orqali jo'natiladi va suhbat kontekstiga yoziladi.

---

## 5. Suhbat Yakunlangandan Keyingi Avtomatlashtirish

Qo'ng'iroq yakunlanganda (`CallFinalizer` va `OutboxDeliveryService`) quyidagi amallar bajariladi:

### 1. UySot CRM Topshiriq Yaratish (`POST /v1/open-api/task`)
Agar mijoz tashrifga rozi bo'lsa yoki to'lov va'dasini bersa, quyidagi shaklda topshiriq yaratiladi:
```json
{
  "leadId": 99436,
  "title": "Sotuv ofisiga uchrashuv (AI bot)",
  "description": "AI Call Center orqali sotuv ofisiga uchrashuv belgilandi.\nUchrashuv vaqti: 2026-09-20 15:00\nXonalar: 3 xonali\nByudjet: 50 000 USD",
  "dueTimestamp": 1766217600
}
```

### 2. Voronka Bosqichini Yangilash (`POST /v1/open-api/lead/{id}/status`)
Lid avtomatik ravishda `meetingBookedStatusId` (masalan: 15 - "Uchrashuv belgilandi") bosqichiga ko'chiriladi.

### 3. Audio Yozuv va Suhbat Xulosasi (`POST /v1/open-api/lead/note`)
Lid kartochkasiga to'liq tahlil va audio yozuv havolasi joylanadi:
```text
AI operator bilan suhbat bo'lib o'tdi.
Xonalar: 3 xonali
Byudjet: 50 000 USD
Shourum tashrifi: 2026-09-20 15:00
Kayfiyat: POSITIVE
Suhbat sifati: 92/100
Audio yozuv: /api/files/10492
```

Menejer ushbu linkni bosib, suhbat audiosini bevosita tinglashi mumkin.

### 4. Qo'ng'iroqlar Tarixi (`POST /v1/open-api/call-history`)
Qo'ng'iroq davomiyligi, sanasi va audio fayli bilan qo'ng'iroqlar jadvaliga yoziladi.

---

## 6. Shartnomalar va Qarzdorlik Monitoringi (Overdue Debtor Pack)

Qarzdorliklarni undirish kampaniyasini ishga tushirish uchun:

1. **Kampaniya yaratish:** Kampaniya yaratishda Nishon manbai (Target Source Provider) sifatida `UYSOT_DEBTORS` tanlanadi.
2. **Avtomatik Filtrlash:** Tizim `POST /v1/open-api/contract/filter` orqali to'lov muddati kechikkan shartnomalarni (`delay > 0`, `residue > 0`) va ularga tegishli lid ma'lumotlarini yuklab oladi.
3. **Ssenariy:** `real_estate_contract.json` ssenariysi ishga tushadi:
   - AI bot shartnoma raqami va qarzdorlik summasini aytadi.
   - Kechikish sababini aniqlaydi.
   - To'lov sanasi bo'yicha va'da oladi.
   - Natija UySot CRM ga topshiriq va izoh sifatida qaytariladi.

---

## 7. Sinov va Tekshirish (Verification Checklist)

1. **Webhook sinovi:**
```bash
curl -X POST "http://localhost:8080/api/public/uysot/webhook" \
     -H "Content-Type: application/json" \
     -d '{
       "eventId": "test-1",
       "eventType": "LEAD_CREATED",
       "companyId": 1,
       "lead": {
         "id": 99436,
         "name": "Test Foydalanuvchi",
         "phone": "+998901234567"
       }
     }'
```
Kutilayotgan javob: `200 OK` bilan `{ "status": "QUEUED", ... }`.

2. **Test Qo'ng'irog'i (Web-test orqali):**
Brauzerdan web-test orqali shourumga tashrif buyurishni aytib sinab ko'ring. Suhbat yakunida CRM'da topshiriq paydo bo'lishini tekshiring.
