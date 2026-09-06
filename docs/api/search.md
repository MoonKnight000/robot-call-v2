# Qidiruv (command palette) API

`uz.murodjon.robotcallv2.search` · huquq: talab qilinmaydi (kirgan bo'lsa yetarli) ·
UI-DESIGN §11.9, §9

`⌘K` command palette'ning ma'lumot manbasi. "Sahifalar" va "Buyruqlar"
guruhlari klient tomonda (statik navigatsiya/buyruq ro'yxati) — bu endpoint
faqat haqiqiy ma'lumotga tayangan ikkita guruhni qaytaradi.

---

## `GET /api/search?q=`

Joriy kompaniya bo'yicha, har guruhda eng ko'p 5 ta natija.

**Javob** (`ResponseData<SearchResult>`) — har bir element `SearchItem`:
`{ id, title, subtitle }` (`label`/`context` **emas**):

```json
{
  "accept": true,
  "data": {
    "campaigns": [ { "id": 3, "title": "Qarzdorlik iyul", "subtitle": "ACTIVE" } ],
    "calls": [ { "id": 812, "title": "998901234567", "subtitle": "PROMISE_TO_PAY" } ]
  },
  "message": null,
  "messageCode": null,
  "errors": null
}
```

`q` bo'sh bo'lsa — `400`. Kampaniyalar nomi bo'yicha, qo'ng'iroqlar
telefon raqami bo'yicha qidiriladi (mavjud
[`CallFilter.q`](reports.md)ning aynan o'zi).
