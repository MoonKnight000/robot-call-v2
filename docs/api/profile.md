# Profilim (self-service) API

`uz.murodjon.uysotvoice.profile` · rol: istalgan (kirgan bo'lsa yetarli) · API-REQUIREMENTS §15, UI-DESIGN §8.3

Har bir endpoint faqat **chaqirgan foydalanuvchining o'z** `app_user`
qatoriga ishlaydi — `CurrentUser` (JWT) orqali aniqlanadi, path'da `id`
yo'q. Boshqa foydalanuvchini boshqarish uchun (invite/rol/block) —
[users.md](users.md), ADMIN-only.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## `GET /api/profile` — "Umumiy" tab

**Javob** (`Profile`):

```json
{
  "id": 1, "name": "Aziz Bekmurodov", "username": "aziz", "email": "aziz@uysot.uz",
  "phone": "+998901234567", "position": "Operator", "avatarFileId": null,
  "role": "OPERATOR", "companyId": 1,
  "lastLoginAt": "2026-08-02T08:00:00Z", "createdAt": "2026-07-01T00:00:00Z"
}
```

`avatarFileId` — [files.md](files.md)dagi `GET /api/files/{avatarFileId}`ga
beriladigan id, xom MinIO URL emas.

## `PUT /api/profile` — "Umumiy" tab, saqlash

Body (`UpdateProfileRequest`):

```json
{ "name": "Aziz Bekmurodov", "email": "aziz@uysot.uz", "phone": "+998901234567",
  "position": "Operator" }
```

`username` bu yerda yo'q — login identifikatori, o'zgarmaydi. `email`
hozircha har doim tahrirlanadi: loyihada hali SSO provayder yo'q (Uysot
OAuth, `POST /api/auth/uysot/callback`, hali stub), shuning uchun
UI-DESIGN §8.3'dagi "email o'zgarmas, agar SSO bo'lsa" qoidasi hozircha
qo'llanmaydi. Boshqa foydalanuvchining email'i band bo'lsa — `400`.
**`avatarFileId` bu yerda yo'q** — avatar faqat pastdagi
`POST /api/profile/avatar` orqali o'zgaradi, qo'lda arbitrar id sifatida
yuborib bo'lmaydi.

---

## `POST /api/profile/avatar` — avatar yuklash

`multipart/form-data`, maydon nomi `file`. Faqat rasm (`image/png`,
`image/jpeg`, `image/webp`), maksimum **5 MB** — mos kelmasa `400`, MinIO
ishlamasa `502`. Muvaffaqiyatli yuklangan fayl `Profile.avatarFileId`ni
almashtiradi, boshqa hech qaysi maydonga tegmaydi.

Javob — yangilangan `Profile` (yuqoridagi shakl, yangi `avatarFileId` bilan).

---

## `PUT /api/profile/password` — "Xavfsizlik" tab, parol almashtirish

Body (`ChangePasswordRequest`):

```json
{ "currentPassword": "eski-parol", "newPassword": "kamida-8-belgi" }
```

`currentPassword` mos kelmasa — `400`. Muvaffaqiyatda `200`, boshqa
sessiyalar (`user_session`) darhol bekor qilinmaydi — kerak bo'lsa
foydalanuvchi ularni pastdagi endpoint bilan alohida tugatadi.

---

## `GET /api/profile/sessions` — faol sessiyalar

**Javob** (`List<UserSession>`):

```json
[
  { "id": 5, "device": "Mozilla/5.0 (Windows NT 10.0...)", "ipAddress": "10.0.0.4",
    "createdAt": "2026-08-01T09:00:00Z", "lastActivityAt": "2026-08-02T07:40:00Z" }
]
```

