# Integratsiya vositalari katalogi — platforma admini

`/api/platform/integration-tools` · huquq: **PLATFORM_ADMIN**

Integratsiya (hozir Uysot) AI agentga beradigan vositalarni platforma admini yuritadi: nomi, tavsifi,
guruhi, sukut holati. Kompaniya ularni o'zining "O'rnatish → Vositalar" oynasida yoqadi yoki
o'chiradi ([settings.md §5](settings.md#get-apisettingsintegrationstools--agentga-beriladigan-crm-vositalari)).

## Qanday ishlaydi

| Daraja | Nima | Kim boshqaradi |
|---|---|---|
| `platformEnabled` | `false` bo'lsa vosita **hech bir** kompaniyada ko'rinmaydi va agentga berilmaydi | admin |
| `defaultEnabled` | kompaniya o'zi tanlamagan bo'lsa shu holat amal qiladi | admin |
| kompaniya tanlovi | faqat sukutdan **farq qiladigan** tanlov saqlanadi | kompaniya |

Kompaniya tanlamagan vosita sukutga **ergashadi**. Kompaniyaning o'z tanlovi sukut o'zgarganda ham
hech qachon bosilmaydi.

## `GET /api/platform/integration-tools?provider=UYSOT`

Provayder katalogi (`provider` sukut bo'yicha `UYSOT`), platforma o'chirgan vositalar ham bor.

```json
{
  "accept": true,
  "data": [
    {
      "id": 8,
      "provider": "UYSOT",
      "tool": "CREATE_LEAD",
      "kind": "BUILT_IN",
      "group": "LEADS",
      "label": "Lid yaratish",
      "description": "CRM'da yo'q mijoz uchun yangi lid ochadi.",
      "permission": "LEAD",
      "scope": "SAVE",
      "writes": true,
      "defaultEnabled": true,
      "platformEnabled": true,
      "sortOrder": 80,
      "definition": null,
      "modelDescription": "Opens a NEW lead in the CRM for this caller. Call it only when ..."
    }
  ]
}
```

- `kind: "BUILT_IN"` — vosita kodda yozilgan; `tool` (kalit), `permission`, `scope` o'zgartirilmaydi,
  `definition` — `null`. `modelDescription` — **modelga** ko'rinadigan matn (inglizcha; qachon chaqirish va
  nimani aytish qoidalari). `null` — koddagi matn ishlatiladi. Migratsiya (V15) hozirgi koddagi matnni
  boshlang'ich qiymat qilib yozgan. Parametrlarning tavsifi (`@ToolParam`) kodda qoladi.
- `kind: "HTTP"` — admin o'zi yaratgan so'rov; `definition` — kompaniya vositasi (`/api/tools`) shaklidagi
  tavsif (`name`, `description`, `http`, `effect` …). Modelga `definition.description` ko'rinadi,
  kompaniyaga — `label`/`description`.

## `PUT /api/platform/integration-tools/{id}`

```json
{
  "label": "Lid yaratish",
  "description": "CRM'da yo'q mijoz uchun yangi lid ochadi.",
  "group": "LEADS",
  "defaultEnabled": false,
  "platformEnabled": true,
  "sortOrder": 80,
  "applyTo": "NEW_ONLY"
}
```

`group`: `FLATS` · `DEBTS` · `LEADS` · `BOOKINGS`. Javob — yangilangan qator (`GET` dagidek).

**Ichki vosita** uchun qo'shimcha: `modelDescription` — modelga ko'rinadigan yangi matn; bo'sh yoki
`null` yuborilsa koddagi matnga qaytadi. HTTP vositada bu maydon e'tiborga olinmaydi (uniki `definition.description`).
Matn **yangi qo'ng'iroqlardan** boshlab amal qiladi.

**HTTP vosita** uchun qo'shimcha: `permission` + `scope` (ikkalasi birga yoki ikkalasi ham yo'q),
`definition` — **majburiy**, eskisini to'liq almashtiradi. Ichki (`BUILT_IN`) vositada bu uchtasi
e'tiborga olinmaydi.

### `applyTo` — sukut o'zgarganda kimga tegadi

`defaultEnabled` saqlangan qiymatdan **farq qilsa** `applyTo` majburiy, aks holda e'tiborga olinmaydi.

| `applyTo` | Natija |
|---|---|
| `ALL` | tanlamagan barcha kompaniyalar yangi sukutga o'tadi |
| `NEW_ONLY` | tanlamagan **mavjud** kompaniyalar eski holatda qotib qoladi (ularga shu holat ochiq yoziladi); faqat keyin yaratilgan kompaniyalar yangi sukutni oladi |

Ikkalasida ham kompaniyaning o'z tanlovi o'zgarmaydi. `platformEnabled` o'zgarishi har doim hammaga
bir zumda ta'sir qiladi (`applyTo` unga tegishli emas).

| HTTP | `messageCode` | Sabab |
|---|---|---|
| 400 | `INTEGRATION_TOOL_PROPAGATION_REQUIRED` | sukut o'zgardi, lekin `applyTo` yo'q |
| 404 | `INTEGRATION_TOOL_NOT_FOUND` | `id` katalogda yo'q |
| 400 | `VALIDATION_FAILED` | `label` / `description` bo'sh, `group` noma'lum |

O'zgarish **yangi qo'ng'iroqlardan** boshlab amal qiladi. Har bir o'zgarish audit jurnaliga yoziladi
(`INTEGRATION_TOOL_UPDATE`; admin kompaniyasi ostida).

## `POST /api/platform/integration-tools` — HTTP vosita qo'shish

