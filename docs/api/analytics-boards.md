# Analytics Boards (Moslashtiriladigan Analitika Panellari) API

Kompaniyalarga o'zlarining shaxsiy analitika panellarini (Custom Boards) yaratish, ularga vidjetlar
(KPI kartalar, chiziqli grafiklar, bar chart, taqsimot) biriktirish va umumiy analitika so'rovlarini
bajarish imkonini beruvchi API.

---

## Endpointlar

### 1. Panel yaratish
`POST /api/reports/boards`  
Ruxsat: `REPORT_EDIT`

**Request:**
```json
{
  "name": "Kollektor Boshqaruvi",
  "description": "Kunlik PTP va undirish ko'rsatkichlari",
  "isDefault": true
}
```

### 2. Panellar ro'yxati
`GET /api/reports/boards`  
Ruxsat: `REPORT_READ`

### 3. Panel tafsilotlarini olish
`GET /api/reports/boards/{id}`  
Ruxsat: `REPORT_READ`

### 4. Panelni yangilash
`PUT /api/reports/boards/{id}`  
Ruxsat: `REPORT_EDIT`

### 5. Panelni o'chirish
`DELETE /api/reports/boards/{id}`  
Ruxsat: `REPORT_EDIT`

### 6. Panelga vidjet qo'shish
`POST /api/reports/boards/{id}/widgets`  
Ruxsat: `REPORT_EDIT`

**Request:**
```json
{
  "title": "Muvaffaqiyat ulushi",
  "widgetType": "LINE_CHART",
  "metric": "SUCCESS_RATE",
  "groupBy": "DATE",
  "timeRange": "LAST_7_DAYS",
  "gridRow": 0,
  "gridCol": 0,
  "gridWidth": 6,
  "gridHeight": 4
}
```

### 7. Vidjetni o'chirish
`DELETE /api/reports/boards/{id}/widgets/{widgetId}`  
Ruxsat: `REPORT_EDIT`

### 8. Moslashuvchan analitika so'rovi (Query)
`POST /api/reports/boards/query`  
Ruxsat: `REPORT_READ`

**Request:**
```json
{
  "metric": "CALL_COUNT",
  "groupBy": "AGENT",
  "from": "2026-10-01T00:00:00Z",
  "to": "2026-10-09T23:59:59Z"
}
```

**Javob:**
```json
{
  "accept": true,
  "data": {
    "metric": "CALL_COUNT",
    "groupBy": "AGENT",
    "data": [
      {
        "key": "Kollektor Agent 1",
        "label": "Kollektor Agent 1",
        "value": 1420.0,
        "secondaryValue": 1420.0
      }
    ]
  }
}
```
