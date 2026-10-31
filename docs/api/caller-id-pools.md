# Caller ID Pools (Raqamlar puli va rotatsiya) API

Bu modul kompaniyalarga chiquvchi qo'ng'iroqlar uchun telefon raqamlari pulini (Caller ID Pool) yaratish,
turli rotatsiya strategiyalari (ROUND_ROBIN, RANDOM, LEAST_USED, HIGHEST_ANSWER_RATE) orqali raqamlarni
avtomatik almashtirish va spamlardan himoya qilish imkonini beradi.

**Pul a'zosi — kompaniyaning ro'yxatdan o'tgan raqami.** Pulga faqat kompaniyaning
[phone-numbers.md](phone-numbers.md) dagi raqami qo'shiladi (BYON yoki sotib olingan). Kampaniya puldan
raqam tanlaganda qo'ng'iroq **shu raqamning o'z trunki** (`providerTrunkId`) orqali ketadi. Raqam faqat trunki kompaniyaning
yoqilgan, ishlayotgan va bo'sh kanali bor trunki bo'lsa tanlanadi; bunday raqam bo'lmasa target 5 daqiqaga
kutadi (urinish hisoblanmaydi) — boshqa raqam yoki platformaning raqami bilan qo'ng'iroq qilinmaydi.
Hozircha faqat AI dialer shunday ishlaydi; predictive (operator) kampaniyalar pulni ishlatmaydi.

> Bozordan sotib olingan raqamning trunki platformaniki, shuning uchun u puldan hali tanlanmaydi.

---

## Endpointlar

### 1. Pul yaratish
`POST /api/caller-id-pools`  
Ruxsat: `SIP_TRUNK_EDIT`

**Request:**
```json
{
  "name": "Toshkent mijozlari puli",
  "strategy": "ROUND_ROBIN",
  "description": "Markaziy hudud uchun rotatsiya qilinadigan raqamlar",
  "initialPhones": [
    "+998712001122",
    "+998712001123"
  ]
}
```

`initialPhones` dagi har bir raqam §6 dagi qoidaga bo'ysunadi; bittasi o'tmasa, pul ham yaratilmaydi.

### 2. Pullar ro'yxatini olish
`GET /api/caller-id-pools`  
Ruxsat: `SIP_TRUNK_READ`

### 3. Pul tafsilotlarini olish
`GET /api/caller-id-pools/{id}`  
Ruxsat: `SIP_TRUNK_READ`

### 4. Pulni yangilash
`PUT /api/caller-id-pools/{id}`  
Ruxsat: `SIP_TRUNK_EDIT`

### 5. Pulni o'chirish
`DELETE /api/caller-id-pools/{id}`  
Ruxsat: `SIP_TRUNK_EDIT`

### 6. Pulga yangi raqam qo'shish
`POST /api/caller-id-pools/{id}/numbers`  
Ruxsat: `SIP_TRUNK_EDIT`

**Request:**
```json
{
  "phone": "+998712001124",
  "label": "Zaxira raqam"
}
```

`phone` normallashtiriladi va kompaniyaning ro'yxatdan o'tgan raqami bo'lishi shart.

| Xato | Qachon |
|---|---|
| `400 PHONE_INVALID` | Raqam noto'g'ri |
| `404 CALLER_ID_POOL_NOT_FOUND` | Pul yo'q yoki boshqa kompaniyaniki |
| `409 CALLER_ID_NUMBER_NOT_REGISTERED` | Raqam kompaniyaning telefon raqamlari orasida yo'q — avval `POST /api/phone-numbers/byo` (yoki sotib olish) |

### 7. Raqamni puldan o'chirish
`DELETE /api/caller-id-pools/{id}/numbers/{numberId}`  
Ruxsat: `SIP_TRUNK_EDIT`

### 8. Raqamni dam olishga (cooldown) qo'yish
`POST /api/caller-id-pools/{id}/numbers/{numberId}/cooldown?hours=24`  
Ruxsat: `SIP_TRUNK_EDIT`

### 9. Raqamni faollashtirish
`POST /api/caller-id-pools/{id}/numbers/{numberId}/activate`  
Ruxsat: `SIP_TRUNK_EDIT`

### 10. Raqamni o'chirib qo'yish (disable)
`POST /api/caller-id-pools/{id}/numbers/{numberId}/disable`  
Ruxsat: `SIP_TRUNK_EDIT`
