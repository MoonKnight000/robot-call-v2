# Foydalanuvchilar API

`uz.murodjon.uysotvoice.user` · rol: **ADMIN** (barcha endpoint) · ROADMAP E.1,
UI-DESIGN §10.12

Login/sessiya endpointlari uchun [auth.md](auth.md)ga qarang — bu fayl faqat
hisob boshqaruvi (invite/rol/block).

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## `GET /api/users` — ro'yxat

Parametrsiz (3 tadan kam parametr qoidasi — pagination hozircha kerak emas,
kompaniyadagi foydalanuvchilar soni kichik). Joriy kompaniya bo'yicha,
`id` tartibida.

**Javob qatori** (`User`):

```json
{
  "id": 2, "companyId": 1, "name": "Aziz Bekmurodov", "username": "aziz.b",
  "email": "aziz@uysot.uz", "role": "OPERATOR", "status": "ACTIVE",
  "lastLoginAt": "2026-08-02T07:00:00Z", "createdAt": "2026-07-15T00:00:00Z"
}
```

`passwordHash` va `refreshTokenHash` hech qachon javobda chiqmaydi.

---

## `POST /api/users/invite` — taklif qilish

Body (`InviteUserRequest`):

```json
{ "name": "Aziz Bekmurodov", "username": "aziz.b", "email": "aziz@uysot.uz", "role": "OPERATOR" }
```

`username` login uchun ishlatiladi ([auth.md](auth.md#post-apiauthlogin--kirish)),
`email` esa faqat aloqa maqsadida. Ikkalasi ham butun platformada unique.
Email yoki username allaqachon ro'yxatdan o'tgan bo'lsa — `409`. `role`
sifatida `SUPERADMIN` yuborilsa — `400` (report #3): bu platforma xodimi
roli, kompaniyaning o'z ADMINi orqali hech qachon berilmaydi.
Muvaffaqiyatda hisob `INVITED` holatida yaratiladi va bir martalik
aktivatsiya tokeni qaytadi:

```json
{
  "user": { "id": 5, "status": "INVITED", ... },
  "activationToken": "xY9...=="
}
```

**Muhim:** email yuborish infratuzilmasi yo'q (SMTP sozlanmagan) — bu token
faqat shu javobda ko'rinadi, keyin qayta olinmaydi. Admin uni foydalanuvchiga
qo'lda (chat, telefon) yetkazadi; foydalanuvchi
[`POST /api/auth/activate`](auth.md#post-apiauthactivate--taklifni-faollashtirish)
orqali parol qo'yib faollashtiradi. Token 7 kundan keyin muddati tugaydi.

---

## `PUT /api/users/{id}/role` — rol o'zgartirish

Body (`UpdateUserRoleRequest`): `{ "role": "VIEWER" }`

Rollar: `ADMIN` > `OPERATOR` > `VIEWER` (config/SecurityConfig'dagi
authority ierarxiyasiga qarang). **Guardrail:** kompaniyaning yagona faol
ADMIN'ini boshqa rolga o'tkazib bo'lmaydi — `409` (avval boshqa birortasini
ADMIN qiling). `role: "SUPERADMIN"` — `400`, xuddi `POST /api/users/invite`
kabi (report #3).

---

## `POST /api/users/{id}/block` / `POST /api/users/{id}/unblock`

Body yo'q. `BLOCKED` holatidagi hisob login qila olmaydi (`403`,
[auth.md](auth.md#post-apiauthlogin--kirish)ga qarang). **Guardrail'lar:**
o'zingizni bloklab bo'lmaydi, kompaniyaning yagona faol ADMIN'ini ham
bloklab bo'lmaydi — ikkalasi ham `409`.
