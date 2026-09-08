# API kalitlari

`uz.murodjon.robotcallv2.apikey` · huquq: **API_KEY_READ** / **API_KEY_EDIT**

Kompaniyaning o'z tizimlari bu API'ni **odam login qilmasdan** chaqirishi uchun kalit.
Ilgari yagona hisob ma'lumoti foydalanuvchi JWT'si edi — ya'ni integratsiya yo kimningdir
akkaunti ostida ishlardi (uning barcha huquqlarini meros olib, u ishdan ketsa to'xtab),
yo umuman ishlamasdi.

Umumiy javob formati va xatolar uchun [README.md](README.md)ga qarang.

---

## 1. Kalit bilan chaqirish

`Authorization: Bearer <jwt>` o'rniga:

```http
GET /api/campaigns
X-Api-Key: rc_live_0123456789ab_xY3...
```

- Ikkalasi ham yuborilsa — **Bearer token ustun**, kalit e'tiborga olinmaydi.
- Noto'g'ri, bekor qilingan yoki muddati o'tgan kalit → `401`.
- Kalitda yetarli huquq bo'lmasa → `403`.

Kalit **o'z kompaniyasi nomidan** ishlaydi: `companyId` kalit qatoridan olinadi, so'rovda
uzatilmaydi va uzatib bo'lmaydi ham.

---

## 2. Kalit nima qila oladi va nima qila olmaydi

Chegara ataylab shunday qo'yilgan: **kalit ma'lumotni ko'chiradi, platformaning
xatti-harakatini yoki narxini o'zgartira olmaydi.**

| Berish mumkin | Nima uchun |
|---|---|
| `CONTACT_READ`, `CONTACT_EDIT` | CRM kontaktlarni yuboradi |
| `CAMPAIGN_READ`, `CAMPAIGN_EDIT` | Kampaniya yaratish, target qo'shish, ishga tushirish |
| `DO_NOT_CALL_READ`, `DO_NOT_CALL_EDIT` | Qora ro'yxatni tashqi tizim bilan sinxronlash |
| `KNOWLEDGE_BASE_READ`, `KNOWLEDGE_BASE_EDIT` | Bilimlar bazasini avtomatik yangilash |
| `CALL_READ`, `REPORT_READ`, `DASHBOARD_READ`, `LIVE_READ` | Natijalarni o'qish |
| `AI_AGENT_READ`, `SCENARIO_READ`, `INBOUND_ROUTE_READ` | Konfiguratsiyani **o'qish** |
| `BILLING_READ` | Sarfni ko'rish |

**Hech qachon berilmaydi** (ro'yxat kodda — `ApiKeyScopes`): `BILLING_EDIT`,
`AI_AGENT_EDIT`, `SCENARIO_EDIT`, `SIP_TRUNK_EDIT`, `AI_MODEL_EDIT`, `ENGINE_EDIT`,
`VOICE_EDIT`, `INTEGRATION_EDIT`, `NOTIFICATION_SETTINGS_EDIT`, `USER_*`, `ROLE_*`,
`COMPANY_EDIT`, `OPERATOR_*`, `API_KEY_*`, `PLATFORM_ADMIN`.

> ℹ️ Ro'yxatdan tashqaridagi scope **rad etilmaydi, tashlab yuboriladi** — kalit yaratilsa
> ham u huquqni olmaydi. Kesish har so'rovda qaytadan qo'llanadi, shuning uchun bazadagi
> `api_key_scope` qatorlarini qo'lda tahrirlash ham kalitga yangi huquq bermaydi.
>
> Kalit **o'zi kalit yarata olmaydi**: `API_KEY_EDIT` berilmaydigan huquqlar ichida.

Kalit odam emas, shuning uchun "mening qatorlarim" bilan ishlaydigan endpointlar
(`/api/profile/**`, `/api/notifications/**`) unga `403 NO_USER_SESSION` qaytaradi.

---

## 3. `GET /api/api-keys/scopes` — Berilishi mumkin bo'lgan huquqlar

Forma shu ro'yxatdan to'ldiriladi — frontend o'zida qattiq yozmasin, chegara kodda
o'zgarishi mumkin.

```json
{
  "accept": true,
  "data": ["DASHBOARD_READ", "CAMPAIGN_READ", "CAMPAIGN_EDIT", "CALL_READ", "..."],
  "message": null, "messageCode": null, "errors": null
}
```

---

## 4. `POST /api/api-keys` — Kalit yaratish

### Request Body (`CreateApiKeyRequest`)
```json
{
  "name": "CRM integratsiyasi (prod)",
  "scopes": ["CONTACT_READ", "CONTACT_EDIT", "CAMPAIGN_READ", "CALL_READ"],
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `name` | `string` | ✅ | Maks. 128 belgi |
| `scopes` | `string[]` | ✅ | Kesishdan keyin bo'sh qolsa `400 API_KEY_SCOPES_REQUIRED` |
| `expiresAt` | `string` (ISO-8601) | ❌ | `null` — muddatsiz. O'tgan vaqt → `400 API_KEY_EXPIRY_IN_PAST` |

### Response (`CreatedApiKeyResponse`)
```json
{
  "accept": true,
  "data": {
    "apiKey": {
      "id": 12,
      "name": "CRM integratsiyasi (prod)",
      "keyPrefix": "rc_live_0123456789ab",
      "scopes": ["CONTACT_READ", "CONTACT_EDIT", "CAMPAIGN_READ", "CALL_READ"],
      "lastUsedAt": null,
      "expiresAt": "2027-01-01T00:00:00Z",
      "revokedAt": null,
      "createdAt": "2026-09-07T10:00:00Z"
    },
    "key": "rc_live_0123456789ab_xY3kQ..."
  },
  "message": null, "messageCode": null, "errors": null
}
```

> ⚠️ **`key` faqat shu javobda qaytadi va boshqa hech qachon.** Bazada faqat SHA-256
> hash saqlanadi, shuning uchun uni ko'rsatadigan endpoint yo'q va bo'lmaydi ham —
> yo'qotilgan kalitni tiklab bo'lmaydi, yangisini yaratish kerak.

---

## 5. `GET /api/api-keys` — Kompaniya kalitlari

`ApiKeyRow` ro'yxati, yangisidan eskisiga. Sirni olib yurmaydi — `keyPrefix` faqat bir
kalitni ikkinchisidan ajratishga yetadi.

Bekor qilingan kalitlar ham ro'yxatda qoladi (`revokedAt` to'ldirilgan holda): audit izi
o'qilishi uchun qator o'chirilmaydi.

---

## 6. `DELETE /api/api-keys/{id}` — Bekor qilish

Kalit shu zahoti ishlamay qoladi (holat har so'rovda tekshiriladi, kesh yo'q). Qator
saqlanib qoladi. Allaqachon bekor qilingan kalitni qayta bekor qilish hech narsani
o'zgartirmaydi va baribir `200` qaytaradi.

Boshqa kompaniyaning kaliti → `404 API_KEY_NOT_FOUND`.

---

## 7. Audit

Kalit bilan bajarilgan har bir amal `audit_log` da **kalit prefiksi** bilan yoziladi
(`rc_live_0123456789ab (API_KEY)`) — ya'ni bir odamning ismi emas, balki bekor qilinadigan
narsaning nomi. Kalit yaratish va bekor qilishning o'zi ham `API_KEY_CREATED` /
`API_KEY_REVOKED` sifatida yoziladi.
