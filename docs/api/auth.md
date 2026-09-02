# Autentifikatsiya (login) API

`uz.murodjon.robotcallv2.auth` · rol: aralash (pastga qarang) · ROADMAP E.1

Panel uchun real foydalanuvchi login — `X-Api-Key` (machine-to-machine)ga
qo'shimcha, uni almashtirmaydi. Ikkalasi ham bir vaqtda ishlaydi:
[README.md §1](README.md#1-bazaviy-url-va-autentifikatsiya)ga qarang.

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## `POST /api/auth/login` — kirish

Rol: hech biri (ochiq).

Body (`LoginRequest`):

```json
{ "username": "admin", "password": "secret123" }
```

**Javob** (`LoginResponse`):

```json
{
  "data": {
    "accessToken": "eyJhbGciOi...",
    "accessTokenExpiresAt": "2026-08-03T08:00:00Z",
    "refreshToken": "xY9-base64url...",
    "refreshTokenExpiresAt": "2026-09-01T08:00:00Z",
    "user": {
      "id": 1,
      "companyId": 1,
      "name": "Admin",
      "username": "admin",
      "email": "admin@example.com",
      "role": "ADMIN",
      "status": "ACTIVE",
      "lastLoginAt": "2026-08-02T08:00:00Z",
      "createdAt": "2026-07-01T00:00:00Z"
    }
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

Har bir keyingi so'rovda `Authorization: Bearer <accessToken>` sarlavhasi
bilan yuboriladi. Hisob `BLOCKED` bo'lsa — `403`; hali `activate` qilinmagan
(`INVITED`) bo'lsa — `403`; username/parol mos kelmasa — `400` (qaysi
biri xato ekani aytilmaydi — hisob mavjudligini oshkor qilmaslik uchun).

`refreshToken` — uzoq muddatli (30 kun), serverda bekor qilinadigan (hash
`user_session.refresh_token_hash`da saqlanadi — bitta foydalanuvchi bir
vaqtda bir nechta qurilmadan kirsa, har biri o'z sessiya qatoriga ega
bo'ladi, ROADMAP E.1 follow-up, [profile.md](profile.md#get-apiprofilesessions--faol-sessiyalar)
"faol sessiyalar" shu jadvaldan o'qiydi). `accessToken` muddati tugaganda,
qaytadan parol so'ramasdan
[`POST /api/auth/refresh`](#post-apiauthrefresh--tokenni-yangilash) orqali
yangi juftlik olinadi.

---

## `POST /api/auth/activate` — taklifni faollashtirish

Rol: hech biri (ochiq). `POST /api/users/invite` qaytargan
`activationToken`ni parol bilan almashtiradi va avtomatik login qiladi.

Body (`ActivateRequest`):

```json
{ "token": "<invite-dan-kelgan-token>", "password": "kamida-8-belgi" }
```

**Javob** — `POST /api/auth/login` bilan bir xil (`LoginResponse`).

Token noto'g'ri, allaqachon ishlatilgan yoki muddati o'tgan (7 kun) bo'lsa —
`400`.

---

## `POST /api/auth/forgot-password` — parolni tiklashni so'rash

Rol: hech biri (ochiq). Login sahifasidagi "Parolni unutdingizmi?".

Body (`ForgotPasswordRequest`):

```json
{ "email": "aziz@uysot.uz" }
```

**Javob** — har doim `200` (`ResponseData<Void>`), `email` ro'yxatda
bor-yo'qligidan qat'i nazar — hisob mavjudligini oshkor qilmaslik uchun.
`email` mavjud va hisob `ACTIVE` bo'lsa (ya'ni `INVITED`/`BLOCKED` emas):
bir martalik tiklash tokeni generatsiya qilinadi (1 soat amal qiladi) va
`spring.mail.*` orqali (`voice-agent.security.password-reset.from`
jo'natuvchi) shu emailga yuboriladi. SMTP sozlanmagan yoki jo'natish
muvaffaqiyatsiz bo'lsa — serverda jim tarzda loglanadi, javob baribir
`200`.

---

## `POST /api/auth/reset-password` — parolni tiklash

Rol: hech biri (ochiq). Yuqoridagi endpoint yuborgan tokenni yangi parol
bilan almashtiradi.

Body (`ResetPasswordRequest`):

```json
{ "token": "<emaildan-kelgan-token>", "newPassword": "kamida-8-belgi" }
```

**Javob** — `POST /api/auth/login` bilan bir xil (`LoginResponse`) —
`POST /api/auth/activate` kabi, muvaffaqiyatli tiklashdan keyin avtomatik
login qiladi. Boshqa qurilmalardagi mavjud sessiyalar bekor qilinmaydi —
`PUT /api/profile/password` bilan bir xil konventsiya
([profile.md](profile.md#put-apiprofilepassword--xavfsizlik-tab-parol-almashtirish)ga
qarang). Token noto'g'ri, allaqachon ishlatilgan yoki muddati o'tgan
(1 soat) bo'lsa — `400`.

---

## `POST /api/auth/refresh` — tokenni yangilash

Rol: hech biri (ochiq) — bu chaqiriq paytida `accessToken` allaqachon
muddati o'tgan bo'lishi mumkin, shuning uchun `Authorization` header talab
qilinmaydi.

Body (`RefreshTokenRequest`):

```json
{ "refreshToken": "xY9-base64url..." }
```

**Javob** — `POST /api/auth/login` bilan bir xil (`LoginResponse`) — yangi
`accessToken` **va** yangi `refreshToken` (eskisi darhol bekor qilinadi —
rotatsiya, qayta ishlatib bo'lmaydi). `refreshToken` noto'g'ri, allaqachon
ishlatilgan yoki muddati o'tgan bo'lsa, yoki hisob endi `ACTIVE` bo'lmasa —
`403`.

---

## `POST /api/auth/logout` — chiqish

Rol: istalgan (kirgan bo'lsa yetarli). So'rov qaysi qurilmani chaqirayotganini
aytmaydi, shuning uchun joriy foydalanuvchining **barcha** qurilmalaridagi
sessiyalarini serverda bekor qiladi (`200`) — bitta qurilmani saqlab
qolib boshqasini chiqarish kerak bo'lsa, o'sha maqsad uchun
[`DELETE /api/profile/sessions/{id}`](profile.md#delete-apiprofilesessionsid--sessiyani-tugatish)
ishlatiladi. `accessToken` o'zi hali muddati tugagunga qadar amal qiladi
(stateless JWT, serverda alohida bekor qilinmaydi); klient uni ham o'zi
tashlab yuboradi (localStorage/memory).

---

## `GET /api/auth/me` — joriy foydalanuvchi

Rol: istalgan (kirgan bo'lsa yetarli).

**Javob** (`CurrentUserResponse`):

```json
{
  "data": {
    "id": 1,
    "name": "Admin",
    "username": "admin",
    "email": "admin@example.com",
    "role": "ADMIN",
    "companyId": 1,
    "companyName": "Default"
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

Sidebar xodim kartochkasi, hover-popover va topbar shu javobga qarab
chiziladi (UI-DESIGN §7–9).

---

## `POST /api/auth/uysot/callback` — Uysot OAuth (stub)

Rol: ochiq. Hozircha har doim `502` (`ExternalServiceException`) qaytaradi —
Uysot OAuth kredensiallari hali sozlanmagan (ROADMAP Bosqich D, tashqi
bog'liqlik). Endpoint shakli UI'ning "Uysot bilan kirish" tugmasi
chaqirishi uchun oldindan qo'yilgan.

---

## `GET /api/companies` — kompaniya tanlagich

Rol: istalgan (kirgan bo'lsa yetarli). Joriy foydalanuvchining kompaniyasini
qaytaradi — MVP'da har doim bitta elementli ro'yxat (bitta foydalanuvchi =
bitta kompaniya). Ko'p kompaniyaga a'zolik ROADMAP E.2'da kengaytiriladi.

```json
{
  "data": [
    {
      "id": 1,
      "name": "Default",
      "status": "ACTIVE",
      "createdAt": "2026-07-01T00:00:00Z",
      "logoFileId": null,
      "address": null
    }
  ],
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

Sidebar kompaniya tanlagichi (UI-DESIGN §7.3) ro'yxat 1 tadan ko'p bo'lganda
ko'rinadi.
