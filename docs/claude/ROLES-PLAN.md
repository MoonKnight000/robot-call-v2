# Foydalanuvchi va rollar boshqaruvi — reja (2026-11-06)

## Maqsad

1. Kompaniya admini xodimni qo'sha oladi (taklif), tahrirlay oladi (ism, login, email), bloklay oladi.
2. **Har bir kompaniyaning rollari o'ziniki** — hammasi tahrirlanadi, permission qo'shiladi/olinadi.
3. **Standart rollarni platforma admini boshqaradi** (qo'shadi/o'chiradi/tahrirlaydi). Yangi kompaniya
   yaratilganda shu standart rollar kompaniyaga **nusxalanadi**; keyin kompaniya ularni o'zicha o'zgartiradi.

## Hozirgi holat (2026-11-06)

- Bor: `POST /api/users/invite`, `PUT /api/users/{id}/role`, `PUT /api/users/{id}/block|unblock`;
  kompaniyaning o'z rollari uchun `POST/PUT/DELETE /api/roles` (permission to'liq almashtiriladi).
- Yo'q: foydalanuvchining ism/login/emailini tahrirlash.
- Tizim rollari (`SystemRole`: DEVELOPER, ADMIN, OPERATOR, VIEWER, SUPERVISOR) har kompaniyaga
  `RoleBootstrap` orqali `app_role` qatori bo'lib seed qilinadi, lekin permissionlari **kodda hisoblanadi**
  (`Role` compact konstruktori), DB da saqlanmaydi → tahrirlab bo'lmaydi (`ROLE_SYSTEM_READONLY`).
- Kodda rol `code` bo'yicha bog'langan joylar: `UserService.inviteFirstAdmin` (`ADMIN`),
  `RoleValidator.validateAssignable` (DEVELOPER — faqat platforma beradi), frontendda `user.role === "ADMIN"`.

## Bosqichlar

### 1. Foydalanuvchini tahrirlash (backend + frontend) — ✅ kodlandi 2026-11-06, tekshirilmagan
- `PUT /api/users/{id}` va `PUT /api/companies/{companyId}/users/{id}` — body `{ name, username, email }`
  (`USER_EDIT` / `PLATFORM_ADMIN`). Email/login band bo'lsa — `409 USER_EMAIL_TAKEN` / `USER_USERNAME_TAKEN`.
  Sessiya bekor qilinmaydi: token `sub` = userId, login emas.
- Frontend: xodim qatorida aniq "Tahrirlash" tugmasi + modal; menyu (rol, bloklash) saqlanadi.
- Tekshirish: xodimning ismi/emaili/loginini o'zgartirish → ro'yxatda yangilanadi; band email → 409 xabari;
  yangi login bilan kirish ishlaydi.

### 2. Standart rol shablonlari (backend) — ✅ kodlandi 2026-11-06, tekshirilmagan
- Yangi jadval `role_template` (`id, code NULL UNIQUE, name UNIQUE, description, assignable_by_company,
  created_at`) + `role_template_permission`. Flyway: jadval; boshlang'ich qatorlar Java bootstrap'da
  `SystemRole.resolvePermissions()` dan bir marta yoziladi (bo'sh bo'lsa).
- Platforma API: `GET/POST/PUT/DELETE /api/platform/role-templates` (`PLATFORM_ADMIN`).
- Himoya: `code = ADMIN` shablonini o'chirib bo'lmaydi va u `USER_EDIT` + `ROLE_EDIT` ni saqlashi shart
  (`inviteFirstAdmin` va yangi kompaniyaning birinchi admini shunga tayanadi).
- Shablon o'zgarishi **faqat yangi kompaniyalarga** ta'sir qiladi (mavjudlariga emas).
- Tekshirish: platforma admini shablon qo'shadi/permission o'zgartiradi → `GET` da ko'rinadi; ADMIN ni o'chirish → 409.

### 3. Kompaniya rollari to'liq tahrirlanadigan bo'ladi (backend) — ✅ kodlandi 2026-11-06, tekshirilmagan
- Startup'da bir martalik: `is_system = true` qatorlar uchun hozirgi `SystemRole` permissionlarini
  `app_role_permission` ga yozib qo'yish (idempotent). Shundan keyin permission doim DB dan o'qiladi;
  `Role` compact konstruktoridagi hisoblash olib tashlanadi. `code` qoladi (qaysi shablondan kelgani).
- `CompanyCreatedEvent` → shablonlar kompaniyaga nusxalanadi (`createSystemRoles` o'rniga).
- `ROLE_SYSTEM_READONLY` o'rniga: barcha rol tahrirlanadi/o'chiriladi, **istisno** — `assignable_by_company = false`
  rol (DEVELOPER) faqat platforma admini tomonidan.
- Yangi guardrail: rolni tahrirlash/o'chirish natijasida kompaniyada `USER_EDIT` ga ega faol foydalanuvchi qolmasa —
  `409` (`LAST_ADMIN_ROLE_CHANGE_FORBIDDEN` bilan bir xil mantiq).
- `MAX_CUSTOM_ROLES` → kompaniyadagi jami rollar limiti (masalan 15).
- **Trade-off:** kelajakda qo'shiladigan yangi permission mavjud rollarga avtomatik tushmaydi — platforma admini
  shablonga qo'shadi (yangi kompaniyalar uchun), mavjud kompaniyalarda admin o'zi qo'shadi.
- `docs/api/roles.md` qayta yoziladi.

### 4. Frontend — ✅ kodlandi 2026-11-06, tekshirilmagan
- Platforma: "Standart rollar" sahifasi (shablon CRUD, permission checkbox — `RoleEditModal` qayta ishlatiladi).
- Kompaniya: Sozlamalar → Rollar — barcha rollar tahrirlanadi; "Tizim roli" belgisi → "Standart" (code bor bo'lsa).

#### 3-bosqich eslatmalari (2026-11-06)
- `V56`: `app_role.assignable_by_company` (DEVELOPER = false). `is_system` ustuni vaqtincha qoladi — faqat
  `RoleBootstrap.materializeLegacySystemRoles` uni o'qiydi; keyingi relizda olib tashlash mumkin.
- `RoleRow.system` endi "shablondan nusxalangan" (`code != null`) degani; yangi maydon `assignableByCompany`.
- Yangi xato kodlari: `ROLE_PLATFORM_MANAGED` (403), `ROLE_EDIT_LOCKS_OUT_ADMINS` (409); `ROLE_SYSTEM_READONLY` olib tashlandi.
  Limit: jami 15 rol (`MAX_ROLES`).
- Kichik oyna: web server `ApplicationReadyEvent` dan oldin ochiladi — materializatsiya tugaguncha (bir necha ms)
  eski "system" rolni o'qigan so'rov uni bo'sh permission bilan ko'radi. Faqat birinchi ishga tushishda.
