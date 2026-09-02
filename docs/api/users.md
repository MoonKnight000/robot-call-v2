# Foydalanuvchilar API

`uz.murodjon.robotcallv2.user` · rol: **ADMIN** (barcha endpoint) · ROADMAP E.1,
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
      "role": "OPERATOR",
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
    "role": "OPERATOR",
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
  "role": "OPERATOR"
}
```

`username` login uchun ishlatiladi ([auth.md](auth.md#post-apiauthlogin--kirish)),
`email` esa aloqa maqsadida. Ikkalasi ham butun platformada unique.
Email yoki username allaqachon ro'yxatdan o'tgan bo'lsa — `409`. `role`
sifatida `SUPERADMIN` yuborilsa — `400` (bu platforma xodimi
roli, kompaniyaning o'z ADMINi orqali berilmaydi).
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
      "role": "OPERATOR",
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

Body (`UpdateUserRoleRequest`): `{ "role": "VIEWER" }`

Rollar: `ADMIN` > `OPERATOR` > `VIEWER` (SecurityConfig'dagi authority ierarxiyasi).
**Guardrail:** kompaniyaning yagona faol ADMIN'ini boshqa rolga o'tkazib bo'lmaydi — `409` (avval boshqa birortasini
ADMIN qiling). `role: "SUPERADMIN"` — `400`.

**Javob** — yangilangan `UserRow`.

---

## `PUT /api/users/{id}/block` / `PUT /api/users/{id}/unblock` — bloklash va blokdan chiqarish

Body yo'q. `BLOCKED` holatidagi hisob login qila olmaydi (`403`,
[auth.md](auth.md#post-apiauthlogin--kirish)ga qarang).

**Guardrail'lar:**
o'zingizni bloklab bo'lmaydi, kompaniyaning yagona faol ADMIN'ini ham
bloklab bo'lmaydi — ikkalasi ham `409`.

**Javob** — yangilangan `UserRow`.
