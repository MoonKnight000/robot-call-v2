# Rollar va huquqlar API

`uz.murodjon.robotcallv2.role` · huquq: **ROLE_READ** (o'qish) / **ROLE_EDIT** (yozish)

Umumiy javob shakli, xatolar va pagination konventsiyasi uchun
[README.md](README.md)ga qarang.

---

## 1. Model: permission, rol, token

**Permission** — har bir panel sahifasi uchun ikkita huquq:

- `<SAHIFA>_READ` — sahifani ochish, ro'yxat va tafsilotni ko'rish;
- `<SAHIFA>_EDIT` — qo'shish, tahrirlash, o'chirish, ishga tushirish.

Backendda har bir endpoint aynan bitta permissionga bog'langan
(`@PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")`), URL naqshiga emas. Huquq yetmasa —
`403` va `messageCode: "PERMISSION_DENIED"`.

**Rol** — bitta kompaniya ichidagi permissionlar to'plami. Ikki xili bor:

| Tur | `code` | `system` | Permissionlari |
|---|---|---|---|
| Tizim roli | `DEVELOPER`, `ADMIN`, `OPERATOR`, `VIEWER`, `SUPERADMIN` | `true` | Kodda hisoblanadi (`SystemRole.java`), DB da saqlanmaydi — shuning uchun yangi permission qo'shilsa avtomatik qamrab oladi |
| Kompaniyaning o'z roli | `null` | `false` | `app_role_permission` jadvalida saqlanadi, admin o'zi tanlaydi |

Tizim rollari har bir kompaniyaga avtomatik seed qilinadi va **tahrirlab ham, o'chirib ham
bo'lmaydi** (`409 ROLE_SYSTEM_READONLY`). Kompaniya ularning ustiga **eng ko'pi 10 ta** o'z
rolini yaratadi (`409 ROLE_LIMIT_EXCEEDED`).

**Tizim rollari nimani beradi:**

| Rol | Qamrovi |
|---|---|
| `DEVELOPER` | Kompaniyadagi **barcha** permissionlar, doimiy — kelajakda qo'shiladigan yangi permissionlar ham. Faqat platforma xodimi bera oladi |
| `ADMIN` | `ENGINE_EDIT` dan tashqari hammasi (speech engine sozlamasi noto'g'ri bo'lsa butun kompaniya qo'ng'irog'i buziladi — u DEVELOPER da qoladi) |
| `OPERATOR` | Kundalik ish: kampaniya, qo'ng'iroq, jonli monitoring, kontakt, DNC (READ+EDIT); stsenariy, agent, bilim bazasi, hisobot, ovoz — faqat READ |
| `VIEWER` | Faqat operatsion READ: dashboard, kampaniya, qo'ng'iroq, jonli, kontakt, stsenariy, agent, bilim bazasi, hisobot, DNC, kiruvchi marshrutlar |
| `SUPERADMIN` | Barcha kompaniya permissionlari + `PLATFORM_ADMIN` (kompaniya yaratish, ro'yxat, statusini o'zgartirish). Faqat platforma kompaniyasida mavjud |

`DEVELOPER` va `SUPERADMIN` ni kompaniyaning o'z admini bera olmaydi — `403
ROLE_NOT_ASSIGNABLE`. Bu ikki rolni faqat `PLATFORM_ADMIN` huquqiga ega chaqiruvchi
biriktiradi.

**Token.** Permissionlar access token ichida **qisqa kod** bilan yuriladi (`perms` claim,
masalan `cmp.r,cmp.w,usr.r`) — enum nomlari bilan token bir necha barobar shishardi.
Frontend uchun kodlar kerak emas: `/api/auth/me` va bu yerdagi javoblar to'liq enum
nomlarini qaytaradi, tarjima ham shu nom bo'yicha qilinadi (`ErrorCode` bilan bir xil
konventsiya).

> **Muhim:** token 12 soat yashaydi, ya'ni undagi kodlar rol tahrirlanganda eskirib qoladi.
> Shuning uchun rol tahrirlansa yoki foydalanuvchining roli almashtirilsa — o'sha
> foydalanuvchilarning **barcha sessiyalari bekor qilinadi** va ular qayta login qiladi.

---

## `GET /api/roles` — kompaniya rollari

Parametrsiz — bitta kompaniyada ko'pi bilan 15 ta rol bo'ladi.

**Javob** (`List<RoleRow>`):

```json
{
  "data": [
    {
      "id": 2,
      "code": "ADMIN",
      "name": "Administrator",
      "description": null,
      "system": true,
      "permissions": ["DASHBOARD_READ", "CAMPAIGN_READ", "CAMPAIGN_EDIT", "..."],
      "userCount": 1,
      "createdAt": "2026-09-05T10:00:00Z"
    },
    {
      "id": 7,
      "code": null,
      "name": "Sotuv menejeri",
      "description": "Kampaniya va kontaktlar, sozlamalarsiz",
      "system": false,
      "permissions": ["DASHBOARD_READ", "CAMPAIGN_READ", "CAMPAIGN_EDIT", "CONTACT_READ", "CONTACT_EDIT", "REPORT_READ"],
      "userCount": 4,
      "createdAt": "2026-09-05T12:30:00Z"
    }
  ],
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

`userCount` — shu rolni ushlab turgan foydalanuvchilar soni (rolni o'chirish mumkinligini
UI shu bo'yicha ko'rsatadi).

---

## `GET /api/roles/permissions` — permission katalogi

Rol tahrirlash oynasidagi checkbox ro'yxati. Sahifa (`group`) bo'yicha guruhlangan;
`PLATFORM_ADMIN` bu ro'yxatga umuman kirmaydi, chunki uni kompaniya roliga berib
bo'lmaydi.

**Javob** (`List<PermissionGroupRow>`):

```json
{
  "data": [
    { "group": "DASHBOARD", "permissions": ["DASHBOARD_READ"] },
    { "group": "CAMPAIGN", "permissions": ["CAMPAIGN_READ", "CAMPAIGN_EDIT"] },
    { "group": "AI_AGENT", "permissions": ["AI_AGENT_READ", "AI_AGENT_EDIT"] },
    { "group": "SCENARIO", "permissions": ["SCENARIO_READ", "SCENARIO_EDIT", "KNOWLEDGE_BASE_READ", "KNOWLEDGE_BASE_EDIT"] }
  ],
  "message": null,
  "messageCode": null,
  "accept": true,
  "errors": null
}
```

Guruh va permission nomlarini frontend o'zi tarjima qiladi (backend matn yubormaydi).

---

## `GET /api/roles/{id}` — bitta rol

**Javob** — `RoleRow`. Rol topilmasa yoki boshqa kompaniyaniki bo'lsa — `404
ROLE_NOT_FOUND`.

---

## `POST /api/roles` — yangi rol

Body (`CreateRoleRequest`):

```json
{
  "name": "Sotuv menejeri",
  "description": "Kampaniya va kontaktlar, sozlamalarsiz",
  "permissions": ["DASHBOARD_READ", "CAMPAIGN_READ", "CAMPAIGN_EDIT", "CONTACT_READ", "CONTACT_EDIT", "REPORT_READ"]
}
```

`name` — kompaniya ichida unique (registrga bog'liq emas). `code` yuborilmaydi: u faqat
tizim rollarida bo'ladi.

| Holat | Javob |
|---|---|
| Nom band | `409 ROLE_NAME_TAKEN` |
| Kompaniyada allaqachon 10 ta o'z roli bor | `409 ROLE_LIMIT_EXCEEDED` |
| `permissions` bo'sh | `400 ROLE_PERMISSIONS_EMPTY` |
| `permissions` ichida `PLATFORM_ADMIN` | `400 ROLE_PERMISSION_NOT_GRANTABLE` |

**Javob** — yaratilgan `RoleRow`.

---

## `PUT /api/roles/{id}` — rolni tahrirlash

Body (`UpdateRoleRequest`) — `POST` bilan bir xil; permission ro'yxati **to'liq
almashtiriladi** (delta emas).

Tizim roli tahrirlanmaydi — `409 ROLE_SYSTEM_READONLY`.

**Yon ta'siri:** shu rolni ushlab turgan barcha foydalanuvchilarning sessiyasi bekor
qilinadi — ularning tokenidagi eski permission kodlari kuchini yo'qotadi va keyingi
so'rovda `401` olib, qayta login qiladilar.

**Javob** — yangilangan `RoleRow`.

---

## `DELETE /api/roles/{id}` — rolni o'chirish

| Holat | Javob |
|---|---|
| Tizim roli | `409 ROLE_SYSTEM_READONLY` |
| Rolni hali kimdir ushlab turibdi | `409 ROLE_IN_USE` (avval o'sha foydalanuvchilarni boshqa rolga o'tkazing) |
| Muvaffaqiyat | `data: null`, `accept: true` |

---

## 2. Permission to'liq ro'yxati

| Guruh (sahifa) | READ | EDIT |
|---|---|---|
| `DASHBOARD` | `DASHBOARD_READ` | — |
| `CAMPAIGN` | `CAMPAIGN_READ` | `CAMPAIGN_EDIT` |
| `CALL` | `CALL_READ` | `CALL_EDIT` |
| `LIVE` | `LIVE_READ` | `LIVE_EDIT` |
| `OPERATOR` | `OPERATOR_READ` | `OPERATOR_EDIT` |
| `CONTACT` | `CONTACT_READ` | `CONTACT_EDIT` |
| `AI_AGENT` | `AI_AGENT_READ` | `AI_AGENT_EDIT` |
| `SCENARIO` | `SCENARIO_READ`, `KNOWLEDGE_BASE_READ` | `SCENARIO_EDIT`, `KNOWLEDGE_BASE_EDIT` |
| `REPORT` | `REPORT_READ` | `REPORT_EDIT` |
| `AUDIT` | `AUDIT_READ` | — |
| `DO_NOT_CALL` | `DO_NOT_CALL_READ` | `DO_NOT_CALL_EDIT` |
| `INBOUND_ROUTE` | `INBOUND_ROUTE_READ` | `INBOUND_ROUTE_EDIT` |
| `SIP_TRUNK` | `SIP_TRUNK_READ` | `SIP_TRUNK_EDIT` |
| `USER` | `USER_READ` | `USER_EDIT` |
| `ROLE` | `ROLE_READ` | `ROLE_EDIT` |
| `COMPANY` | `COMPANY_READ` | `COMPANY_EDIT` |
| `AI_MODEL` | `AI_MODEL_READ` | `AI_MODEL_EDIT` |
| `ENGINE` | `ENGINE_READ` | `ENGINE_EDIT` |
| `VOICE` | `VOICE_READ` | `VOICE_EDIT` |
| `NOTIFICATION_SETTINGS` | `NOTIFICATION_SETTINGS_READ` | `NOTIFICATION_SETTINGS_EDIT` |
| `INTEGRATION` | `INTEGRATION_READ` | `INTEGRATION_EDIT` |
| `BILLING` | `BILLING_READ` | `BILLING_EDIT` |
| `PLATFORM` | — | `PLATFORM_ADMIN` (faqat SUPERADMIN) |

**Permissionsiz endpointlar** (har qanday login qilgan foydalanuvchi uchun ochiq):
`/api/auth/me`, `/api/auth/logout`, `/api/profile/**`, `/api/notifications/**`,
`/api/files/**`, `/api/search`, `/api/tts/voices` katalogi.

**M2M kirish:** alohida kalit yo'q — tashqi xizmat ham oddiy foydalanuvchi sifatida
`POST /api/auth/login` orqali kiradi va o'ziga berilgan rol permissionlarini oladi.
Faqat o'qish kerak bo'lsa — `VIEWER` roli.
