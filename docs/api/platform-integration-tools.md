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
      "group": "LEADS",
      "label": "Lid yaratish",
      "description": "CRM'da yo'q mijoz uchun yangi lid ochadi.",
      "permission": "LEAD",
      "scope": "SAVE",
      "writes": true,
      "defaultEnabled": true,
      "platformEnabled": true,
      "sortOrder": 80,
      "definition": { "name": "createLead", "description": "Opens a NEW lead in the CRM for this caller. ...", "http": { "...": "..." }, "effect": "WRITE" }
    }
  ]
}
```

- Har bir vosita — HTTP so'rov (migratsiya `V28` dan buyon kodda yozilgan "ichki" vosita yo'q; `kind` va
  `modelDescription` maydonlari olib tashlandi). `definition` — kompaniya vositasi (`/api/tools`) shaklidagi
  tavsif (`name`, `description`, `http`, `effect` …), hech qachon `null` emas. Modelga `definition.description` ko'rinadi,
  kompaniyaga — `label`/`description`. `definition.result` (`maxItems`, `emptyMessage`, `errorMessage`)
  ham shu yerda — ro'yxatni qisqartirish va bo'sh/xato javobda modelga nima deyilishi ([tools.md](tools.md) §3).

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

Qo'shimcha: `permission` + `scope` (ikkalasi birga yoki ikkalasi ham yo'q), `definition` — **majburiy**,
eskisini to'liq almashtiradi (yo'q bo'lsa — 400 `INTEGRATION_TOOL_DEFINITION_REQUIRED`). Modelga ko'rinadigan
matn — `definition.description`; **yangi qo'ng'iroqlardan** boshlab amal qiladi.

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
| 400 | `INTEGRATION_TOOL_DEFINITION_REQUIRED` | `definition` berilmagan |
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
- Uysot vositalariga qo'shimcha o'zgaruvchilar (qo'ng'iroq boshida bir marta o'qiladi):
  `phone_last9` — `phone` ning oxirgi 9 raqami (Uysot `search` mijozni shunday topadi);
  kompaniyaning CRM sozlamalari (`PUT /api/settings/crm`) — `crm_ai_employee_id`, `crm_new_lead_status_id`,
  `crm_meeting_booked_status_id`, `crm_promise_to_pay_status_id`, `crm_not_interested_status_id`,
  `crm_callback_status_id`, `crm_task_type_id`. Kompaniya sozlamani saqlamagan bo'lsa, o'zgaruvchi yo'q
  (`{{...}}` o'rni almashtirilmay qoladi, 0 bo'lib ketmaydi) — shunday vositaning so'rovi Uysot tomonidan
  rad etilishi mumkin. Admin sinovida (`/test`) ular `variables` bilan qo'lda beriladi.
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

Vositani va barcha kompaniyalarning u haqidagi tanlovini o'chiradi (vaqtincha yashirish uchun —
`platformEnabled: false`). Yo'q `id` — 404.
Audit: `INTEGRATION_TOOL_DELETE`.

## `POST /api/platform/integration-tools/{id}/test`

HTTP vositani **admin o'z kompaniyasining Uysot hisobi** bilan bir marta ishga tushiradi va modelga
ko'rinadigan javobni qaytaradi. Tana: kompaniya vositasining testidagi kabi (`TestToolRequest`:
`variables`, `args`, `responseMapping`; hammasi ixtiyoriy). Javobda `result` bilan birga `raw` (Uysot javobi
o'zgarishsiz) va `mapped` (yuborilgan `responseMapping` natijasi) bor — maydonlarni tanlash uchun
([tools.md](tools.md) §4.1). **`WRITE` so'rov haqiqatan yuboriladi.**

| HTTP | `messageCode` | Sabab |
|---|---|---|
| 404 | `INTEGRATION_TOOL_NOT_FOUND` | `id` yo'q |
| 409 | `INTEGRATION_TOOL_TEST_NOT_CONNECTED` | admin kompaniyasi Uysot'ga ulanmagan |

Audit: `INTEGRATION_TOOL_TEST`.

## Uysot'ning tayyor HTTP vositalari (migratsiya `V19`)

V19 oltita o'qish vositasining HTTP egizagini (`*_LIVE`) ichki jufti yonida qo'shgan edi. V23–V26 dan keyin
ularning hammasi o'chirildi: ichki juftlarning o'zi o'sha model nomi bilan HTTP vositaga aylandi (pastda).

- Uysot javobining o'rami (`data` / `data.data` / `data.content`) hujjatlashtirilmagan, shu sabab har bir
  `responseMapping` uchala shaklni `||` bilan sinaydi. Admin "Sinash" da **Xom javob**ni ko'rib, kerak bo'lsa
  xaritani aniqlashtiradi.
- Ichki (kod) holda qoldi: `CREATE_LEAD`, `ADD_LEAD_NOTE`, `CREATE_FOLLOW_UP_TASK`, `MOVE_LEAD_STAGE` — yozuvchi
  vositalar, keyingi bosqichda HTTP'ga o'tadi. Uysot'ga tegmaydigan `REMEMBER_FLAT_INTEREST` va
  `REQUEST_MANAGER_CALLBACK` katalogdan olib tashlandi (migratsiya `V25`): ular endi agentning `EXTRACT_VARIABLE`
  funksiyalari ([ai-agents.md](ai-agents.md), `remember: true` — mijoz xotirasiga ham yoziladi).

## `SEARCH_FLATS` va `LIST_COMPLEXES` — endi HTTP (migratsiya `V23`)

Ikkalasi `kind=HTTP` ga o'tdi, **model nomi o'zgarmadi** (`searchFlats`, `listResidentialComplexes`) — agent
funksiyalari va ssenariy bosqichlari (`LOAD_COMPLEXES`) o'sha nom bilan ishlayveradi. Kod versiyasi va
`SEARCH_FLATS_LIVE` / `LIST_COMPLEXES_LIVE` egizaklari o'chirildi (kompaniyaning ular bo'yicha tanlovi ham).

| `key` | Model nomi | So'rov | `result` |
|---|---|---|---|
| `SEARCH_FLATS` | `searchFlats` | `POST v1/open-api/flat/filter` (`size=3`, eng arzoni birinchi) | `maxItems=3`, `emptyMessage`, `errorMessage`, `argumentVariables: {rooms: roomsNeeded, priceTo: budgetRange}`, `memoryFacts: {flats: offeredFlats}` |
| `LIST_COMPLEXES` | `listResidentialComplexes` | `GET v1/open-api/house/all` | `errorMessage` |

- Kod qilgan qo'shimcha ishlar endi vositaning o'z sozlamasi ([tools.md](tools.md) `result.argumentVariables`,
  `result.memoryFacts`): xona soni va byudjet lid izohiga, taklif qilingan xonadonlar mijoz xotirasiga tushadi.
- Farq: majmua endi nom bilan emas, `houseIds` bilan tanlanadi — model avval `listResidentialComplexes` dan id
  oladi. `listResidentialComplexes` sotuvda bo'lmagan majmuani ham qaytaradi (`isSale=false`); ta'rif modelga
  faqat `isSale=true` larini taklif qilishni aytadi. `flatSearch` xotira fakti endi yozilmaydi.

## `GET_CONTRACT_BALANCE` va `GET_PAYMENT_SCHEDULE` — endi HTTP (migratsiya `V24`)

Ikkalasi `kind=HTTP` ga o'tdi, model nomi o'zgarmadi (`getContractBalance`, `getPaymentSchedule`). Kod versiyasi
va `GET_CONTRACT_BALANCE_LIVE` / `GET_PAYMENT_SCHEDULE_LIVE` egizaklari o'chirildi.

| `key` | Model nomi | So'rov | Kod qilgan ish endi qayerda |
|---|---|---|---|
| `GET_CONTRACT_BALANCE` | `getContractBalance` | `POST v1/open-api/contract/filter` (`size=5`) | `search`: `"x-fallback": ["contractNumber", "phone_last9"]`; `responseMapping` → `contractNumber`, `responsibleEmployeeId`, `responsibleEmployeeName` (birinchi shartnomadan) qo'ng'iroq natijasiga |
| `GET_PAYMENT_SCHEDULE` | `getPaymentSchedule` | `GET v1/open-api/contract/{contractId}/monthly-payment` | `payments`: `$.data[?paid!=true&paidStatus!=PAID&status!=PAID]{…}`, `maxItems=3` |

Farqlar:
- `getContractBalance` endi keyingi to'lovni aytmaydi — model `getPaymentSchedule` ni chaqiradi.
- `getPaymentSchedule` shartnoma raqamini emas, `contractId` ni oladi (`getContractBalance` javobidagi `id`).
- Telefon bo'yicha bir nechta shartnoma chiqsa, kod eng ko'p kechikkanini tanlardi; endi model 5 tagacha
  shartnomani ko'radi va tavsifga ko'ra eng ko'p kechikkani haqida gapiradi. `responsibleEmployeeId` esa javobdagi
  **birinchi** shartnomadan olinadi.
- "Muddati o'tgan" belgisini kod qo'ymaydi — model sanani promptdagi bugungi sana bilan solishtiradi.

## `GET_CLIENT_PROFILE`, `GET_DEBT_SUMMARY`, `GET_BOOKING_STATUS` — endi HTTP (migratsiya `V26`)

Model nomlari o'zgarmadi. `GET_BOOKING_STATUS_LIVE` / `GET_DEBT_SUMMARY_LIVE` o'chirildi. Uchalasi ham mijozni
qo'ng'iroq o'zgaruvchisi `phone_last9` bo'yicha qidiradi.

| `key` | Model nomi | So'rov | Qo'ng'iroq natijasiga |
|---|---|---|---|
| `GET_CLIENT_PROFILE` | `getClientProfile` | `POST v1/open-api/lead/filter` (`phone`, `size=1`) | `lead`, `crmLeadId`, `crmResponsibleId` (lid mas'uli, `V30`) |
| `GET_DEBT_SUMMARY` | `getDebtSummary` | `POST v1/open-api/contract/filter` (`ACTIVE`/`STARTED`, `size=20`, modelga 3 tasi) | `contracts`, `responsibleEmployeeId`, `responsibleEmployeeName` |
| `GET_BOOKING_STATUS` | `getBookingStatus` | `POST v1/open-api/booking/filter` (`size=5`) | `bookings` |

Farqlar:
- `getClientProfile` endi faqat lidni qaytaradi (ism, id, holat). Shartnomalar — `getDebtSummary` da.
- `getDebtSummary` endi har shartnomaning muddati o'tgan to'lovlarini o'zi so'ramaydi — model kerak bo'lsa
  `getPaymentSchedule` ni shartnoma `id` si bilan chaqiradi. Tartiblash ham kodda emas: tavsif modelga eng ko'p
  kechikkanidan boshlashni aytadi.

## Lid yozuvchi vositalar — endi HTTP (migratsiya `V27`)

`CREATE_LEAD`, `ADD_LEAD_NOTE`, `CREATE_FOLLOW_UP_TASK`, `MOVE_LEAD_STAGE` endi `kind: "HTTP"`, model nomlari
o'zgarmadi. Hammasi `effect: "WRITE"`, `requiresConfirmation: false`. Ro'yxatda `BUILT_IN` vosita qolmadi.
Yangi qator: `LIST_LEAD_STAGES` (`listLeadStages`, `LEAD`/`READ`, sukut bo'yicha yoqilgan).

| `key` | Model nomi | So'rov | Qo'ng'iroqdan oladi |
|---|---|---|---|
| `CREATE_LEAD` | `createLead` | `POST v1/open-api/lead` (`name`, `pipeStatusId`, `responsibleById`, `contacts`) | `crm_new_lead_status_id`, `crm_ai_employee_id`, `phone` |
| `ADD_LEAD_NOTE` | `addLeadNote` | `POST v1/open-api/lead-note/{id}/list` | `crmLeadId` |
| `CREATE_FOLLOW_UP_TASK` | `createFollowUpTask` | `POST v1/open-api/lead-task/{id}/save` | `crmLeadId`, `crm_task_type_id`, `now_epoch`, `timezone`; `assignedById` — `crmResponsibleId`, bo'lmasa webhook'dagi `responsibleId`, bo'lmasa `crm_ai_employee_id` (`V30`) |
| `MOVE_LEAD_STAGE` | `moveLeadStage` | `POST v1/open-api/lead` (`id`, `pipeStatusId`) | `crmLeadId` |
| `LIST_LEAD_STAGES` | `listLeadStages` | `GET v1/open-api/pipe/all` | — |

Farqlar:
- Lid kodda qidirilmaydi: `crmLeadId` ni `getClientProfile` yozadi, shuning uchun u avval chaqiriladi.
- `createFollowUpTask` muddatni `yyyy-MM-dd HH:mm` mahalliy vaqtda oladi (`x-format: epochSeconds`); muddat
  `followUpTaskTime` o'zgaruvchisiga yoziladi.
- `moveLeadStage` endi bosqich **nomi** emas, `pipeStatusId` oladi — model uni `listLeadStages` dan topadi.
  `moveLeadStage` funksiyasi bor agentlarga migratsiya `listLeadStages` funksiyasini ham qo'shadi; o'rnatilgan
  ssenariylarning `MOVE_STAGE`/`REJECT_STAGE` bosqichlari `allowedTools: ["listLeadStages"]` oladi.
- `createLead` qo'ng'iroqda bir martadan ko'p chaqirilmaydi (`maxCallsPerCall: 1`).
