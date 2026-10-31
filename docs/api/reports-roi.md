# ROI hisoboti API

`uz.murodjon.robotcallv2.report` · huquq: **REPORT_READ**

Rahbar uchun bitta sahifa: davr ichida robot nima qildi, qancha turdi va xuddi shu qo'ng'iroqlarni
operatorlar qilganda qancha turardi. Masalan: "Bu oy robot 120 mln so'm undirdi, 3 operatorning
ishini qildi, bitta javob berilgan qo'ng'iroq 400 so'm turdi".

Umumiy javob formati va xatolar uchun [README.md](README.md)ga qarang.

---

## `GET /api/reports/roi`

| Parametr | Majburiy | Izoh |
|---|---|---|
| `from` | ❌ | ISO instant yoki `YYYY-MM-DD` (kompaniya vaqt zonasida); standart — `to` dan 7 kun oldin |
| `to` | ❌ | Xuddi shunday; standart — hozir. Davr ko'pi bilan 366 kun |
| `campaignId` | ❌ | Faqat shu kampaniya qo'ng'iroqlari |
| `operatorHourlyCostUzs` | ❌ | Kompaniyaning o'z operator soati narxi (maosh + soliq); standart — platforma sozlamasi |

**Response** (`RoiReportResponse`):

```json
{
  "accept": true,
  "data": {
    "from": "2026-09-30T19:00:00Z",
    "to": "2026-10-31T19:00:00Z",
    "totals": {
      "totalCalls": 12000, "answeredCalls": 5200, "talkSeconds": 468000,
      "spentUzs": 2080000,
      "promises": 900, "promisedAmountUzs": 310000000,
      "conversions": 430, "conversionValueUzs": 121500000
    },
    "figures": {
      "costPerAnsweredCallUzs": 400, "costPerPromiseUzs": 2311,
      "operatorHours": 280.0, "operatorCostUzs": 8400000, "savedUzs": 6320000,
      "operatorEquivalent": 1.5, "roiPercent": 5741.3
    },
    "assumptions": {
      "operatorHourlyCostUzs": 30000, "operatorSecondsPerAttempt": 45, "operatorHoursPerMonth": 176
    }
  }
}
```

### Qanday hisoblanadi

Hamma ko'rsatkich **bitta qo'ng'iroqlar to'plami** bo'yicha hisoblanadi: davr ichida boshlangan
qo'ng'iroqlar (`call_attempt.started_at`). Va'da yoki to'lov qachon xabar qilinganidan qat'i
nazar, uni yutgan qo'ng'iroq davriga yoziladi.

| Maydon | Izoh |
|---|---|
| `totals.spentUzs` | Shu qo'ng'iroqlar uchun yechilgan pul (`call_billing`, `SETTLED`) |
| `totals.promises` / `promisedAmountUzs` | Qo'ng'iroqlarda olingan to'lov va'dalari |
| `totals.conversions` / `conversionValueUzs` | CRM xabar qilgan va shu qo'ng'iroqlarga bog'langan konversiyalar ([conversions.md](conversions.md)) — **haqiqiy tushgan pul** |
| `figures.operatorHours` | `(talkSeconds + totalCalls × operatorSecondsPerAttempt) / 3600` — operator javob berilmagan qo'ng'iroqqa ham vaqt sarflaydi |
| `figures.operatorCostUzs` | `operatorHours × operatorHourlyCostUzs` |
| `figures.savedUzs` | `operatorCostUzs − spentUzs`; robot qimmatroq bo'lsa manfiy |
| `figures.operatorEquivalent` | Davr davomida nechta to'liq stavkali operator ishi: `operatorHours / (operatorHoursPerMonth × davr_kunlari / 30)` |
| `figures.roiPercent` | `(conversionValueUzs − spentUzs) / spentUzs × 100`; hech narsa sarflanmagan bo'lsa `null` |
| `figures.costPerAnsweredCallUzs`, `costPerPromiseUzs` | Bo'luvchi `0` bo'lsa `null` |

ROI daromadi **faqat konversiyalardan** olinadi: va'da hali tushgan pul emas, shuning uchun u daromadga qo'shilmaydi.

`assumptions` javobda qaytadi: tejash shu farazlarga bog'liq, UI ularni ko'rsatishi va
`operatorHourlyCostUzs` ni o'zgartirish imkonini berishi kerak.

### Sozlamalar (`voice-agent.report.roi.*`)

| Kalit | Env | Standart |
|---|---|---|
| `operator-hourly-cost-uzs` | `REPORT_ROI_OPERATOR_HOURLY_COST_UZS` | `30000` |
| `operator-seconds-per-attempt` | `REPORT_ROI_OPERATOR_SECONDS_PER_ATTEMPT` | `45` |
| `operator-hours-per-month` | `REPORT_ROI_OPERATOR_HOURS_PER_MONTH` | `176` |

### Xatolar

| Status | `messageCode` | Qachon |
|---|---|---|
| `400` | `DATE_RANGE_INVALID` | `from` `to` dan keyin |
| `400` | `DATE_RANGE_TOO_LONG` | Davr 366 kundan uzun |
| `400` | `REPORT_OPERATOR_COST_INVALID` | `operatorHourlyCostUzs` ≤ 0 |
