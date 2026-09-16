# ari4java

`implementation("io.github.ari4java:ari4java:0.18.0")` — `build.gradle.kts`

## Bu nima

**ari4java** — Asterisk REST Interface (ARI) uchun Java klient kutubxonasi.
Asterisk 12+ da ARI — bu dialplan'dan tashqarida, tashqi dastur orqali qo'ng'iroqni
boshqarish uchun HTTP + WebSocket API. Kutubxona ikki narsani beradi:

1. **HTTP klient** — `channels`, `bridges`, `endpoints`, `playbacks` kabi ARI
   resurslariga type-safe so'rovlar (`ari.channels().originate(...).execute()`).
2. **WebSocket event oqimi** — Asterisk `StasisStart`, `StasisEnd`,
   `ChannelHangupRequest` kabi voqealarni yuboradi; `AriWSHelper` ni override
   qilib ularni Java metodlari sifatida qabul qilamiz.

Model klasslari Asterisk'ning Swagger spetsifikatsiyasidan generatsiya qilingan,
shuning uchun paket nomi `ch.loway.oss.ari4java.generated.models` (0.9.0 dan
0.18.0 ga o'tganda Maven koordinatasi `io.github.ari4java` bo'ldi, Java paketlari
o'zgarmadi). Transport ostida Netty ishlaydi — loyihadagi RTP bilan bir xil
Netty versiyasini bo'lishadi (`build.gradle.kts` dagi netty izohiga qarang).

## Loyihada nima uchun kerak

Loyiha SIP orqali qo'ng'iroq qiladi, lekin SIP stack'i o'zi emas — **Asterisk 20**
qiladi. Java ilova Asterisk'ga "qo'ng'iroq qil", "javob ber", "audioni menga
yo'naltir", "qo'y" deb buyruq beradi. Shu buyruqlar va Asterisk'dan keladigan
voqealar **faqat ari4java orqali** yuradi. Kutubxona bo'lmasa, ARI'ning HTTP va
WebSocket protokolini qo'lda yozish kerak bo'lardi.

Faqat `agent/ari` paketi ichida ishlatiladi: ulanish `AriClient` da (`ARI.build`), voqealar
oqimi `AriEventDispatcher` da (`connect()`), qo'ng'iroqning boshi va oxiri `CallSetupService` /
`CallTeardownService` da. Paketdan tashqarida hech bir klass `ch.loway.*` ni import qilmaydi;
ilovaning qolgan qismi uchun fasad — `AriService`.

## Qo'ng'iroq hayot sikli (agent/ari)

```
Java (ari4java)                          Asterisk
─────────────────────────────────────────────────────────────
ARI.build(url, app, user, pass)   ───►  HTTP + WS ulanish
events().eventWebsocket(app)      ◄───  voqealar oqimi (Stasis app: uysot-voice-agent)

channels().originate("PJSIP/998…@trunk")
        .setApp(app).setAppArgs(callId)  ───►  mijozga qo'ng'iroq
                                          ◄───  StasisStart (mijoz ko'tardi)

channels().externalMedia(app, "ip:port", "ulaw")
        .setEncapsulation("rtp").setTransport("udp")
                                          ───►  RTP oqimi bizning Netty UDP portimizga
bridges().create().setType("mixing")
bridges().addChannel(bridge, extMedia)
channels().answer(channelId)
bridges().addChannel(bridge, channelId)   ───►  mijoz ↔ AI audio ko'prigi

... suhbat (audio RTP orqali, ARI emas) ...

channels().hangup(channelId)              ───►  qo'ng'iroq tugadi
                                          ◄───  StasisEnd, ChannelDestroyed (Q.850 cause)
```

Asosiy ARI chaqiriqlari:

| Chaqiriq                                            | Nima uchun                                                                                                     |
|-----------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `channels().originate(...)`                         | Outbound qo'ng'iroq; `appArgs` orqali `callId` beriladi, `StasisStart` da shu bilan bog'lanadi                 |
| `channels().externalMedia(...)`                     | Asterisk'dan audioni RTP/UDP orqali o'z portimizga olish (audio ARI'dan emas, RTP'dan yuradi — CLAUDE.md §8.2) |
| `bridges().create/addChannel/removeChannel/destroy` | Mijoz + externalMedia + operator kanallarini bitta mixing bridge'da ushlash, operatorga transfer               |
| `channels().answer / hangup / ring / sendDTMF`      | Qo'ng'iroqni boshqarish                                                                                        |
| `channels().snoopChannel(...)`                      | Supervisor live tinglash (spy/whisper)                                                                         |
| `channels().setChannelVar / getChannelVar`          | `JITTERBUFFER(...)` qo'yish; `UNICASTRTP_LOCAL_ADDRESS/PORT` o'qib playback manzilini aniqlash                 |
| `endpoints().get()`                                 | PJSIP endpoint holatini tekshirish                                                                             |

Qabul qilinadigan voqealar (`AriWSHelper` override'lari): `StasisStart`, `StasisEnd`,
`ChannelHangupRequest`, `ChannelDestroyed`, `ChannelDtmfReceived`. Qolganlari
(`Dial`, `ChannelVarset`, ...) ataylab e'tiborsiz qoldiriladi.

## Konfiguratsiya

`voice-agent.asterisk.*` → `AsteriskProperties` (`agent/ari/AsteriskProperties.java`):
`ari-url`, `ari-user`, `ari-password`, `app-name`, `enabled` va h.k. ARI foydalanuvchisi
Asterisk tomonida `asterisk/etc/asterisk/ari.conf` (+ `.env` dan generatsiya
qilinadigan `ari_auth.conf`) da. Dialplan (`extensions.conf`) kanallarni
`Stasis(uysot-voice-agent, ...)` bilan ilovaga topshiradi.

## Loyihaga xos nozikliklar

- **`AriVersion.IM_FEELING_LUCKY`** — ARI versiyasi Asterisk'dan avtomatik aniqlanadi.
- **`BaseAriAction.setObjectMapperLessStrict()`** — Asterisk 20 yuboradigan ba'zi
  maydonlar (masalan `ChannelDestroyed.tech_cause`) 0.18.0 modellarida yo'q. Qat'iy
  rejimda voqea tashlab yuborilardi; less-strict rejimda maydon o'tkazib yuboriladi va
  voqea yetib keladi. Shu sababli `ch.loway` loggerlari `application.yml` da `ERROR`
  darajasiga qo'yilgan — aks holda har qo'ng'iroqda o'nlab "Event X Unhandled" WARN
  chiqadi.
- **Jackson 2** — ari4java `com.fasterxml.jackson` ni tortib keladi; Boot 4 esa Jackson 3
  ni auto-config qiladi. Shu sababli Jackson 2 `build.gradle.kts` da oshkora e'lon
  qilingan (u yerdagi izohga qarang).
- **Netty versiyasi pin qilinmaydi** — 0.18.0 Netty 4.2 talab qiladi, Boot 4.1.1 buni
  allaqachon beradi. Eski pin HTTPS'da `NoSuchFieldError` chiqargan edi.
- ARI xatolari service'da `ExternalServiceException(ErrorCode.ARI_*, "asterisk", ...)`
  ga o'raladi (CLAUDE.md §5).
- ARI ulanishi ko'tarilmasa ilova crash bo'lmaydi — faqat log; `enabled=false` testlar
  uchun ulanishni o'chiradi.

## Asterisk ↔ ARI versiyasi

ARI versiyasi Asterisk versiyasi bilan bir xil emas. Asterisk'ning so'nggi relizlaridagi
`rest-api/resources.json` bo'yicha (2026-09-12 holatiga):

| Asterisk | ARI | `AriVersion` |
|---|---|---|
| 20 LTS, hozir `docker-compose.yml` da (20.21.0) | 8.0.0 | `ARI_8_0_0` |
| 22 LTS (22.11.0) | 10.0.0 | `ARI_10_0_0` |
| 23 (23.5.0) | 11.0.0 | `ARI_11_0_0` |

`IM_FEELING_LUCKY` versiyani serverdan o'qiydi, shuning uchun Asterisk 22 ga o'tishda kodni
o'zgartirish shart emas. Lekin 0.18.0 dagi ARI 10.0.0 ma'lumoti eskirgan (22.10+ dagi bridge
o'zgaruvchilari, `getChannelVars`/`setChannelVars`, `CallBroadcast`/`CallClaimed` yo'q) —
bu keyingi relizda tuzatilgan.

## Keyingi relizda (0.18.0 dan keyin)

- ARI 10.0.0 (Asterisk 22 LTS) bindinglari 22.11.0 dan yangilangan.
- `IM_FEELING_LUCKY` noma'lum yangi ARI versiyasida xato bermay, eng yaqin eski versiyaga tushadi.
- `ARI.setMaxReconnectCount(int)` — event WebSocket'i qancha qayta ulanishini sozlash
  (standart 10 urinish ≈ 1,5 daqiqa; `-1` = cheksiz).
- Less-strict rejim noma'lum obyekt/massiv qiymatini to'liq o'tkazib yuboradi (avval keyingi
  maydonlarni "yutib" yuborardi) va DEBUG'da loglaydi.
- `AriWSHelper` ishlov berilmagan voqealarni DEBUG'da loglaydi.

Relizdan keyin loyihada: `build.gradle.kts` dagi versiyani ko'tarish, `application.yml` dagi
`ch.loway` ERROR pinlarini olib tashlash, `AriClient` da `ARI.build(...)` dan keyin
`ari.setMaxReconnectCount(-1)` (ulanishni `AriEventDispatcher.connect()` ochadi).

## Havolalar

- GitHub: https://github.com/ari4java/ari4java
- Asterisk ARI hujjati: https://docs.asterisk.org/Asterisk_20_Documentation/API_Documentation/Asterisk_REST_Interface/
- Loyiha arxitekturasi: `CLAUDE.md` §2 (`agent/ari`, `agent/rtp`)
