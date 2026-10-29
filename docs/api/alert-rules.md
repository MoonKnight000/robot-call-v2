# Alert qoidalari (`/api/alert-rules`)

Kompaniya o'z qo'ng'iroqlari uchun chegara qo'yadi: masalan "oxirgi 60 daqiqada muvaffaqiyat
darajasi 40% dan tushsa" yoki "30 daqiqada birorta ham qo'ng'iroq bo'lmasa". Server yoqilgan
qoidalarni **har 5 daqiqada** (`voice-agent.alert-rules.check-ms`, default `300000`) baholaydi.
Qoida buzilsa, kompaniyaga `ALERT_TRIGGERED` bildirishnomasi yuboriladi
([notifications.md](notifications.md)):

- panel'dagi bell'ga — shu turni o'chirmagan har bir faol foydalanuvchiga;
- kompaniya [sozlamalarida](settings.md) `ALERT_TRIGGERED` uchun yoqilgan kanallarga (Telegram, email, webhook).

Bildirishnoma sarlavhasi — qoida nomi (`name`). Matni quyidagicha bo'ladi:
`"Muvaffaqiyat darajasi: 32% (oxirgi 60 daqiqada, 54 ta qo'ng'iroq). Chegara: 40% dan past."`

Qoida yuborilgandan keyin `cooldownMinutes` davomida jim turadi, hatto chegara hali ham
buzilgan bo'lsa ham — bitta muammo uchun har 5 daqiqada xabar kelmasligi uchun. Holat
tiklanganda alohida "tiklandi" xabari **yuborilmaydi**.

Faqat AI agent olib borgan qo'ng'iroqlar hisoblanadi; operator qo'ng'iroqlari kirmaydi.

| Metod | Yo'l | Huquq | Vazifasi |
|---|---|---|---|
| `POST` | `/api/alert-rules` | `ALERT_EDIT` | Qoida yaratish |
| `POST` | `/api/alert-rules/filter` | `ALERT_READ` | Ro'yxat (sahifalangan) |
| `GET` | `/api/alert-rules/{id}` | `ALERT_READ` | Bitta qoida |
| `PUT` | `/api/alert-rules/{id}` | `ALERT_EDIT` | Sozlamalarni to'liq almashtirish |
| `DELETE` | `/api/alert-rules/{id}` | `ALERT_EDIT` | O'chirish |
| `GET` | `/api/alert-rules/{id}/preview` | `ALERT_READ` | Hozirgi qiymat va qoida ishlab ketadimi — hech narsa yubormaydi |

---

## 1. Metrikalar (`AlertMetric`)

Har bir metrika qoida oynasida (`windowMinutes`) **tugagan** qo'ng'iroqlar bo'yicha hisoblanadi.
Ulush (rate) — foizda, 0–100.

| Qiymat | Nima | Namuna (`sampleSize`) | `minCalls` qo'llanadimi |
|---|---|---|---|
| `SUCCESS_RATE` | Konversiyalar ulushi: `PROMISE_TO_PAY`, `COMPLETED`, `GOAL_REACHED` — hisobotlardagi ta'rif | tugagan qo'ng'iroqlar | ha |
| `TRANSFER_RATE` | Operatorga o'tkazilganlar (`TRANSFERRED`) ulushi | tugagan qo'ng'iroqlar | ha |
| `FAILED_CALL_RATE` | Texnik xato (`FAILED`) va operator rad etgan (`CARRIER_REJECTED`) qo'ng'iroqlar ulushi | tugagan qo'ng'iroqlar | ha |
| `NEGATIVE_SENTIMENT_RATE` | Tahlil mijozni `NEGATIVE` yoki `HOSTILE` deb baholagan qo'ng'iroqlar ulushi | tahlili bor qo'ng'iroqlar | ha |
| `AVG_TURN_LATENCY_MS` | Mijoz gapini tugatgandan bot javob bergungacha o'rtacha vaqt, ms | latency o'lchangan qo'ng'iroqlar | ha |
| `TOOL_ERROR_COUNT` | Qo'ng'iroq paytida 2xx qaytarmagan tool so'rovlari soni (panel'dagi "Test" tugmasi kirmaydi) | barcha tool so'rovlari | yo'q |
| `CALL_COUNT` | Tugagan qo'ng'iroqlar soni — `BELOW` bilan "dialer yoki trunk to'xtab qoldi" degan signal | — | yo'q |

`minCalls` qo'llanadigan metrika oynada `minCalls` dan kam qo'ng'iroq bo'lsa baholanmaydi —
3 ta qo'ng'iroqdan 0% chiqishi hech narsani anglatmaydi. Oyna bo'sh bo'lsa (`value: null`)
qoida ishlamaydi. `CALL_COUNT` va `TOOL_ERROR_COUNT` bundan mustasno: ular uchun bo'sh
oyna — `0` qiymati.

