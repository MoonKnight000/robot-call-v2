# Parallel qo'ng'iroqlar (concurrency) API

`uz.murodjon.robotcallv2.dialer` · huquq: **LIVE_READ**; limitni o'rnatish — **PLATFORM_ADMIN**
([companies.md](companies.md#put-apicompaniesidplatform--kompaniyani-platforma-tomonidan-tahrirlash))

Dialer bir vaqtda ushlab turadigan qo'ng'iroqlar uchta chegara bilan cheklanadi; eng kichigi ishlaydi:

| Chegara | Qayerdan | Izoh |
|---|---|---|
| Platforma | `voice-agent.dialer.max-concurrent-calls` va klasterdagi RTP portlar soni | Hamma kompaniyalar uchun umumiy |
| **Kompaniya** | `company.max_concurrent_calls` (tarif); `null` bo'lsa — `voice-agent.dialer.max-concurrent-calls-per-company` | Shu sahifa |
| Kampaniya | kampaniyaning `maxConcurrentCalls` i ([campaigns.md](campaigns.md)) | `0` — o'z chegarasi yo'q |
| **SIP trunk** | trunkning `maxChannels` i ([sip-trunks.md](sip-trunks.md)) — provayder bergan kanal soni | `null` — cheklanmaydi. To'lgan trunk o'tkazib yuboriladi, hammasi to'lsa dialer kutadi |

Kompaniya limiti `AI` va `PREDICTIVE` kampaniyalarning dialer qo'ng'iroqlariga qo'llanadi. Kiruvchi
qo'ng'iroqlar, `PREVIEW`/`PROGRESSIVE` operator qo'ng'iroqlari va qo'lda boshlangan qo'ng'iroqlar
bu hisobga kirmaydi.

Limitga yetilganda dialer yangi qo'ng'iroq boshlamaydi (slot bo'shashini kutadi) va kompaniyaga
`CONCURRENCY_LIMIT_REACHED` bildirishnomasi yuboradi — soatiga ko'pi bilan bir marta
([notifications.md](notifications.md)).

---

## `GET /api/concurrency` — limit va band slotlar

**Response** (`ConcurrencyResponse`):

```json
{
  "accept": true,
  "data": {
    "limit": 40,
    "platformDefault": false,
    "active": 37,
    "available": 3
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `limit` | `number` | Kompaniya bir vaqtda ushlab tura oladigan dialer qo'ng'iroqlari soni |
| `platformDefault` | `boolean` | `true` — kompaniyaga alohida limit o'rnatilmagan, platforma standarti ishlayapti |
| `active` | `number` | Hozir dialer kompaniya uchun ushlab turgan qo'ng'iroqlar (jiringlayotgan va suhbatdagilar) |
| `available` | `number` | Bo'sh slotlar; limitga yetilganda `0` |

Xuddi shu `active`/`limit` [billing overview](billing.md#1-get-apibillingoverview--hisob-holati-va-limitlar)
dagi `metrics.concurrentChannels` da ham ko'rinadi.

---

## `GET /api/concurrency/trunks` — trunklar kanal soni

Kompaniyaning har bir yoqilgan SIP trunki: o'rnatilgan `maxChannels`, hozir band kanallar va
dialer qo'ng'iroqlardan **o'zi aniqlagan** kanal soni.

SIP protokolida provayderdan kanal sonini so'rab bo'lmaydi, shuning uchun u qo'ng'iroqlardan
o'rganiladi:
- ulangan qo'ng'iroq tugaganda shu trunkda nechta qo'ng'iroq bo'lgani yoziladi — `peakConnected`
  (trunk shuncha qo'ng'iroqni ko'tara olishi isbotlangan);
- provayder qo'ng'iroqni "kanal yo'q" deb rad etsa (Q.850 `34` — SIP `503`, `42`, `44`, `47`),
  o'sha paytdagi boshqa qo'ng'iroqlar soni — `congestionLimit`.

Ma'lumot Redis'da 30 kun saqlanadi (butun klaster uchun umumiy). Faqat dialer (`AI` kampaniya)
qo'ng'iroqlari hisoblanadi: shu trunkdan kiruvchi yoki qo'lda qilingan qo'ng'iroqlar ham kanal
egallaydi, bunday trunkda provayder hisoblangandan ertaroq rad etishi mumkin.

**Response** (`List<TrunkCapacityRow>`):

```json
{
  "accept": true,
  "data": [
    {
      "sipTrunkId": 3,
      "name": "Uzmobile",
      "maxChannels": null,
      "active": 9,
      "peakConnected": 10,
      "congestionLimit": 10,
      "lastCongestionAt": "2026-10-14T08:12:44Z",
      "suggestedMaxChannels": 10
    }
  ]
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `maxChannels` | `number \| null` | Trunkka o'rnatilgan limit; `null` — yo'q |
| `active` | `number` | Hozir shu trunk orqali dialer qo'ng'iroqlari |
| `peakConnected` | `number` | Bir vaqtda ko'rilgan eng ko'p qo'ng'iroq (ulangan qo'ng'iroq paytida); `0` — hali yo'q |
| `congestionLimit` | `number \| null` | Provayder oxirgi marta "kanal yo'q" deganda nechta qo'ng'iroq bor edi; `null` — hech qachon |
| `lastCongestionAt` | `string \| null` | O'sha payt |
| `suggestedMaxChannels` | `number \| null` | `maxChannels` uchun tavsiya: `max(congestionLimit, peakConnected)`; rad etish ko'rilmaguncha `null` (kanal soni kamida `peakConnected`) |

UI: `suggestedMaxChannels` bor va `maxChannels` undan farq qilsa — "Bu trunk taxminan N kanalli
ko'rinadi. Limitni N ga qo'yaymi?" tavsiyasi, tasdiqlansa `PUT /api/sip-trunks/{id}` bilan
`maxChannels` yoziladi.
