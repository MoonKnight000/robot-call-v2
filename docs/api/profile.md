# Profilim (self-service) API

`uz.murodjon.robotcallv2.profile` · huquq: talab qilinmaydi (kirgan bo'lsa yetarli) · API-REQUIREMENTS §15, UI-DESIGN §8.3

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
  "data": {
    "id": 1,
    "name": "Aziz Bekmurodov",
    "username": "aziz",
    "email": "aziz@uysot.uz",
    "phone": "+998901234567",
    "position": "Operator",
    "avatarFileId": null,
    "roleId": 3,
    "roleCode": "OPERATOR",
    "roleName": "Operator",
    "companyId": 1,
    "lastLoginAt": "2026-08-02T08:00:00Z",
    "createdAt": "2026-07-01T00:00:00Z",
    "callColumns": ["startedAt", "phone", "clientName", "disposition", "durationSec"]
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

`avatarFileId` — [files.md](files.md)dagi `GET /api/files/{avatarFileId}`ga
beriladigan id, xom MinIO URL emas.

`roleId`/`roleCode`/`roleName` — foydalanuvchining roli ([roles.md](roles.md)). Bitta
`role` maydoni **yo'q**; kompaniyaning o'z rolida `roleCode` — `null`.
`callColumns` — qo'ng'iroqlar jadvalida ko'rsatiladigan ustunlar tartibi
(`PUT /api/profile/call-columns` bilan saqlanadi).

## `PUT /api/profile` — "Umumiy" tab, saqlash

Body (`UpdateProfileRequest`):

```json
{
  "name": "Aziz Bekmurodov",
  "email": "aziz@uysot.uz",
  "phone": "+998901234567",
  "position": "Operator"
}
```

`username` bu yerda yo'q — login identifikatori, o'zgarmaydi. Boshqa foydalanuvchining email'i band bo'lsa — `400`.
**`avatarFileId` bu yerda yo'q** — avatar faqat pastdagi
`POST /api/profile/avatar` orqali o'zgaradi, qo'lda arbitrar id sifatida
yuborib bo'lmaydi.

**Javob** — yangilangan `Profile`.

---

## `POST /api/profile/avatar` — avatar yuklash

`multipart/form-data`, maydon nomi `file`. Faqat rasm (`image/png`,
`image/jpeg`, `image/webp`), maksimum **5 MB** — mos kelmasa `400`, MinIO
ishlamasa `502`. Muvaffaqiyatli yuklangan fayl `Profile.avatarFileId`ni
almashtiradi, boshqa hech qaysi maydonga tegmaydi.

**Javob** — yangilangan `Profile` (yangi `avatarFileId` bilan).

---

## `PUT /api/profile/password` — "Xavfsizlik" tab, parol almashtirish

Body (`ChangePasswordRequest`):

```json
{
  "currentPassword": "eski-parol",
  "newPassword": "kamida-8-belgi"
}
```

`currentPassword` mos kelmasa — `400`. Muvaffaqiyatda `200` (`ResponseData<Void>`), boshqa
sessiyalar (`user_session`) darhol bekor qilinmaydi — kerak bo'lsa
foydalanuvchi ularni pastdagi endpoint bilan alohida tugatadi.

---

## `GET /api/profile/sessions` — faol sessiyalar

**Javob** (`List<UserSessionRow>`):

```json
{
  "data": [
    {
      "id": 5,
      "device": "Mozilla/5.0 (Windows NT 10.0...)",
      "ipAddress": "10.0.0.4",
      "createdAt": "2026-08-01T09:00:00Z",
      "lastActivityAt": "2026-08-02T07:40:00Z"
    }
  ],
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

Har bir qator — bitta qurilmadan qilingan login (`user_session`), hali
bekor qilinmagan (`revoked_at IS NULL`) va muddati o'tmagan. `device` —
o'sha login/refresh so'rovining `User-Agent` sarlavhasi.

## `DELETE /api/profile/sessions/{id}` — sessiyani tugatish

Berilgan `id` boshqa foydalanuvchiniki bo'lsa yoki mavjud bo'lmasa — jim
tarzda hech narsa qilmaydi (`200`), boshqa revoke endpointlari bilan bir
xil konventsiya. Tugatilgan sessiyaning `refreshToken`i endi
`POST /api/auth/refresh`da ishlamaydi.

---

## `GET /api/profile/notifications` — "Bildirishnomalar" tab

**Javob** (`List<PersonalNotificationMatrixEntry>`):

```json
{
  "data": [
    { "type": "OPERATOR_REQUEST", "channel": "EMAIL", "enabled": true },
    { "type": "ERROR_OCCURRED", "channel": "TELEGRAM", "enabled": false }
  ],
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

Yo'q qator — o'chirilgan degani (matritsa bo'sh bo'lsa, hammasi o'chiq).
Bu shaxsiy (foydalanuvchi darajasidagi) matritsa — [settings.md](settings.md)dagi
`GET/PUT /api/settings/notifications` kompaniya darajasidagi matritsadan
mustaqil, ikkalasi ham parallel ishlaydi.

## `PUT /api/profile/notifications` — matritsani saqlash

Body (`UpdatePersonalNotificationSettingsRequest`):

```json
{
  "matrix": [
    { "type": "OPERATOR_REQUEST", "channel": "EMAIL", "enabled": true }
  ]
}
```

Butun matritsani almashtiradi.

---

## `GET /api/profile/schedule` — "Ish jadvali" tab

**Javob** (`List<ScheduleSlot>`):

```json
{
  "data": [
    { "dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "18:00" }
  ],
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

Operator qaysi hafta kuni/soatlarda kiruvchi qo'ng'iroq qabul qilishga
tayyor (inbound transfer uchun).

## `PUT /api/profile/schedule` — jadvalni saqlash

Body (`UpdateScheduleRequest`):

```json
{
  "slots": [
    { "dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "18:00" }
  ]
}
```

`startTime >= endTime` bo'lgan slot — `400`. Butun jadvalni almashtiradi.

---

## `PUT /api/profile/call-columns` — qo'ng'iroqlar jadvali ustunlari

Qo'ng'iroqlar hisoboti jadvali uchun foydalanuvchi tanlagan ustunlar ro'yxatini saqlash.

Body (`UpdateCallColumnsRequest`):

```json
{
  "columns": ["phone", "campaign", "disposition", "duration", "startedAt"]
}
```

**Javob** (`List<String>`):

```json
{
  "data": ["phone", "campaign", "disposition", "duration", "startedAt"],
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

---

## `GET /api/profile/table-config/{key}` — jadval sozlamasi

Har qanday jadval uchun erkin, foydalanuvchiga xos UI sozlamasi
(API-REQUIREMENTS §4 "Ustunlar ⚙"). `key` — frontend o'zi tanlagan nom (masalan
`"callsTableColumns"`, `"campaignsTableColumns"`); qiymatning JSON shakli
ham frontendning o'z ixtiyorida, backend uni o'zgartirmasdan
saqlaydi/qaytaradi.

**Javob** — saqlangan JSON qiymat, yoki hech qachon
saqlanmagan bo'lsa `null`:

```json
{
  "data": ["phone", "campaign", "disposition", "duration"],
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

## `PUT /api/profile/table-config/{key}` — jadval sozlamasini saqlash

Body — ixtiyoriy JSON qiymat (masalan massiv yoki obyekt):

```json
["phone", "campaign", "disposition", "duration"]
```

`null` yuborish (yoki body'ni bo'sh qoldirish) shu `key` uchun
saqlangan qiymatni o'chiradi — standart holatga qaytish. Javob — saqlangan
qiymatning o'zi.

---

## `GET /api/profile/today-stats` — popover mini-statistika

**Javob** (`TodayStats`):

```json
{
  "data": {
    "totalCalls": 24,
    "answeredCalls": 22,
    "onAirMinutes": 18.4,
    "qualityPct": 91.7
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

Operator-scoped — faqat shu foydalanuvchining o'z SIP extensioniga uzatilgan va javob berilgan
qo'ng'iroqlar hisoblanadi (`call_attempt.operator_user_id`, UTC kun chegarasi).
