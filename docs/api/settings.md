## CRM Integratsiyalari — `uz.murodjon.robotcallv2.integration`

Kompaniyalar tashqi CRM tizimlarini o'z hisoblariga to'liq ulashi mumkin: **Uysot CRM**, **amoCRM**, **Kommo (Global amoCRM)** va **Bitrix24**.

### `GET /api/settings/integrations/catalog` — Mavjud integratsiyalar katalogi

```json
[
  { "provider": "UYSOT", "displayName": "Uysot CRM", "authMethod": "OAUTH", "available": true },
  { "provider": "AMOCRM", "displayName": "amoCRM", "authMethod": "OAUTH", "available": true },
  { "provider": "KOMMO", "displayName": "Kommo CRM (Global amoCRM)", "authMethod": "OAUTH", "available": true },
  { "provider": "BITRIX24", "displayName": "Bitrix24", "authMethod": "OAUTH", "available": true }
]
```

### 1. Uysot CRM Integratsiyasi
- **Protokol**: Uysot Open API v1 & OAuth 2.0 (`https://apidoc.app.uysot.uz`).
- **Autentifikatsiya**: `X-Open-Api-Token` sarlavhasi bilan so'rovlar yuboriladi.
- **Funksiyalar**:
  - `GET /v1/open-api/lead/{id}` — mijoz/qarz ma'lumotlarini jonli olish.
  - `POST /v1/open-api/lead/{id}/note` — AI xulosa va QA baholarini (`qaScore`, `commitmentScore`) yozish.
  - `POST /v1/open-api/call-history` — audio yozuv va davomiylikni biriktirish.

### `GET /api/settings/integrations` — Joriy integratsiya holati

**Response** (`CrmIntegration`):

```json
{
  "companyId": 1,
  "provider": "UYSOT",
  "appName": "Bizning CRM integratsiyamiz",
  "grants": [
    { "permission": "LEAD", "scope": "READ" },
    { "permission": "CALL", "scope": "SAVE" }
  ],
  "status": "CONNECTED",
  "connectedAt": "2026-08-02T10:00:00Z"
}
```

### `PUT /api/settings/integrations/uysot` — Uysot App & Grants sozlash

```json
{
  "appName": "Bizning CRM integratsiyamiz",
  "grants": [
    { "permission": "LEAD", "scope": "READ" },
    { "permission": "CALL", "scope": "SAVE" }
  ]
}
```

### `GET /api/settings/integrations/uysot/authorize-url` — OAuth avtorizatsiya havolasi

```json
{
  "authorizeUrl": "https://app.uysot.uz/oauth/authorize?client_id=...&app_name=...&redirect_url=...&grants=...&state=..."
}
```

### 2. amoCRM & Kommo CRM Integratsiyasi
- **Funksiyalar**:
  - `/api/v4/contacts?query={phone}&with=leads` — Telefon orqali kontakt va faol bitimni aniqlash.
  - `/api/v4/leads/{id}/notes` — AI qo'ng'iroq xulosasi, sentiment va QA bahosini yozish.
  - `/api/v4/calls` — Telefoniya pleyeriga audio yozuvni biriktirish.
  - `/api/v4/tasks` — Qayta qo'ng'iroq vazifalarini avtomatik ochish.

### 3. Bitrix24 Integratsiyasi
- **Funksiyalar**:
  - `crm.contact.list` / `crm.lead.list` — Telefon orqali kontakt topish.
  - `crm.timeline.comment.add` — Bitim kartochkasi taymlayniga AI sharhini qoldirish.
  - `telephony.externalcall.register/finish` — Audio yozuv va davomiylikni biriktirish.
  - `tasks.task.add` — Qayta qo'ng'iroq vazifasini yaratish.
