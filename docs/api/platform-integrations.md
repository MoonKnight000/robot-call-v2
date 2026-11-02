# Platforma admini: integratsiyalar katalogi

Kompaniyalar ulay oladigan integratsiyalar ro'yxatini **platforma admini o'zi** yuritadi: nomi, kategoriyasi
(CRM / fayl menejeri / SMS / …), ulanish usullari va logosi. Katalog DB'da; **ulagich (konnektor) kodi** esa
kodda — shuning uchun yozuvning `available` maydonini faqat reliz o'zgartiradi (`true` — kodda ulagich bor; `false` —
kompaniyalar "tez orada" holatida ko'radi). Admin qo'shgan yozuv `available = false` bilan boshlanadi.

Hammasi `PLATFORM_ADMIN` roli uchun. Javob — `ResponseData` ichida. Kompaniya ko'zi bilan katalog:
[settings.md](settings.md) §5 (`GET /api/settings/integrations/catalog`).

Sukut (yagona) yozuvlar, `builtIn = true` — o'chirib bo'lmaydi, nomini/kategoriyasini o'zgartirish mumkin:

| Kalit | Nomi | Kategoriya | Ulanish usullari | `available` |
|---|---|---|---|---|
| `UYSOT` | Uysot CRM | `CRM` | `OAUTH` | ✅ |
| `AMOCRM` | amoCRM | `CRM` | `API_TOKEN` | ✅ |
| `KOMMO` | Kommo CRM (Global amoCRM) | `CRM` | `API_TOKEN` | ✅ |
| `BITRIX24` | Bitrix24 | `CRM` | `API_TOKEN` | ✅ |
| `GOOGLE_DRIVE` | Google Drive | `FILE_MANAGER` | `OAUTH` | — |
| `NOTION` | Notion | `KNOWLEDGE_BASE` | `OAUTH` | — |
| `ESKIZ` | Eskiz SMS | `SMS` | `LOGIN_PASSWORD` | ✅ |
| `ASTERISK` | Asterisk | `TELEPHONY` | `LOGIN_PASSWORD` | — |

Sukut kategoriyalar: `CRM`, `FILE_MANAGER`, `KNOWLEDGE_BASE`, `SMS`, `TELEPHONY`.

**Ulanish usullari (`authMethods`):** `OAUTH` — provayderning rozilik ekrani; `API_TOKEN` — kompaniya provayderdan
nusxalagan token; `LOGIN_PASSWORD` — hisobning login va paroli. Bir yozuv bir nechtasini qo'llashi mumkin (masalan
Uysot: `["OAUTH", "API_TOKEN"]`); birinchisi — ustuvor (`authMethod`). Kamida bittasi shart.

## Yozuvlar

### `GET /api/platform/integrations`

`IntegrationDefinitionRow[]`, kompaniyalar ko'radigan tartibda (`sortOrder`, keyin kalit):

