## `GET /api/reports/calls/{callId}` — Bitta qo'ng'iroq + to'liq transkript

**Response** (`CallDetail`):

```json
{
  "call": { /* CallRow, yuqoriga qarang */ },
  "transcript": [
    { "seq": 1, "role": "AGENT", "text": "Assalomu alaykum...", "dialogState": "GREETING", "tsOffsetMs": 0, "confidence": null },
    { "seq": 2, "role": "CLIENT", "text": "Ha, tinglayapman", "dialogState": null, "tsOffsetMs": 3200, "confidence": 0.94 }
  ],
  "reasonCode": "TEMPORARY_HARDSHIP",
  "sentiment": "POSITIVE",
  "qaScore": 95,
  "commitmentScore": 90,
  "callbackAt": "2026-09-05T14:00:00Z",
  "needsFollowUp": false,
  "followUpNote": null,
  "escalated": false,
  "errorMessage": null,
  "technical": {
    "channelName": "PJSIP/trunk-endpoint-00000012",
    "trunk": "trunk-endpoint",
    "amdResult": "HUMAN",
    "sttProvider": "yandex",
    "ttsProvider": "yandex",
    "ttsVoice": "alena",
    "llmModel": "gemini-3.8-flash",
    "promptTokens": 1840,
    "completionTokens": 320,
    "cachedTokens": 1200,
    "turnCount": 6,
    "avgTurnLatencyMs": 780,
    "maxTurnLatencyMs": 1120,
    "avgLlmLatencyMs": 410,
    "maxLlmLatencyMs": 650
  }
}
```

| Maydon | Turi | Izoh |
|---|---|---|
| `qaScore` | int (0–100) | AI Agent suhbat sifati va qoidalarga rioya qilish bahosi |
| `commitmentScore` | int (0–100) | Mijozning to'lov/kelishuvga rozilik va sodiqlik darajasi |
| `sentiment` | enum | `POSITIVE`, `NEUTRAL`, `NEGATIVE`, `ANGRY` |
| `callbackAt` | ISO timestamp | Mijoz so'ragan qayta qo'ng'iroq vaqti |

---

## `GET /api/reports/calls/{callId}/recording` — Qo'ng'iroq audiosi

**`ResponseData`ga o'ralmagan** — xom WAV baytlarini qaytaradi
(`ResponseEntity<Resource>`). Ilgari bu endpoint `302` bilan
`/api/files/{id}` ga yuborardi; endi baytlarni o'zi qaytaradi, chunki
redirect pleyerdan `Authorization` sarlavhasini yo'qotib, yozuvni
ijro etib bo'lmaydigan qilib qo'yardi.

**Sarlavhalar**:

| Sarlavha | Qiymat |
|---|---|
| `Content-Type` | `audio/wav` |
| `Content-Disposition` | `inline; filename="<asl nom>"` |
| `Content-Length` | fayl hajmi (pleyer davomiylikni shundan chizadi) |
| `Accept-Ranges` | `bytes` |

**Seek**: so'rovda `Range: bytes=<start>-<end>` bo'lsa javob `206 Partial
Content` bo'ladi va `Content-Range: bytes <start>-<end>/<total>` qaytadi.
Bir nechta diapazon so'ralsa faqat birinchisi beriladi.

Autentifikatsiya boshqa `/api/**` kabi — ya'ni `<audio src="...">`
to'g'ridan-to'g'ri ishlamaydi; frontend `fetch` bilan blob olib
`URL.createObjectURL` ga o'raydi.

**Xatolar**: bu qo'ng'iroqda yozuv bo'lmasa (`recording_file_id` bo'sh) —
`404` `CALL_RECORDING_NOT_FOUND`. MinIO ishlamasa — `502`.
