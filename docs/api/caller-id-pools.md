# Caller ID Pools (Raqamlar puli va rotatsiya) API

Bu modul kompaniyalarga chiquvchi qo'ng'iroqlar uchun telefon raqamlari pulini (Caller ID Pool) yaratish,
turli rotatsiya strategiyalari (ROUND_ROBIN, RANDOM, LEAST_USED, HIGHEST_ANSWER_RATE) orqali raqamlarni
avtomatik almashtirish va spamlardan himoya qilish imkonini beradi.

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