| Maydon | Izoh |
|---|---|
| `provider` | Yozuv kaliti |
| `name` | Nomi |
| `category` / `categoryName` | Kategoriya kaliti va sarlavhasi |
| `authMethods` | Ulanish usullari, ustuvori birinchi |
| `available` | Kodda ulagich bor (faqat reliz o'zgartiradi) |
| `builtIn` | Platforma bilan kelgan; o'chirib bo'lmaydi |
| `sortOrder` | Tartib (o'sish bo'yicha) |
| `iconFileId` | Logoning `stored_file` id'si; yozuvni qayta saqlaganda (`PUT`) shuni qaytarib yuboring, aks holda logo olib tashlanadi |
| `iconUrl` | Logo yo'li (`/api/integration-icons/{provider}?v=...`, `Authorization` bilan olinadi); yo'q bo'lsa `null` |

### `POST /api/platform/integrations`

```json
{ "key": "MY_SMS", "name": "My SMS", "category": "SMS", "authMethods": ["LOGIN_PASSWORD"], "sortOrder": null, "iconFileId": 57 }
```

`key` — `UPPER_SNAKE_CASE`, 2–32 belgi, harf bilan boshlanadi (kichik harf katta qilinadi). `sortOrder` bo'sh bo'lsa —
oxiriga. Yozuv `available = false`, `builtIn = false`. Xatolar: `400 INTEGRATION_KEY_INVALID`,
`INTEGRATION_NAME_INVALID`, `INTEGRATION_AUTH_METHODS_REQUIRED`; `404 INTEGRATION_CATEGORY_NOT_FOUND`;
`409 INTEGRATION_KEY_TAKEN`. Audit: `INTEGRATION_CREATE`.

### `PUT /api/platform/integrations/{key}`

```json
{ "name": "Eskiz", "category": "SMS", "authMethods": ["LOGIN_PASSWORD"], "sortOrder": 70, "iconFileId": 57 }
```

Nom, kategoriya, ulanish usullari va tartibni almashtiradi (`sortOrder: null` — o'zgarishsiz). `available` va `builtIn`
o'zgarmaydi. `404 INTEGRATION_NOT_FOUND`. Audit: `INTEGRATION_UPDATE`.

### `DELETE /api/platform/integrations/{key}`

Faqat admin qo'shgan yozuvni o'chiradi; uning kompaniyalardagi yoqish/o'chirish belgilari va logosi ham o'chadi.
Built-in yozuv — `400 INTEGRATION_BUILT_IN_LOCKED`; yo'q yozuv — `404 INTEGRATION_NOT_FOUND`. Audit: `INTEGRATION_DELETE`.

## Kategoriyalar

### `GET /api/platform/integration-categories`

`[{ "key": "CRM", "name": "CRM tizimlari", "sortOrder": 10 }, …]`

### `POST /api/platform/integration-categories`

`{ "key": "MESSENGER", "name": "Messenjerlar", "sortOrder": null }` — kalit qoidasi yozuvniki bilan bir xil.
`409 INTEGRATION_CATEGORY_KEY_TAKEN`. Audit: `INTEGRATION_CATEGORY_CREATE`.

### `PUT /api/platform/integration-categories/{key}`

`{ "name": "…", "sortOrder": 15 }` (`sortOrder: null` — o'zgarishsiz). `404 INTEGRATION_CATEGORY_NOT_FOUND`.
Audit: `INTEGRATION_CATEGORY_UPDATE`.

### `DELETE /api/platform/integration-categories/{key}`

Kategoriya ostida yozuv qolgan bo'lsa — `409 INTEGRATION_CATEGORY_IN_USE` (avval yozuvlarni boshqa kategoriyaga
ko'chiring). Audit: `INTEGRATION_CATEGORY_DELETE`.

## Boshqa ekranlarga ta'siri

- `provider` (katalog, `disabledIntegrations`, logo yo'llari) endi yopiq ro'yxat emas — erkin kalit; frontend nomni va
  kategoriyani **katalogdan** olishi kerak, qattiq kodlangan jadvaldan emas.
- Katalogda yo'q kalit `disabledIntegrations` ga yuborilsa, e'tiborga olinmaydi.

## Logo faylini olish

`GET /api/integration-icons/{provider}` — logo rasmi (har qanday kirgan foydalanuvchi uchun; logo yo'q bo'lsa `404`).
Katalogdagi `iconUrl` shu yo'lga ishora qiladi. Brauzer `<img src>` sarlavha yubora olmagani uchun frontend uni
`fetch` bilan blob qilib oladi (`requestBlob`, [files.md](files.md) dagi kabi).

## Logo qanday o'rnatiladi

Logo — yozuvning o'z maydoni, alohida endpoint yo'q. Ikki qadam:

1. Rasmni umumiy fayl API'siga yuklang: `POST /api/files/upload` ([files.md](files.md)) → javobdagi `id`.
2. Shu `id` ni yozuvni yaratish/yangilashdagi `iconFileId` ga yozing (`POST` yoki `PUT /api/platform/integrations/{key}`).

Talablar: rasm adminning o'z kompaniyasi yuklagan, `image/png`, `image/jpeg`, `image/webp` yoki `image/svg+xml`, ko'pi bilan
128 KB (aks holda `404 FILE_NOT_FOUND`, `400 INTEGRATION_ICON_TOO_LARGE` / `INTEGRATION_ICON_TYPE_UNSUPPORTED`).
`PUT` butun yozuvni almashtiradi: `iconFileId: null` — logoni olib tashlaydi; mavjud logoni saqlab qolish uchun
`GET` dagi `iconFileId` ni qaytarib yuboring. Logo almashganda yoki yozuv o'chirilganda eski fayl MinIO'dan ham o'chadi.
