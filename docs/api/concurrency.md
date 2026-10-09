# Parallel qo'ng'iroqlar (concurrency) API

`uz.murodjon.robotcallv2.dialer` · huquq: **LIVE_READ**; limitni o'rnatish — **PLATFORM_ADMIN**
([companies.md](companies.md#put-apicompaniesidconcurrency-limit--parallel-qongiroqlar-limiti))

Dialer bir vaqtda ushlab turadigan qo'ng'iroqlar uchta chegara bilan cheklanadi; eng kichigi ishlaydi:

| Chegara | Qayerdan | Izoh |
|---|---|---|
| Platforma | `voice-agent.dialer.max-concurrent-calls` va klasterdagi RTP portlar soni | Hamma kompaniyalar uchun umumiy |
| **Kompaniya** | `company.max_concurrent_calls` (tarif); `null` bo'lsa — `voice-agent.dialer.max-concurrent-calls-per-company` | Shu sahifa |
| Kampaniya | kampaniyaning `maxConcurrentCalls` i ([campaigns.md](campaigns.md)) | `0` — o'z chegarasi yo'q |

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