Admin Uysot API'ga yangi so'rov yaratadi; kompaniya xodimlari uni "Vositalar" oynasida boshqa vositalar
kabi yoqib-o'chiradi. Agent uni qo'ng'iroqda **kompaniyaning o'z Uysot hisobi va tokeni** bilan chaqiradi.

```json
{
  "provider": "UYSOT",
  "key": "LOOKUP_CONTRACT_BY_PHONE",
  "group": "DEBTS",
  "label": "Shartnoma — telefon bo'yicha",
  "description": "Mijozning shartnomalarini telefon raqami bo'yicha topadi.",
  "permission": "CONTRACT",
  "scope": "READ",
  "defaultEnabled": true,
  "platformEnabled": true,
  "sortOrder": 95,
  "applyTo": "NEW_ONLY",
  "definition": {
    "name": "lookupContractByPhone",
    "description": "Mijozning shartnomalarini topadi. Qarz yoki to'lov haqida so'ralganda chaqir.",
    "http": {
      "url": "v1/open-api/contract/filter",
      "method": "POST",
      "bodySchema": {
        "type": "object",
        "properties": {
          "page": { "type": "integer", "x-source": "static", "x-value": 1 },
          "size": { "type": "integer", "x-source": "static", "x-value": 5 },
          "search": { "type": "string", "x-source": "variable", "x-value": "phone" }
        }
      }
    },
    "effect": "READ"
  }
}
```

- `key` — `UPPER_SNAKE_CASE` (3–64 belgi), provayder ichida noyob; keyin o'zgarmaydi.
- `definition.name` — modelga ko'rinadigan nom (harf bilan boshlanadi, harf/raqam/`_`, 3–64); ichki
  vositalar va shu provayderning boshqa HTTP vositalari nomlari bilan takrorlanmaydi.
- **`definition.http.url` — Uysot manziliga nisbatan yo'l** (`v1/open-api/...`): sxema, `/` bilan boshlanish,
  `..`, `//`, `@`, bo'sh joy, `{{...}}` taqiqlangan. Manzil va token har bir kompaniyaning ulanishidan olinadi.
  Yo'ldagi `{id}` kabi o'rinlar `pathParams` bilan to'ldiriladi (qiymat URL-kodlanadi).
- `definition.http.headers` va `definition.http.auth` **berilmaydi** (token sarlavhasi `X-Open-Api-Token`ni platforma o'zi qo'yadi).
- Tanada/so'rovda qo'ng'iroq o'zgaruvchilari: `x-source: variable` bilan `phone`, `language`, `call_attempt_id`
  va qo'ng'iroq faktlari. `x-source: secret` bu yerda ishlamaydi (kompaniya sirlari o'qilmaydi).
- `effect: WRITE` bo'lsa, kompaniya vositasidagidek qo'ng'iroq paytida mijozdan tasdiq olinadi.
- `defaultEnabled: true` bo'lsa `applyTo` majburiy: `ALL` — mavjud kompaniyalar ham oladi; `NEW_ONLY` —
  mavjudlarda o'chiq qotadi, keyin yaratilganlar oladi.
- Kompaniyaga vosita faqat Uysot grantlari `permission:scope` ni qoplasa beriladi (ichki vositalardagidek).

| HTTP | `messageCode` | Sabab |
|---|---|---|
| 400 | `INTEGRATION_TOOL_KEY_INVALID` | kalit formati noto'g'ri |
| 409 | `INTEGRATION_TOOL_KEY_TAKEN` | kalit katalogda bor |
| 400 | `INTEGRATION_TOOL_NAME_INVALID` | model nomi noto'g'ri yoki band |
| 400 | `INTEGRATION_TOOL_PATH_INVALID` | `http.url` nisbiy yo'l emas |
| 400 | `INTEGRATION_TOOL_HTTP_LOCKED` | `headers` yoki `auth` berilgan |
| 400 | `INTEGRATION_TOOL_GRANT_INCOMPLETE` | `permission` va `scope` faqat birga |
| 400 | `INTEGRATION_TOOL_PROPAGATION_REQUIRED` | `defaultEnabled: true`, lekin `applyTo` yo'q |
| 400 | `TOOL_RESPONSE_MAPPING_INVALID` / tana xatolari | `definition` ichidagi tekshiruvlar (kompaniya vositasidagidek) |

Audit: `INTEGRATION_TOOL_CREATE`.

## `DELETE /api/platform/integration-tools/{id}`

HTTP vositani va barcha kompaniyalarning u haqidagi tanlovini o'chiradi. Ichki vosita — 400
`INTEGRATION_TOOL_BUILT_IN_LOCKED` (uni `platformEnabled: false` bilan yashiring). Yo'q `id` — 404.
Audit: `INTEGRATION_TOOL_DELETE`.

## `POST /api/platform/integration-tools/{id}/test`

HTTP vositani **admin o'z kompaniyasining Uysot hisobi** bilan bir marta ishga tushiradi va modelga
ko'rinadigan javobni qaytaradi. Tana: kompaniya vositasining testidagi kabi (`TestToolRequest`:
`variables`, `args`; ixtiyoriy). **`WRITE` so'rov haqiqatan yuboriladi.**

| HTTP | `messageCode` | Sabab |
|---|---|---|
| 404 | `INTEGRATION_TOOL_NOT_FOUND` | `id` yo'q |
| 400 | `INTEGRATION_TOOL_BUILT_IN_LOCKED` | ichki vositada sinash yo'q |
| 409 | `INTEGRATION_TOOL_TEST_NOT_CONNECTED` | admin kompaniyasi Uysot'ga ulanmagan |

Audit: `INTEGRATION_TOOL_TEST`.
