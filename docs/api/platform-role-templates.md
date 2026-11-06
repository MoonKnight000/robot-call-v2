# Platforma: standart rollar — `/api/platform/role-templates`

Rol: `PLATFORM_ADMIN`. Har bir **yangi** kompaniya yaratilganda shu ro'yxatdagi har bir shablon kompaniyaning
o'z roli bo'lib nusxalanadi; keyin kompaniya admini o'z nusxasini o'zicha tahrirlaydi
([roles.md](roles.md)). Shablonni o'zgartirish yoki o'chirish **mavjud kompaniyalarga ta'sir qilmaydi**.

> Holat (2026-11-06): shablonlar saqlanadi, boshqariladi va har yangi kompaniyaga nusxalanadi. Mavjud
> kompaniyalarning rollari (ilgari kodda hisoblangan) birinchi ishga tushishda DB ga yozib qo'yiladi va
> to'liq tahrirlanadi ([roles.md](roles.md)).

**Boshlang'ich qiymat.** Jadval bo'sh bo'lsa, ilova ishga tushganda bugungi tizim rollari shablon bo'lib bir
marta yoziladi: `DEVELOPER`, `ADMIN`, `OPERATOR`, `VIEWER`, `SUPERVISOR` — hozirgi permissionlari bilan.
`ADMIN` shablonini o'chirib bo'lmagani uchun jadval qayta bo'shamaydi va o'chirilgan shablon qaytib kelmaydi.

## `RoleTemplateRow`

```json
{
  "id": 2,
  "code": "ADMIN",
  "name": "Administrator",
  "description": null,
  "assignableByCompany": true,
  "permissions": ["DASHBOARD_READ", "USER_EDIT", "ROLE_EDIT", "..."],
  "createdAt": "2026-11-06T10:00:00Z"
}
```

- `code` — faqat boshlang'ich (seed) shablonlarda: `DEVELOPER`, `ADMIN`, … Platforma qo'shgan shablonda `null`.
  O'zgarmaydi; nusxaga ham o'tadi — kod "kompaniyaning ADMIN roli"ni shu bilan topadi.
- `assignableByCompany = false` — bu roldagi nusxani xodimga faqat platforma admini bera oladi (bugun `DEVELOPER`).

## `GET /api/platform/role-templates` — ro'yxat

Javob — `RoleTemplateRow[]`, `id` tartibida.

## `GET /api/platform/role-templates/permissions` — permission katalogi

Shablon tahrirlash oynasi uchun; javob `GET /api/roles/permissions` bilan bir xil (`PermissionGroupRow[]`,
[roles.md](roles.md)).

## `GET /api/platform/role-templates/{id}` — bitta shablon

Topilmasa — `404 ROLE_TEMPLATE_NOT_FOUND`.

## `POST /api/platform/role-templates` — shablon qo'shish

Body (`SaveRoleTemplateRequest`):

```json
{
  "name": "Sotuv menejeri",
  "description": "Kampaniya va kontaktlar",
  "assignableByCompany": true,
  "permissions": ["DASHBOARD_READ", "CAMPAIGN_READ", "CAMPAIGN_EDIT", "CONTACT_READ", "CONTACT_EDIT"]
}
```

`assignableByCompany` yuborilmasa — `true`. `code` yuborilmaydi.

| Holat | Javob |
|---|---|
| Nom band (registrga bog'liq emas) | `409 ROLE_TEMPLATE_NAME_TAKEN` |
| `permissions` bo'sh | `400 ROLE_PERMISSIONS_EMPTY` |
| `permissions` ichida `PLATFORM_ADMIN` | `400 ROLE_PERMISSION_NOT_GRANTABLE` |

Javob — yaratilgan `RoleTemplateRow`. Audit: `ROLE_TEMPLATE_CREATE`.

## `PUT /api/platform/role-templates/{id}` — tahrirlash

Body — `POST` bilan bir xil; permission ro'yxati **to'liq almashtiriladi**. Yuqoridagi xatolardan tashqari:
`ADMIN` shablonidan `USER_EDIT` yoki `ROLE_EDIT` olib tashlansa — `400 ROLE_TEMPLATE_ADMIN_PERMISSION_REQUIRED`
(har yangi kompaniyaning birinchi admini shu rolda yaratiladi). Audit: `ROLE_TEMPLATE_UPDATE`.

## `DELETE /api/platform/role-templates/{id}` — o'chirish

`ADMIN` shablonini o'chirib bo'lmaydi — `409 ROLE_TEMPLATE_PROTECTED`. Muvaffaqiyatda `data: null`.
Audit: `ROLE_TEMPLATE_DELETE`.
