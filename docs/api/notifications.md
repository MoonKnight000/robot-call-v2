# Bildirishnomalar API

`uz.murodjon.robotcallv2.notification` · huquq: talab qilinmaydi (kirgan bo'lsa yetarli)
· UI-DESIGN §0.8/§8.2/§9

Topbar qo'ng'iroq ikonkasi va uning popover'idagi toggle'lar. Har bir
bildirishnoma kompaniya darajasida yaratiladi va shu kompaniyaning har bir
faol foydalanuvchisiga (o'zining preference'i yoqilgan bo'lsa) alohida qator
sifatida fan-out qilinadi — o'qilgan/o'qilmagan holat foydalanuvchi bo'yicha.

Umumiy javob shakli uchun [README.md](README.md)ga qarang.

Bu yerdagi hammasi **ichki** (in-app) bell — tashqi kanallarga (email/webhook/telegram)
kompaniya darajasida yetkazish uchun [settings.md](settings.md#4-kompaniya-bildirishnomalar-matritsasi-apisettingsnotifications)dagi
`GET/PUT /api/settings/notifications`ga qarang; ikkalasi bir `NotificationService
.notify(...)` chaqiruvidan parallel ishga tushadi.

---

## Turlari (`NotificationType`)

| Tur | Qachon | Producer holati |
|---|---|---|
| `OPERATOR_REQUEST` | Qo'ng'iroq operatorga uzatilganda | ✅ ulangan (`AriService.transferToOperator`) |
| `ERROR_OCCURRED` | Qo'ng'iroq muvaffaqiyat darajasi chegaradan pastga tushganda | ✅ ulangan (`AlertingService.checkSuccessRate`) |
| `CAMPAIGN_FINISHED` | Kampaniya barcha nishonlarni tugatganda | ✅ ulangan (`CampaignService.checkCompletion`) |
| `DAILY_REPORT` | Rejalashtirilgan hisobot jo'natilganda | ✅ ulangan (`ReportScheduleService`, [reports.md](reports.md#3-rejalashtirilgan-hisobotlar-apireportsschedule)) |

To'rttasi ham endi haqiqiy producerga ega — popover'dagi to'rtta toggle ham ishlaydi.

---

## `GET /api/notifications` — ro'yxat

Joriy foydalanuvchi uchun oxirgi 50 ta, yangi birinchi.

**Javob qatori** (`Notification`):

```json
{
  "id": 12, "type": "OPERATOR_REQUEST", "title": "Operatorga so'rov",
  "message": "Qo'ng'iroq abc123 operatorga uzatildi", "link": null,
  "read": false, "createdAt": "2026-08-02T09:00:00Z"
}
```

---

## `POST /api/notifications/{id}/read` — o'qilgan deb belgilash

Body yo'q. Bildirishnoma joriy foydalanuvchiga tegishli bo'lmasa (yoki
mavjud bo'lmasa) — jim tarzda no-op.

---

## `PUT /api/notifications/preferences` — toggle

Body (`UpdatePreferenceRequest`):

```json
{ "type": "ERROR_OCCURRED", "enabled": false }
```

Bitta so'rov — bitta tur. Qator yozilmagan tur uchun standart holat —
**yoqilgan** (UI-DESIGN §8.2 popover'idagi 4 ta toggle), shuning uchun yangi
tur qo'shilganda mavjud foydalanuvchilar sukut bo'yicha chetlanib
qolmaydi.
