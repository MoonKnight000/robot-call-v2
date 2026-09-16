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
| `ERROR_OCCURRED` | Qo'ng'iroq muvaffaqiyat darajasi (`PROMISE_TO_PAY` + `GOAL_REACHED` + `COMPLETED` ulushi — hisobotlardagi ta'rif bilan bir xil, [reports.md](reports.md)) chegaradan pastga tushganda | ✅ ulangan (`AlertingService.checkSuccessRate`) |
| `CAMPAIGN_FINISHED` | Kampaniya barcha nishonlarni tugatganda | ✅ ulangan (`CampaignService.checkCompletion`) |
| `DAILY_REPORT` | Rejalashtirilgan hisobot jo'natilganda | ✅ ulangan (`ReportScheduleService`, [reports.md](reports.md#3-rejalashtirilgan-hisobotlar-apireportsschedule)) |
| `MISSED_CALL` | Raqam uchun **yangi** javobsiz qo'ng'iroq yozuvi ochilganda (ish vaqtidan tashqari, navbatda go'shakni qo'ydi, bo'sh operator yo'q, qayta qo'ng'iroq so'radi, ovozli xabar). Shu raqamning ochiq yozuviga qo'shilgan takroriy qo'ng'iroq bildirishnoma bermaydi. Sarlavha: `"O'tkazib yuborilgan qo'ng'iroq"`, matn: `"998901234567 — ish vaqtidan tashqari"` | ✅ ulangan (`MissedCallService.record`, [missed-calls.md](missed-calls.md)) |
| `VOICEMAIL_RECEIVED` | Mijoz ovozli pochta qutisiga xabar qoldirganda — har bir xabar uchun. Sarlavha: `"Yangi ovozli xabar"` | ✅ ulangan (`VoicemailService.recordMessage`) |
| `LOW_SURVEY_SCORE` | Operator suhbatidan keyingi so'rovnomada mijoz 1 yoki 2 baho qo'yganda (navbatda `postCallSurvey: true`, [call-queues.md](call-queues.md)). Sarlavha: `"Past baho"`, matn: `"998901234567 operator suhbatini 2 ga baholadi"` | ✅ ulangan (`CallFlowRunner.survey`) |
| `CAMPAIGN_PAUSED` | Dialer kampaniyani o'zi to'xtatganda — yaroqli SIP trunk yo'q. Sarlavha: `"Kampaniya to'xtatildi"`, matn: `"\"<nom>\" kampaniyasi to'xtatildi: <sabab>"` | ✅ ulangan (`CampaignService.pauseByPlatform`) |
| `BALANCE_LOW` | Balans kampaniya qo'ng'irog'ini qoplay olmaganda — nishonlar 15 daqiqaga kutishga qo'yiladi; bitta kompaniyaga soatiga ko'pi bilan bir marta. Sarlavha: `"Balans yetarli emas"` | ✅ ulangan (`DialerService.dispatch`) |
| `TARGET_SYNC_FAILED` | Kampaniyaning target source'idan ro'yxat olib bo'lmaganda (qo'lda `targets/sync` yoki rejalashtirilgan). Sarlavha: `"Nishonlar sinxronlanmadi"` | ✅ ulangan (`CampaignService.importFromSource`) |
| `WEBHOOK_DELIVERY_FAILED` | Post-call webhook yoki post-call `WEBHOOK` action 4 marta urinib ham yetkazilmaganda (1, 5, 30 daqiqa oraliq bilan). Sarlavha: `"Webhook yetkazilmadi"`, matnda faqat host va xato (token yashiriladi) | ✅ ulangan (`WebhookDeliveryService.retryDue`) |

Hammasi haqiqiy producerga ega — popover'dagi har bir toggle ishlaydi.

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
**yoqilgan** (UI-DESIGN §8.2 popover'idagi toggle'lar), shuning uchun yangi
tur qo'shilganda mavjud foydalanuvchilar sukut bo'yicha chetlanib
qolmaydi.