`AlertComparator`: `ABOVE` — qiymat chegaradan **katta** bo'lsa ishlaydi, `BELOW` — **kichik** bo'lsa.
Chegaraga teng qiymat qoidani ishga tushirmaydi.

---

## 2. Qoida yaratish / almashtirish

```http
POST /api/alert-rules
PUT  /api/alert-rules/{id}
```

```json
{
  "name": "Qarz undirish — muvaffaqiyat tushdi",
  "metric": "SUCCESS_RATE",
  "comparator": "BELOW",
  "threshold": 40,
  "windowMinutes": 60,
  "minCalls": 20,
  "cooldownMinutes": 120,
  "aiAgentId": 1,
  "enabled": true
}
```

| Maydon | Turi | Majburiy | Cheklov / default |
|---|---|---|---|
| `name` | string | ha | 1–100 belgi; bildirishnoma sarlavhasi |
| `metric` | `AlertMetric` | ha | §1 |
| `comparator` | `ABOVE` \| `BELOW` | ha | |
| `threshold` | number | ha | `>= 0`; ulush metrikasida `0–100` |
| `windowMinutes` | int | ha | 5–1440 |
| `minCalls` | int | yo'q | 1–10000, default `10` |
| `cooldownMinutes` | int | yo'q | 5–10080 (7 kun), default `60` |
| `aiAgentId` | long | yo'q | `null` — barcha agentlar; berilsa — kompaniyaning o'z agenti bo'lishi shart |
| `enabled` | boolean | yo'q | default `true` |

`PUT` — to'liq almashtirish: yuborilmagan ixtiyoriy maydon default'ga qaytadi. Baholash holati
(`lastValue`, `lastEvaluatedAt`, `lastTriggeredAt`) saqlanib qoladi, shuning uchun cooldown
tahrirlashdan keyin ham davom etadi.

Bir kompaniyada ko'pi bilan **20 ta** qoida bo'lishi mumkin.

**Javob** — `ResponseData<AlertRule>`:

```json
{
  "success": true,
  "data": {
    "id": 5,
    "companyId": 1,
    "name": "Qarz undirish — muvaffaqiyat tushdi",
    "metric": "SUCCESS_RATE",
    "comparator": "BELOW",
    "threshold": 40.0,
    "windowMinutes": 60,
    "minCalls": 20,
    "cooldownMinutes": 120,
    "aiAgentId": 1,
    "enabled": true,
    "lastValue": 52.3,
    "lastEvaluatedAt": "2026-10-09T10:05:00Z",
    "lastTriggeredAt": null,
    "createdAt": "2026-10-09T09:00:00Z"
  }
}
```

`lastValue` — oxirgi baholashdagi qiymat, baholanadigan narsa bo'lmasa `null`.
`lastTriggeredAt` — oxirgi marta bildirishnoma yuborilgan vaqt.

---

## 3. Ro'yxat

```http
POST /api/alert-rules/filter
```

```json
{ "page": 0, "size": 20, "orders": { "CREATED_AT": "DESC" }, "metric": "SUCCESS_RATE", "enabled": true }
```

`metric` va `enabled` — ixtiyoriy filtrlar. Saralash ustunlari (`AlertRuleTableField`):
`CREATED_AT` (default, `DESC`), `NAME`, `LAST_TRIGGERED_AT`.
Javob — `ResponseData<PageableData<AlertRule>>`.

---

## 4. Preview

```http
GET /api/alert-rules/{id}/preview
```

Qoidani hozir baholaydi: chegara to'g'ri qo'yilganini sozlash paytida tekshirish uchun.
Bildirishnoma yubormaydi va `last*` maydonlarini o'zgartirmaydi. Cooldown hisobga olinmaydi.

```json
{
  "success": true,
  "data": {
    "value": 32.1,
    "sampleSize": 54,
    "breached": true,
    "message": "Muvaffaqiyat darajasi: 32.1% (oxirgi 60 daqiqada, 54 ta qo'ng'iroq). Chegara: 40% dan past."
  }
}
```

`value` va `message` oyna bo'sh bo'lsa `null`.

---

## 5. Xatolar

| Status | `messageCode` | Qachon |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Majburiy maydon yo'q yoki `name` 100 belgidan uzun |
| 400 | `ALERT_RULE_INVALID` | Chegara, oyna, cooldown yoki `minCalls` ruxsat etilgan oraliqdan tashqarida — har bir muammo `errors` ro'yxatida |
| 404 | `ALERT_RULE_NOT_FOUND` | Qoida yo'q yoki boshqa kompaniyaniki |
| 404 | `AI_AGENT_NOT_FOUND` | `aiAgentId` kompaniyaning agenti emas |
| 409 | `ALERT_RULE_LIMIT_REACHED` | Kompaniyada allaqachon 20 ta qoida bor |