Har bir qator — bitta qurilmadan qilingan login (`user_session`), hali
bekor qilinmagan (`revoked_at IS NULL`) va muddati o'tmagan. `device` —
o'sha login/refresh so'rovining `User-Agent` sarlavhasi (xom matn, klient
kerak bo'lsa o'zi chiroyli formatga o'giradi).

## `DELETE /api/profile/sessions/{id}` — sessiyani tugatish

Berilgan `id` boshqa foydalanuvchiniki bo'lsa yoki mavjud bo'lmasa — jim
tarzda hech narsa qilmaydi (`200`), boshqa revoke endpointlari bilan bir
xil konventsiya. Tugatilgan sessiyaning `refreshToken`i endi
`POST /api/auth/refresh`da ishlamaydi — o'sha qurilma qayta login qilishi
kerak bo'ladi.

---

## `GET /api/profile/notifications` — "Bildirishnomalar" tab

**Javob** (`List<PersonalNotificationMatrixEntry>`):

```json
[
  { "type": "OPERATOR_REQUEST", "channel": "EMAIL", "enabled": true },
  { "type": "ERROR_OCCURRED", "channel": "TELEGRAM", "enabled": false }
]
```

Yo'q qator — o'chirilgan degani (matritsa bo'sh bo'lsa, hammasi o'chiq).
Bu shaxsiy (foydalanuvchi darajasidagi) matritsa — [settings.md](settings.md)dagi
`GET/PUT /api/settings/notifications` kompaniya darajasidagi matritsadan
mustaqil, ikkalasi ham parallel ishlaydi.

## `PUT /api/profile/notifications` — matritsani saqlash

Body (`UpdatePersonalNotificationSettingsRequest`):

```json
{ "matrix": [ { "type": "OPERATOR_REQUEST", "channel": "EMAIL", "enabled": true } ] }
```

Butun matritsani almashtiradi (jo'natilmagan katak — o'chiq bo'lib qoladi).

---

## `GET /api/profile/schedule` — "Ish jadvali" tab

**Javob** (`List<ScheduleSlot>`):

```json
[ { "dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "18:00" } ]
```

Operator qaysi hafta kuni/soatlarda kiruvchi qo'ng'iroq qabul qilishga
tayyor (inbound transfer uchun, ROADMAP C.4). Hozircha faqat saqlanadi —
kiruvchi marshrutlash bu jadvalni hali o'qimaydi (kelgusi bosqich).

## `PUT /api/profile/schedule` — jadvalni saqlash

Body (`UpdateScheduleRequest`):

```json
{ "slots": [ { "dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "18:00" } ] }
```

`startTime >= endTime` bo'lgan slot — `400`. Butun jadvalni almashtiradi.

---

## `GET /api/profile/table-config/{key}` — jadval sozlamasi

Har qanday jadval uchun erkin, foydalanuvchiga xos UI sozlamasi
(backend-uchun-talablar.md §1, API-REQUIREMENTS §4 "Ustunlar ⚙") —
`app_user.call_columns`dan farqli, faqat qo'ng'iroqlar jadvaliga
cheklanmagan. `key` — frontend o'zi tanlagan nom (masalan
`"callsTableColumns"`, `"campaignsTableColumns"`); qiymatning JSON shakli
ham frontendning o'z ixtiyorida, backend uni o'zgartirmasdan
saqlaydi/qaytaradi.

**Javob** — xom JSON qiymat (o'rab olinmagan), yoki hech qachon
saqlanmagan bo'lsa `null`:

```json
{ "data": ["phone", "campaign", "disposition", "duration"], "message": null, "messageCode": null, "accept": true, "errors": null }
```

## `PUT /api/profile/table-config/{key}` — jadval sozlamasini saqlash

Body — xom JSON qiymat (o'rab olinmagan), masalan:

```json
["phone", "campaign", "disposition", "duration"]
```

`null` yuborish (yoki body'ni umuman yubormaslik) shu `key` uchun
saqlangan qiymatni o'chiradi — standart holatga qaytish. Javob — saqlangan
qiymatning o'zi (yuqoridagi `GET` shakli).

---

## `GET /api/profile/today-stats` — popover mini-statistika

**Javob** (`TodayStats`):

```json
{ "totalCalls": 24, "answeredCalls": 22, "onAirMinutes": 18.4, "qualityPct": 91.7 }
```

Operator-scoped (backend-uchun-talablar.md §6, tuzatildi) — faqat shu
foydalanuvchining o'z SIP extensioniga uzatilgan va javob berilgan
qo'ng'iroqlar hisoblanadi (`call_attempt.operator_user_id`,
`ReportRepository#operatorTotals`, UTC kun chegarasi). Faqat bot ishlagan
qo'ng'iroqlar hech kimning shaxsiy hisobiga kirmaydi — shuning uchun hech
qachon qo'ng'iroq qabul qilmagan operator uchun bu yerda hammasi `0`
ko'rinishi kutilgan holat, xato emas.
