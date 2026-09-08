# MCP serverlari

`uz.murodjon.robotcallv2.mcp` · huquq: **AI_AGENT_READ** / **AI_AGENT_EDIT**

Kompaniya boshqa joyda ishlatayotgan MCP serverini ulash — uning tool'lari qo'ng'iroq
davomida AI agentga beriladi.

**[tools.md](tools.md) dan farqi — kim tavsiflaydi.** U yerdagi tool bitta REST endpoint
bo'lib, kompaniya har bir maydonini qo'lda tavsiflaydi. MCP server esa o'z tool'larini
o'zi tavsiflaydi, ya'ni server allaqachon bor bo'lsa, URL joylashning o'zi kifoya.

Umumiy javob formati va xatolar uchun [README.md](README.md)ga qarang.

---

## 1. Ikkita muhim qoida

**Server saqlash paytidayoq so'raladi.** `POST`/`PUT` javobi allaqachon `status`,
`toolCount` va `usableToolCount` bilan qaytadi. Noto'g'ri URL yoki rad etilgan token —
formadagi xabar, qo'ng'iroqda jimgina paydo bo'lmaydigan tool emas.

**Tool ro'yxati bazada saqlanadi.** Qo'ng'iroq shu qatordan o'qiydi, serverga bormaydi:
mijoz go'shakni ko'targani bilan agent gapirgani orasiga birovning serveri kutishini
qo'yish mumkin emas. Server tool'lari o'zgarsa — `refresh`.

---

## 2. Qaysi tool modelga beriladi

Model tirik telefon suhbatida, istalgan narsani ayta oladigan notanish odam bilan
gaplashadi. Shuning uchun qoida tool nima uchun kerakligi haqida emas, ko'ndirilgan model
nima qila olishi haqida:

