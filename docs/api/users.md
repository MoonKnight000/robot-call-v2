# Foydalanuvchilar API

`uz.murodjon.robotcallv2.user` · huquq: **USER_READ** (o'qish) / **USER_EDIT** (yozish) · ROADMAP E.1,
UI-DESIGN §10.12

Login/sessiya endpointlari uchun [auth.md](auth.md)ga qarang — bu fayl faqat
hisob boshqaruvi (invite/rol/block).

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## `GET /api/users` — ro'yxat

Parametrsiz (kompaniyadagi foydalanuvchilar soni ixcham bo'lgani uchun to'liq ro'yxat). Joriy kompaniya bo'yicha,
`id` tartibida.

**Javob** (`List<UserRow>`):

```json
{
  "data": [
    {
      "id": 2,
      "companyId": 1,
      "name": "Aziz Bekmurodov",
      "username": "aziz.b",
      "email": "aziz@uysot.uz",
      "roleId": 3,
      "roleCode": "OPERATOR",
      "roleName": "Operator",
      "status": "ACTIVE",
      "lastLoginAt": "2026-08-02T07:00:00Z",
      "createdAt": "2026-07-15T00:00:00Z"
    }
  ],
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

`passwordHash` va `refreshTokenHash` hech qachon javobda chiqmaydi.

---

## `GET /api/users/{id}` — bitta foydalanuvchi

Path parametr: `id` (foydalanuvchi ID si). Faqat joriy kompaniyaga tegishli foydalanuvchini ko'rish mumkin.

**Javob** (`UserRow`):

```json
{
  "data": {
    "id": 2,
    "companyId": 1,
    "name": "Aziz Bekmurodov",
    "username": "aziz.b",
    "email": "aziz@uysot.uz",
    "roleId": 3,
    "roleCode": "OPERATOR",
    "roleName": "Operator",
    "status": "ACTIVE",
    "lastLoginAt": "2026-08-02T07:00:00Z",
    "createdAt": "2026-07-15T00:00:00Z"
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

Foydalanuvchi topilmasa yoki boshqa kompaniyaga tegishli bo'lsa — `404 Not Found`.

---

## `POST /api/users/invite` — taklif qilish

Body (`InviteUserRequest`):

```json
{
  "name": "Aziz Bekmurodov",
  "username": "aziz.b",
  "email": "aziz@uysot.uz",
  "roleId": 3
}
```

`username` login uchun ishlatiladi ([auth.md](auth.md#post-apiauthlogin--kirish)),
`email` esa aloqa maqsadida. Ikkalasi ham butun platformada unique.
Email yoki username allaqachon ro'yxatdan o'tgan bo'lsa — `409`.

`roleId` — [`GET /api/roles`](roles.md) dagi rollardan biri. Rol boshqa kompaniyaniki
bo'lsa yoki umuman bo'lmasa — `404 ROLE_NOT_FOUND`. `DEVELOPER` yoki `SUPERADMIN` tizim
rolini kompaniyaning o'z admini bera olmaydi — `403 ROLE_NOT_ASSIGNABLE` (buni faqat
`PLATFORM_ADMIN` huquqiga ega platforma xodimi qiladi).

Muvaffaqiyatda hisob `INVITED` holatida yaratiladi va bir martalik
aktivatsiya tokeni qaytadi:

```json
{
  "data": {
    "user": {
      "id": 5,
      "companyId": 1,
      "name": "Aziz Bekmurodov",
      "username": "aziz.b",
      "email": "aziz@uysot.uz",
      "roleId": 3,
      "roleCode": "OPERATOR",
      "roleName": "Operator",
      "status": "INVITED",
      "lastLoginAt": null,
      "createdAt": "2026-08-02T08:00:00Z"
    },
    "activationToken": "xY9...=="
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

**Muhim:** bu token faqat shu javobda ko'rinadi, keyin qayta olinmaydi. Admin uni foydalanuvchiga
yetkazadi; foydalanuvchi
[`POST /api/auth/activate`](auth.md#post-apiauthactivate--taklifni-faollashtirish)
orqali parol qo'yib faollashtiradi. Token 7 kundan keyin muddati tugaydi.

---

## `PUT /api/users/{id}/role` — rol o'zgartirish

Body (`UpdateUserRoleRequest`): `{ "roleId": 4 }`

Rol [`GET /api/roles`](roles.md) dagi rollardan biri bo'lishi kerak; `DEVELOPER` va
`SUPERADMIN` uchun `PLATFORM_ADMIN` huquqi talab qilinadi (`403 ROLE_NOT_ASSIGNABLE`).

**Guardrail:** `USER_EDIT` huquqiga ega yagona faol foydalanuvchini shu huquqsiz rolga
o'tkazib bo'lmaydi — `409 LAST_ADMIN_ROLE_CHANGE_FORBIDDEN` (aks holda kompaniya o'z
foydalanuvchilarini boshqara olmay qoladi).

**Yon ta'siri:** foydalanuvchining barcha sessiyalari bekor qilinadi — uning tokenidagi
eski permission kodlari kuchda qolmasligi uchun. U qayta login qiladi.

**Javob** — yangilangan `UserRow`.

---

## `PUT /api/users/{id}/block` / `PUT /api/users/{id}/unblock` — bloklash va blokdan chiqarish

Body yo'q. `BLOCKED` holatidagi hisob login qila olmaydi (`403`,
[auth.md](auth.md#post-apiauthlogin--kirish)ga qarang) va bloklanganda uning sessiyalari
ham bekor qilinadi. Ochiq taklif (invite) va parol tiklash havolalari ham o'chiriladi —
bloklangan foydalanuvchi ular orqali qaytib kira olmaydi.

**Guardrail'lar:**
o'zingizni bloklab bo'lmaydi, `USER_EDIT` huquqiga ega yagona faol foydalanuvchini ham
bloklab bo'lmaydi — ikkalasi ham `409`.

**Javob** — yangilangan `UserRow`.

---

## Platforma: istalgan kompaniya foydalanuvchilari — `/api/companies/{companyId}/…`

**Faqat `PLATFORM_ADMIN` (SUPERADMIN).** Yuqoridagi `/api/users` endpointlarining aynan o'zi, lekin kompaniya
tokendan emas, URL'dagi `{companyId}` dan olinadi. Kompaniya yo'q bo'lsa — `404 COMPANY_NOT_FOUND`.
Kompaniya `SUSPENDED`/`DELETED` bo'lsa ham ishlaydi.

| Endpoint | Izoh |
|---|---|
| `GET /api/companies/{companyId}/users` | `UserRow[]` |
| `GET /api/companies/{companyId}/users/{id}` | `UserRow` |
| `GET /api/companies/{companyId}/roles` | kompaniya rollari (`RoleRow[]`) — invite uchun `roleId` tanlash |
| `POST /api/companies/{companyId}/users/invite` | body — `InviteUserRequest`, javob — `InviteUserResponse` (aktivatsiya tokeni) |
| `PUT /api/companies/{companyId}/users/{id}/role` | body — `{ "roleId": 5 }` |
| `PUT /api/companies/{companyId}/users/{id}/block` · `.../unblock` | body yo'q |

**Yangi kompaniyaning birinchi admini:** `POST /api/companies` → `GET .../roles` (kodi `ADMIN` bo'lgan rolning `id`si)
→ `POST .../users/invite` → javobdagi tokenni egasiga berish (`POST /api/auth/activate`).

Qoidalar oddiy `/api/users` bilan bir xil: o'zingizning rolingizni o'zgartira/o'zingizni bloklay olmaysiz,
`USER_EDIT` li yagona faol foydalanuvchini bloklab yoki rolini tushirib bo'lmaydi (`409`). Farqi — platforma
`DEVELOPER`/`SUPERADMIN` rollarini ham bera oladi.
