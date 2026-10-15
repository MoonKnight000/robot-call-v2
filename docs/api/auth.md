# Autentifikatsiya (login) API

`uz.murodjon.robotcallv2.auth` · huquq: aralash (pastga qarang) · ROADMAP E.1

Panel uchun real foydalanuvchi login — API'ga kirishning yagona yo'li:
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

Ikki bosqichli kirish yoqilgan akkauntda ([profile.md](profile.md#2fa)) body'da `otpCode` ham
bo'ladi — autentifikator ilovasidagi joriy 6 xonali kod:

```json
{ "username": "admin", "password": "secret123", "otpCode": "287082" }
```

Parol to'g'ri, lekin `otpCode` yo'q bo'lsa — `400 TOTP_REQUIRED` (frontend kod maydonini
ko'rsatib, so'rovni qayta yuboradi; bu muvaffaqiyatsiz urinish hisoblanmaydi). Kod noto'g'ri,
muddati o'tgan yoki allaqachon ishlatilgan bo'lsa — `400 TOTP_CODE_INVALID`, va bu
muvaffaqiyatsiz urinish sifatida lockout hisobiga qo'shiladi (audit `LOGIN_FAILED`, `BAD_TOTP`).

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
      "roleId": 2,
      "roleCode": "ADMIN",
      "roleName": "Administrator",
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

**Refresh token cookie'da ham keladi.** Javob bilan birga

```
Set-Cookie: refresh_token=<refreshToken>; Path=/api/auth; Max-Age=<30 kun, soniyada>; Expires=...; Secure; HttpOnly; SameSite=Strict
```

sarlavhasi keladi — `activate`, `reset-password` va `refresh` javoblarida ham. Brauzer uni o'zi
saqlaydi va faqat `/api/auth/*` so'rovlariga (refresh, logout) yuboradi; JavaScript uni o'qiy
olmaydi, shuning uchun XSS orqali o'g'irlab bo'lmaydi. Boshqa origin'dagi frontend
`fetch(..., { credentials: 'include' })` bilan chaqiradi (CORS `allowCredentials` yoqilgan).
Body'dagi `refreshToken` maydoni hozircha qaytadi, lekin **deprecated** — frontend uni
localStorage'da saqlashni to'xtatib, cookie'ga o'tishi kerak; keyingi versiyalardan birida body'dan
olib tashlanadi. `Secure` doim qo'yiladi (brauzerlar `http://localhost`ni xavfsiz deb hisoblaydi,
lokal ishlab chiqish buzilmaydi).

Har bir keyingi so'rovda `Authorization: Bearer <accessToken>` sarlavhasi
bilan yuboriladi. Login o'tmagan **har qanday** holat — username yo'q, parol
noto'g'ri, hisob `BLOCKED` yoki hali `activate` qilinmagan (`INVITED`) — bitta
javob: `400 LOGIN_INVALID_CREDENTIALS`. Sabab faqat audit jurnaliga yoziladi
(`LOGIN_FAILED`), javobdan hisob mavjudligini yoki holatini bilib bo'lmaydi.
Parol (va 2FA) to'g'ri, lekin kompaniya to'xtatilgan (`SUSPENDED`) bo'lsa —
`403 COMPANY_SUSPENDED` (bu faqat parolni biladigan odamga ko'rinadi).

**Brute-force himoyasi.** Bitta IP dan daqiqasiga 5 ta urinish
(`429 RATE_LIMITED`, `Retry-After` sarlavhasi bilan); bitta username bo'yicha **bitta IP
dan** 15 daqiqa ichida 10 ta muvaffaqiyatsiz urinish → shu username **shu IP uchun** 15
daqiqaga qulflanadi: `429 LOGIN_TEMPORARILY_LOCKED`, parol tekshirilmaydi ham (audit
`LOGIN_LOCKED`, tafsilotida IP). Boshqa IP dan (masalan, hisob egasining o'zidan) kirish
ishlashda davom etadi — username'ni bilgan begona odam hisobni hamma uchun qulflab qo'ya
olmaydi. IP per-IP limit bilan bir xil olinadi: `RATE_LIMIT_TRUST_FORWARDED_FOR=true` bo'lsa
`X-Forwarded-For`ning birinchi qiymati, aks holda TCP manzil. Muvaffaqiyatli login shu
username+IP hisoblagichini nolga tushiradi. Chegaralar `config/auth.yml` `rate-limit` blokida.

`accessToken` **60 daqiqa** yashaydi (`JWT_EXPIRY_MINUTES`), lekin logout, parol
almashtirish/tiklash, rol o'zgarishi yoki bloklash uni muddatidan oldin bekor
qiladi — shundan keyin `401`. Sarlavha qo'ya olmaydigan oqimlar (`EventSource`, `<audio>`)
uchun URL'ga **bir martalik ticket** qo'yiladi —
[`POST /api/auth/stream-ticket`](#post-apiauthstream-ticket--oqim-uchun-bir-martalik-ticket).
Eskicha `?token=` / `?access_token=` (to'liq access token URL'da) hali qabul qilinadi, lekin
**deprecated**. Ikkalasi ham faqat SSE oqimlari va jonli audio (`/live/stream`, `/me/events`,
`/listen`, faqat `GET`) uchun; boshqa yo'llarda e'tiborsiz qoldiriladi.

`refreshToken` — uzoq muddatli (30 kun), serverda bekor qilinadigan (hash
`user_session.refresh_token_hash`da saqlanadi — bitta foydalanuvchi bir
vaqtda bir nechta qurilmadan kirsa, har biri o'z sessiya qatoriga ega
bo'ladi, ROADMAP E.1 follow-up, [profile.md](profile.md#get-apiprofilesessions--faol-sessiyalar)
"faol sessiyalar" shu jadvaldan o'qiydi). `accessToken` muddati tugaganda,
qaytadan parol so'ramasdan
[`POST /api/auth/refresh`](#post-apiauthrefresh--tokenni-yangilash) orqali
yangi juftlik olinadi.

---

## `POST /api/auth/signup` — ro'yxatdan o'tish (bepul sinov)

Rol: hech biri (ochiq). Yangi kompaniya, uning birinchi administratori (`ADMIN` roli) va
sinov balansini yaratadi, so'ng administrator email'iga faollashtirish havolasini yuboradi.
Parol shu yerda **berilmaydi** — havola orqali
[`POST /api/auth/activate`](#post-apiauthactivate--taklifni-faollashtirish) da o'rnatiladi,
bu email haqiqiyligini ham tasdiqlaydi.

Platformada yoqilgan bo'lishi kerak: `SIGNUP_ENABLED=true` (standart — o'chiq).

Body (`SignupRequest`):

```json
{
  "companyName": "Mening kompaniyam",
  "name": "Ali Valiyev",
  "username": "ali",
  "email": "ali@example.com"
}
```

**Javob** (`SignupResponse`):

```json
{ "companyId": 42, "email": "ali@example.com", "activationExpiresAt": "2026-10-21T09:00:00Z" }
```

Email'dagi havola: `SIGNUP_ACTIVATION_URL?token=<token>` (standart
`http://localhost:5173/activate`). Frontend sahifasi `token`ni olib, parol bilan
`/api/auth/activate` ga yuboradi — keyingi oqim oddiy taklif bilan bir xil. Havola 7 kun amal qiladi.

Yangi kompaniya balansiga `voice-agent.billing.trial.credit-uzs` (standart **20 000 so'm**) qo'shiladi —
  holatini [`GET /api/billing/trial`](billing.md#10-get-apibillingtrial--bepul-sinov) ko'rsatadi.

Hammasi bitta tranzaksiyada: email yuborilmasa hech narsa saqlanmaydi va xuddi shu email bilan
qayta urinish mumkin.

| Status | `messageCode` | Qachon |
|---|---|---|
| `400` | `VALIDATION_FAILED` | Maydon bo'sh, email noto'g'ri, `username` 3–64 belgidan tashqarida |
| `403` | `SIGNUP_DISABLED` | Platformada ro'yxatdan o'tish o'chirilgan |
| `409` | `USER_EMAIL_TAKEN` | Bu email allaqachon ro'yxatdan o'tgan (bitta email — bitta sinov) |
| `409` | `USER_USERNAME_TAKEN` | `username` band |
| `502` | `SIGNUP_MAIL_FAILED` | SMTP xatni yubora olmadi; hech narsa yaratilmadi |

---

## `POST /api/auth/activate` — taklifni faollashtirish

Rol: hech biri (ochiq). `POST /api/users/invite` qaytargan (yoki
[`/api/auth/signup`](#post-apiauthsignup--royxatdan-otish-bepul-sinov) email'da yuborgan)
`activationToken`ni parol bilan almashtiradi va avtomatik login qiladi.

Body (`ActivateRequest`):

```json
{ "token": "<invite-dan-kelgan-token>", "password": "kamida10belgi" }
```

Parol siyosati (activate, reset va profil parol almashtirish uchun bir xil):
**10–128 belgi, kamida bitta harf va bitta raqam** — aks holda `400 VALIDATION_FAILED`.

**Javob** — `POST /api/auth/login` bilan bir xil (`LoginResponse`).

Token noto'g'ri, allaqachon ishlatilgan yoki muddati o'tgan (7 kun) bo'lsa —
`400`. Taklif faqat `INVITED` holatidagi hisobni faollashtiradi — bloklangan yoki allaqachon faol
hisob uchun `400 ACTIVATION_TOKEN_INVALID` (ilgari eski taklif havolasi bloklangan hisobni ham
qayta faollashtirardi).

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

> ℹ️ Ikki bosqichli kirish yoqilgan akkauntda body'da `otpCode` ham bo'lishi shart, aks holda
> parol o'zgarmaydi: `400 TOTP_REQUIRED` yoki `400 TOTP_CODE_INVALID`. Tiklash havolasining
> o'zi (pochta qutisiga kirgan kishi uchun) akkauntga kirish yo'li bo'lmasin.

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
(1 soat) bo'lsa — `400`. Faqat `ACTIVE` hisob uchun ishlaydi: bloklangan hisob tiklash havolasi
bilan kira olmaydi (`400 RESET_TOKEN_INVALID`).

---

## `POST /api/auth/refresh` — tokenni yangilash

Rol: hech biri (ochiq) — bu chaqiriq paytida `accessToken` allaqachon
muddati o'tgan bo'lishi mumkin, shuning uchun `Authorization` header talab
qilinmaydi.

Body (`RefreshTokenRequest`) — **ixtiyoriy**:

```json
{ "refreshToken": "xY9-base64url..." }
```

Token avval body'dan olinadi (deprecated, eski klientlar uchun); body yo'q yoki `refreshToken`
bo'sh bo'lsa — `refresh_token` cookie'dan. Yangi frontend body yubormaydi:

```ts
fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' });
```

Ikkala joyda ham token bo'lmasa — `400 REFRESH_TOKEN_MISSING`.

**Javob** — `POST /api/auth/login` bilan bir xil (`LoginResponse` + yangi `refresh_token`
cookie) — yangi `accessToken` **va** yangi `refreshToken` (eskisi darhol bekor qilinadi —
rotatsiya, qayta ishlatib bo'lmaydi). `refreshToken` noto'g'ri yoki muddati
o'tgan bo'lsa, yoki hisob endi `ACTIVE` bo'lmasa — `403`. Kompaniya to'xtatilgan
(`SUSPENDED`) bo'lsa — `403 COMPANY_SUSPENDED`.

**Qayta ishlatish aniqlanadi:** allaqachon rotatsiya qilingan `refreshToken`
yana kelsa — u ikki qo'lda bo'lgani aniq (o'g'irlangan), shuning uchun
foydalanuvchining **barcha** sessiyalari va access tokenlari bekor qilinadi,
javob `403 REFRESH_TOKEN_REUSED`, audit `REFRESH_REUSE_DETECTED`. Bitta IP dan
daqiqasiga 30 tadan ortiq refresh — `429 RATE_LIMITED`.

---

## `POST /api/auth/logout` — chiqish

Rol: istalgan (kirgan bo'lsa yetarli). So'rov qaysi qurilmani chaqirayotganini
aytmaydi, shuning uchun joriy foydalanuvchining **barcha** qurilmalaridagi
sessiyalarini serverda bekor qiladi (`200`) — bitta qurilmani saqlab
qolib boshqasini chiqarish kerak bo'lsa, o'sha maqsad uchun
[`DELETE /api/profile/sessions/{id}`](profile.md#delete-apiprofilesessionsid--sessiyani-tugatish)
ishlatiladi. Shu bilan birga foydalanuvchining shu paytgacha berilgan **barcha
`accessToken`lari ham** bekor qilinadi (Redis'dagi "shu vaqtdan oldin berilgan
tokenlar yaroqsiz" belgisi) — keyingi so'rov `401` oladi; klient tokenni o'zi
ham tashlab yuboradi (localStorage/memory). Javob `refresh_token` cookie'sini ham o'chiradi
(`Set-Cookie: refresh_token=; Path=/api/auth; Max-Age=0; ...`).

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
    "roleId": 2,
    "roleCode": "ADMIN",
    "roleName": "Administrator",
    "permissions": ["DASHBOARD_READ", "CAMPAIGN_READ", "CAMPAIGN_EDIT", "USER_READ", "USER_EDIT", "ROLE_READ", "ROLE_EDIT"],
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

`permissions` — foydalanuvchi rolidagi barcha huquqlarning to'liq enum nomlari. Frontend
qaysi sahifa va tugmani ko'rsatishni shu ro'yxatga qarab hal qiladi; nomlarni o'zi tarjima
qiladi (`ErrorCode` bilan bir xil konventsiya). To'liq model: [roles.md](roles.md).

Access tokenning o'zida esa permissionlar **qisqa kod** bilan yuriladi — `perms` claim,
masalan `"cmp.r,cmp.w,usr.r"`; yana `roleId` va `role` (rol kodi) claimlari bor. Token
12 soat yashagani uchun rol tahrirlanganda yoki foydalanuvchining roli almashtirilganda
uning sessiyalari bekor qilinadi va u qayta login qiladi.

---

## `POST /api/auth/stream-ticket` — oqim uchun bir martalik ticket

Rol: istalgan (kirgan foydalanuvchi, `Authorization: Bearer`). API kalit bilan —
`403 NO_USER_SESSION` (kalit oqimga `X-Api-Key` sarlavhasi bilan ulanadi).

Body yo'q. **Javob** (`StreamTicketResponse`):

```json
{
  "data": {
    "ticket": "q3V1Xk...base64url",
    "expiresAt": "2026-09-14T10:01:00Z"
  },
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

`ticket` — tasodifiy 256-bit qiymat, **60 soniya** yashaydi va **faqat bir marta** ishlatiladi.
U so'ragan foydalanuvchiga, uning kompaniyasi va joriy huquqlariga bog'langan. Oqim URL'iga
`?ticket=` bilan qo'yiladi:

```ts
const { ticket } = await requestJson('/api/auth/stream-ticket', {});
const source = new EventSource(`/api/live/stream?ticket=${encodeURIComponent(ticket)}`);
```

Qabul qilinadigan joylar (faqat `GET`): `/api/live/stream`, `/api/call-queues/live/stream`,
`/api/operators/me/events`, `/api/calls/{channelId}/listen`. Ticket allaqachon ishlatilgan,
muddati o'tgan yoki noma'lum bo'lsa, foydalanuvchi shu orada bekor qilingan bo'lsa (logout,
bloklash, rol o'zgarishi) yoki Redis ishlamayotgan bo'lsa — so'rov autentifikatsiyasiz qoladi:
`401`. Shuning uchun **har bir (qayta) ulanishdan oldin yangi ticket olinadi**: `EventSource`ning
o'zi qayta ulanganda eski URL `401` oladi — `onerror`da yangi ticket bilan yangi `EventSource`
ochiladi. Ochilgan oqim ticket muddati tugashi bilan uzilmaydi — ticket faqat ulanish paytida
tekshiriladi.

Ticket berish uchun Redis kerak: u ishlamasa — `502 STREAM_TICKET_STORE_UNAVAILABLE`.

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