| Server nima deb belgilagan | Beriladimi |
|---|---|
| `destructiveHint: true` (o'chiradi, qaytaradi) | **Hech qachon**, `allowWrites` yoqilgan bo'lsa ham |
| `readOnlyHint: true` (o'qiydi) | **Doim** |
| Ikkalasi ham yo'q (yozadi, lekin buzmaydi — masalan, uchrashuv band qilish) | Faqat `allowWrites: true` bo'lsa |

"Ishonchingiz komilmi?" degan hech qanday matn qat'iyatli qo'ng'iroq qiluvchidan omon
qolmaydi, birovning tizimidagi o'chirishni bu platforma orqaga qaytara olmaydi.

Filtr **prompt yasalishidan oldin** ishlaydi: model bilmagan tool'ni chaqirishga
ko'ndirib bo'lmaydi. `usableToolCount` — aynan agentga yetib borgan son;
`toolCount` bilan farqi katta bo'lsa, `allowWrites` bo'yicha qaror kerak.

---

## 3. Xavfsizlik

- **Faqat HTTPS.** Oddiy `http://` rad etiladi (ogohlantirish bilan o'tkazilmaydi):
  bearer token har so'rovda sarlavhada ketadi.
- Manzil `PublicUrlGuard` dan o'tadi — loopback, xususiy tarmoq, link-local va IPv6
  unique-local manzillar rad etiladi. Tekshiruv **har so'rovda** takrorlanadi.
- **Redirect kuzatilmaydi** — 302 tekshiruvdan keyin boshqa hostga o'tish yo'li.
- Token qatorda saqlanmaydi: `secretKey` kompaniya secret'ining **nomi**
  ([secrets.md](secrets.md)), qiymat esa o'sha yerda qoladi.
- Javob 256 KB gacha o'qiladi, modelga 4000 belgigacha beriladi.

---

## 4. Endpointlar

| Metod | Yo'l | Izoh |
|---|---|---|
| `GET` | `/api/mcp/connections` | Ro'yxat |
| `POST` | `/api/mcp/connections` | Yaratish + darhol so'rash |
| `PUT` | `/api/mcp/connections/{id}` | Yangilash + qayta so'rash |
| `DELETE` | `/api/mcp/connections/{id}` | O'chirish |
| `POST` | `/api/mcp/connections/{id}/refresh` | Tool ro'yxatini qayta o'qish |
| `POST` | `/api/mcp/connections/{id}/agents/{agentId}` | Agentga biriktirish |
| `DELETE` | `/api/mcp/connections/{id}/agents/{agentId}` | Agentdan ajratish |

### Request (`McpConnectionRequest`)
```json
{
  "name": "Uysot CRM",
  "url": "https://mcp.uysot.uz/mcp",
  "authType": "BEARER",
  "secretKey": "UYSOT_MCP_TOKEN",
  "allowWrites": false
}
```

| Maydon | Turi | Majburiymi | Izoh |
|---|---|---|---|
| `name` | `string` | ✅ | Kompaniya ichida unikal (`409 MCP_CONNECTION_NAME_EXISTS`) |
| `url` | `string` | ✅ | HTTPS, ommaviy manzil (`400 MCP_URL_INVALID`) |
| `authType` | `string` | ❌ | `NONE` (standart) yoki `BEARER` |
| `secretKey` | `string` | ❌ | `BEARER` uchun secret **nomi**, tokenning o'zi emas |
| `allowWrites` | `boolean` | ❌ | Standart `false` |

### Response (`McpConnectionRow`)
```json
{
  "accept": true,
  "data": {
    "id": 1,
    "name": "Uysot CRM",
    "url": "https://mcp.uysot.uz/mcp",
    "authType": "BEARER",
    "secretKey": "UYSOT_MCP_TOKEN",
    "status": "CONNECTED",
    "toolCount": 12,
    "usableToolCount": 5,
    "allowWrites": false,
    "lastError": null,
    "refreshedAt": "2026-09-07T10:00:00Z",
    "createdAt": "2026-09-07T10:00:00Z"
  },
  "message": null, "messageCode": null, "errors": null
}
```

| `status` | Ma'nosi |
|---|---|
| `PENDING` | Saqlangan, hali gaplashilmagan |
| `CONNECTED` | Server javob berdi, tool'lari o'qildi |
| `AUTH_REQUIRED` | 401/403 — token yo'q, noto'g'ri yoki muddati o'tgan |
| `ERROR` | Yetib bo'lmadi yoki yaroqsiz javob; sabab `lastError` da |
| `DISABLED` | Kompaniya o'chirgan |

> ℹ️ Ulanish xatosi **javobda 502 sifatida emas, qatordagi `status`/`lastError` da**
> qaytadi. Operator sababni ulanishlar ekranida o'qishi kerak; POST'dan chiqqan 502 buni
> bir marta aytadi va yo'qoladi.

---

## 5. Qo'ng'iroqda

Tool modelga `<ulanish nomi>__<tool nomi>` ko'rinishida beriladi — ikki server bir xil
`search` tool'ini taklif qilishi mumkin, bir xil nomli ikki tool berilgan model esa
xohlaganini chaqiradi.

Har chaqiruv `mcp_tool_execution` ga yoziladi: `latency_ms`, `status`, qisqartirilgan
argument va javob. Birovning serveri sekin javob bersa, bu telefondagi odam uchun
sukunat — shu jadval buni bahslashmasdan ko'rsatadi.

Tool ishlamasa **exception tashlanmaydi**: model "Tool is unavailable." matnini oladi va
suhbatni davom ettiradi. Ishlamayotgan bitta server qo'ng'iroqni to'xtatmaydi va boshqa
serverlarning tool'larini ham yo'qotmaydi.

---

## 6. Cheklovlar

- Faqat **streamable-HTTP** transport (JSON-RPC POST). SSE obuna, stdio va OAuth oqimi
  yo'q — token oldindan berilgan bo'lishi kerak.
- MCP **resource** va **prompt** turlari o'qilmaydi, faqat `tools`.
- Tool ro'yxati avtomatik yangilanmaydi — `refresh` qo'lda chaqiriladi.
