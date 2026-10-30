# LiveKit telefoniya dvigateli

Asterisk bilan **yonma-yon** ishlaydi: qaysi dvigatel ishlatilishi kompaniya darajasida
tanlanadi (`PUT /api/companies/{id}/telephony-engine` → `ASTERISK` | `LIVEKIT`,
[api/companies.md](api/companies.md)).

```
Telefon ──SIP:5070 / RTP:10300-10400──▶ livekit-sip ──▶ livekit (xona) ◀──WebRTC/Opus── livekit-bridge (Go)
                                                           │                                 │ G.711 RTP (compose tarmog'i)
                                                           │                                 ▼
                                                           └── webhook ──▶ app: POST /api/webhooks/livekit
                                                                           app: RtpEndpoint → VAD/STT/LLM/TTS (o'zgarmagan)
app ──Twirp (HTTP, HS256 JWT)──▶ livekit:7880   (CreateRoom, DeleteRoom, CreateSIPParticipant)
app ──HTTP──▶ livekit-bridge:8090               (POST /calls, DELETE /calls/{room}, POST /calls/{room}/dtmf)
app ◀──HTTP── livekit-bridge                       (POST /api/webhooks/livekit-bridge/dtmf — caller keypad digits)
```

Bridge Asterisk'dagi `externalMedia` vazifasini bajaradi. U xonaga `robot-call-agent` bo'lib
kiradi, telefon tomonining Opus audiosini 8 kHz'da dekodlaydi va G.711 RTP qilib app'ning
RTP portiga yuboradi. App'dan kelgan G.711'ni esa Opus'ga kodlab agent track'i sifatida
e'lon qiladi. App tomonidagi butun pipeline (VAD, STT, dialog, TTS, yozuv, finalizatsiya)
Asterisk qo'ng'irog'idagi bilan bir xil.

## Holat

| Bosqich | Nima | Holat |
|---|---|---|
| 1 | docker-compose (`livekit`, `livekit-sip`), server API klienti, imzolangan webhook, inbound qoidalari | ✅ |
| 2 | Go media bridge, chiquvchi va kiruvchi qo'ng'iroqda agent suhbati, teardown | ✅ |
| 3 | Kompaniyaning o'z (managed) SIP trunk'i orqali chiquvchi qo'ng'iroq | ✅ |
| 4 | Tashqi raqamga uzatish (`transferPhoneNumber` / bosqich raqami) | ✅ |
| 5 | Operatorga brauzer (WebRTC) orqali uzatish: taklif, qabul, xonaga qo'shilish | ✅ backend (frontend kerak) |
| 6a | Kiruvchi CallFlow (IVR): salomlashish, jadval, til/menyu (ovozli), AI agent, ovozli pochta, webhook | ✅ odamga yetadigan tugunlarsiz |
| 6b | CallFlow odamli tugunlari: operatorlar (brauzer) va telefon raqamga yo'naltirish | ✅ navbatsiz |
| 6c | Mijoz tugmalari (DTMF): bridge → ilova, menyu va dialogda | ✅ (Go kodi kompilyatsiya qilinmagan) |
| 7 | Navbat kutishi LiveKit'da: tartib, hold musiqa, pozitsiya e'loni, callback taklifi | ✅ (`FlowQueueLine`) |
| 8 | Brauzer test va vidjet qo'ng'iroqlari LiveKit'da: xona + token, brauzer qo'shilganda boshlanadi | ✅ backend (frontend kerak) |

**LiveKit'da hozircha yo'q (faqat Asterisk'da):** `FORWARD_EXTERNAL` ning "1 ni bosing" tasdig'i
(`confirmAnswer`: LiveKit xonasida har bir ovoz hammaga eshitiladi, shuning uchun faqat operator oyog'iga
aytib bo'lmaydi), softphone operatorlarga (`op-<id>` PJSIP) uzatish, uzatilgandan keyingi suhbatni yozish,
`WHISPER` (pichirlash), kutish vaqti bahosi va navbatning `waitingAgentId` hamrohi, `overflowAction`.
Telefonga chiqadigan qo'lda va test qo'ng'iroqlar (`POST /api/calls`, `POST /api/calls/test`) hamda
brauzer test va vidjet qo'ng'iroqlari (`POST /api/calls/web-test`, vidjet sessiyasi) LiveKit'da ishlaydi.
Agent IVR menyusida tugma bosa oladi (`sendDtmfTones`): bridge `SipDTMF` data paketini xonaga yuboradi, LiveKit SIP
xizmati uni telefon oyog'ida ohangga aylantiradi. Mijozning tugmalarini bridge `SipDTMF` data paketidan o'qib, ilovaga yuboradi (`POST /api/webhooks/livekit-bridge/dtmf`, `BRIDGE_APP_DTMF_URL`, [api/webhooks.md](api/webhooks.md)); u yerdan Asterisk'dagi ARI hodisasi bilan bir xil joyga boradi (`DtmfInputRouter#onDigit`).
Bu menyu kalitlarini, ichki raqam terishni va `dtmfInputEnabled` agentlarni yoqadi.
Supervayzer kuzatuvi (`POST /api/calls/{id}/monitor`) brauzerdan ishlaydi: `LISTEN` — ko'rinmas va jim ishtirokchi (`sv-<userId>`), `BARGE` — mikrofon bilan ([api/calls.md](api/calls.md)).

## Qo'ng'iroq oqimi

**Chiquvchi** (`LiveKitGateway#originate` → `LiveKitCallService#dialOutbound`):
1. Trunk tanlanadi (Asterisk'dagi qoida bilan bir xil: berilgan `sipTrunkId` yoki kompaniyaning default trunk'i).
   `sipTrunkId` berilmasa, default trunk ishlamaganda kompaniyaning boshqa **managed** trunk'lari ketma-ket
   sinaladi: LiveKit qo'ng'iroqni joylay olmasa yoki operator `500/502/503/504` desa (CPS limitidan oshgan
   trunk ham shuni qaytaradi). Band, rad etish, javob bermaslik trunk xatosi emas, failover bo'lmaydi.
   **Managed** trunk'ning host, login va paroli qo'ng'iroq bilan birga `CreateSIPParticipant.trunk` (inline) qilib
   beriladi, LiveKit'da hech narsa saqlanmaydi. Manual trunk → `409 SIP_TRUNK_NOT_MANAGED`. Trunk'i yo'q
   platforma kompaniyasi global `LIVEKIT_SIP_TRUNK_ID` orqali chiqadi; boshqa trunk'siz kompaniya
   Asterisk'dagidek `409 SIP_TRUNK_NONE_CONFIGURED`.
2. `CreateRoom livekit-<callId>`, so'ng urinish qatori va tracking yoziladi; dialer'ga darhol qaytadi.
3. Call thread'da `CreateSIPParticipant` (`waitUntilAnswered`) go'shak ko'tarilguncha yoki rad
   etilguncha kutadi (`LIVEKIT_ANSWER_TIMEOUT_SEC`). Rad etilsa, SIP status disposition'ga
   aylanadi (486/408/480/487 → `NO_ANSWER`, 603 → `REFUSED`, 404/484 → `WRONG_NUMBER`,
   403 → `CARRIER_REJECTED`, qolgani → `FAILED`), urinish yopiladi.
4. Javob berilsa: RTP port olinadi, bridge xonaga kiradi (`POST /calls`), sessiya ro'yxatga
   olinadi va dialog boshlanadi.

**Kiruvchi:** dispatch rule qo'ng'iroqni `inbound-…` xonaga qo'yadi. `participant_joined`
webhook'i kelgach `InboundCallGate` qaror qiladi. `AGENT` bo'lsa, `LiveKitCallService#startInboundCall`
CRM, xotira va fact webhook'idan ma'lumot olib agentni boshlaydi. Boshqa holatda xona o'chiriladi.

**Uzatish** (`LiveKitCallService#transferToNumber`): agent operatorga uzatmoqchi bo'lsa, avval ssenariy
bosqichining raqami, bo'lmasa agentning `transferPhoneNumber`'i olinadi (`PJSIP/` prefiksi olib tashlanadi;
oddiy raqam bo'lmasa — go'shak qo'yiladi). Raqam kompaniyaning default trunk'i orqali xonaga
`sip-operator` bo'lib chaqiriladi. Javob bersa bridge xonadan chiqadi va mijoz bilan operator LiveKit orqali
to'g'ridan-to'g'ri gaplashadi. Javob bermasa (`OPERATOR_ANSWER_TIMEOUT`) Asterisk'dagidek "operatorlar
band" aytiladi, `CALLBACK_REQUESTED` yoziladi va go'shak qo'yiladi. Ikki tomondan biri go'shakni qo'ysa,
qo'ng'iroq tugaydi.

**Brauzer test va vidjet qo'ng'iroqlari** (`WebTestCallService`, `TelephonyGateway#openBrowserCall`): kompaniya
`LIVEKIT` dvigatelida bo'lsa, `POST /api/calls/web-test` va vidjet sessiyasi SIP o'rniga `join` qaytaradi
([api/calls.md](api/calls.md)). Xona `web-<sessionId>` deb ochiladi, brauzer `browser-caller` bo'lib qo'shiladi; qo'shilishi
webhook'da `participant_joined` bo'lib keladi va `LiveKitCallService#onBrowserCallerJoined` kutayotgan sessiyani
olib, Asterisk'dagi `web-test` Stasis chaqiruvi kabi qo'ng'iroqni boshlaydi. Brauzer chiqsa — `participant_left`,
teardown. Qo'ng'iroq davomida operatorga uzatish va boshqalar telefon qo'ng'irog'i bilan bir xil ishlaydi.

**Operatorga uzatish (brauzerdan)** (`LiveKitCallService#transfer` → `LiveKitOperatorTransfers`): ssenariy
bosqichining raqami yoki agentning `transferPhoneNumber`'i bo'lsa, yuqoridagidek telefonga chaqiriladi. Raqam
yo'q bo'lsa yoki agentda `transferQueueId` bo'lsa, qo'ng'iroq kompaniyaning eng uzoq `AVAILABLE` turgan
operatoriga taklif qilinadi (`CALL_OFFER`). Operator `POST /api/operator/calls/{channelId}/accept` bilan token oladi,
brauzerdan xonaga `op-<userId>` bo'lib qo'shiladi ([api/operator.md](api/operator.md)); LiveKit webhook'i
`participant_joined` kelganda bridge xonadan chiqadi. Javob bo'lmasa — yuqoridagi fallback. Brauzer ulanishi
uchun `LIVEKIT_PUBLIC_URL` (`wss://`, TLS) kerak.

**CallFlow (IVR)** (`LiveKitCallService#startInboundFlow`): marshrut `FLOW` bo'lsa, `InboundCallGate` qarori
bo'yicha media Asterisk'dagidek ochiladi (yozuv, VAD), so'ng `CallFlowRunner` grafni shu qo'ng'iroq oqimida
yuritadi. Ishlaydi: `PLAY`, `SCHEDULE`, `LANGUAGE_SELECT`, `MENU` (kalit so'z bilan ovozli), `AI_AGENT`,
`VOICEMAIL`, `WEBHOOK_ROUTER`, `CALLER_ROUTER`, `HANGUP`. Odamga yetadigan tugunlar (`RING_GROUP`, `EXTENSION`, `QUEUE`, `FORWARD_EXTERNAL`) brauzerdagi operatorlarga yoki telefon raqamga o'tadi (`LiveKitFlowPeople`); hech kim olmasa `NO_ANSWER` chiqishiga o'tadi
([api/call-flows.md](api/call-flows.md)). `CLOSED` / `REJECT` qarorlari (xabar aytib uzish) bu dvigatelda
xabarsiz uziladi.

**Tugashi:** telefon tomoni chiqsa (`participant_left`), teardown Asterisk'dagi `StasisEnd` bilan
bir xil ishlaydi va xona yopiladi. Agent go'shakni qo'ysa (yoki voicemail, shutdown, ghost reaper),
`LiveKitGateway#hangup` (resolver orqali) `DeleteRoom` qiladi. Shundan keyin `participant_left`
keladi va yuqoridagi teardown ishlaydi.

## 1. `.env`

```properties
COMPOSE_PROFILES=livekit
LIVEKIT_ENABLED=true
LIVEKIT_API_KEY=robotcall
# kamida 32 belgi (HS256): openssl rand -base64 32
LIVEKIT_API_SECRET=...
# 3-qadamdan keyin to'ldiriladi
LIVEKIT_SIP_TRUNK_ID=
```

**App compose ichida ishlashi shart.** Bridge RTP'ni compose tarmog'i ichida `RTP_LOCAL_IP=app`
ga yuboradi va o'zini `livekit-bridge` deb e'lon qiladi. IntelliJ'dan ishga tushirilgan app'ga
bu manzillar yetib bormaydi.

## 2. Ishga tushirish

```bash
docker compose --profile livekit up -d --build livekit livekit-sip livekit-bridge app
docker compose logs -f livekit livekit-sip livekit-bridge
```

Bridge birinchi build'da Go bog'liqliklarini yuklab oladi (`go mod tidy`). Takrorlanadigan
build uchun bir marta `cd livekit-bridge && go mod tidy` qilib, `go.sum` va `go.mod` commit
qilinadi. Bu Go 1.24+ va libopus (`opus-dev`, Windows'da MSYS2 orqali) talab qiladi. Yoki
fayllar konteynerdan olinadi: `docker run --rm -v "$PWD/livekit-bridge:/src" -w /src golang:1.24-alpine go mod tidy`.

`livekit` va `livekit-sip` bitta Redis'dan foydalanadi (`redis:6379`, db 1), chunki bir-biri bilan shu orqali gaplashadi.

**Linux server:** SIP/RTP bridge NAT ortida konteyner IP'sini SDP'ga yozadi. Shuning uchun
`livekit-sip` (va kerak bo'lsa `livekit`) uchun `network_mode: host` qo'yiladi (`ports:`
o'chiriladi) va `.env` da `LIVEKIT_USE_EXTERNAL_IP=true` yoziladi. Docker Desktop'da tashqi
trunk bilan haqiqiy qo'ng'iroq Asterisk'dagi kabi cheklangan.

## 3. SIP trunk va dispatch rule (bir marta)

Trunk'lar va dispatch rule LiveKit API obyektlari, ular config faylda emas. `lk` CLI bilan yaratiladi:

```bash
export LIVEKIT_URL=http://localhost:7880 LIVEKIT_API_KEY=robotcall LIVEKIT_API_SECRET=...
# yoki: docker run --rm --network robot-call-v2_default -e LIVEKIT_URL=http://livekit:7880 ... livekit/livekit-cli
```

**Chiquvchi trunk** — faqat **platforma kompaniyasi** uchun (o'z trunk'i yo'q kompaniya). Boshqa
kompaniyalar o'zlarining managed trunk'i orqali chiqadi, ular uchun bu qadam kerak emas.
`outbound-trunk.json`:

```json
{
  "trunk": {
    "name": "skyline-out",
    "address": "sip.provider.uz",
    "numbers": ["998712001122"],
    "authUsername": "LOGIN",
    "authPassword": "PAROL"
  }
}
```

```bash
lk sip outbound create outbound-trunk.json   # → ST_xxx  →  .env: LIVEKIT_SIP_TRUNK_ID=ST_xxx
```

**Kiruvchi trunk** (`inbound-trunk.json`) va **dispatch rule** (`dispatch-rule.json`).
Xona prefiksi aynan `inbound-` bo'lishi shart (`LiveKitTelephonyService.INBOUND_ROOM_PREFIX`):

```json
{ "trunk": { "name": "skyline-in", "numbers": ["998712001122"] } }
```

```json
{ "dispatch_rule": { "name": "robot-call-inbound", "rule": { "dispatchRuleIndividual": { "roomPrefix": "inbound-" } } } }
```

```bash
lk sip inbound create inbound-trunk.json
lk sip dispatch create dispatch-rule.json
```

(JSON shakli `lk` versiyasiga qarab biroz farq qilishi mumkin: `lk sip outbound create --help`.)
Provayder tomonida kiruvchi qo'ng'iroq `<server-ip>:5070` ga yo'naltiriladi.

## 4. Kompaniyani LiveKit'ga o'tkazish

```http
PUT /api/companies/{id}/telephony-engine
{ "telephonyEngine": "LIVEKIT" }
```

## Tekshirish

- Bridge tirikmi: `docker compose exec app curl -s -o /dev/null -w '%{http_code}' http://livekit-bridge:8090/healthz` → `200`.
- **Chiquvchi:** LiveKit kompaniyasining kampaniyasidan qo'ng'iroq qilinadi.
  1. App logida `LiveKit: room livekit-… created` va `LiveKit: dialling …` chiqadi.
  2. Go'shak ko'tarilgach `LiveKit media ready for livekit-…` chiqadi, bridge logida esa `bridged` va `relaying phone audio`.
  3. Agent salomlashadi va javob beradi.
  4. Go'shak qo'yilgach `LiveKit phone leg left … ending the call` va `Torn down call livekit-…` chiqadi, `call_attempt` disposition bilan yopiladi.
- **Javob berilmagan:** qo'ng'iroq qabul qilinmaydi. `LIVEKIT_ANSWER_TIMEOUT_SEC` dan keyin `Unanswered call livekit-… settled as NO_ANSWER` chiqadi.
- **Kiruvchi:** DID'ga qo'ng'iroq qilinadi. `AGENT` marshruti bo'lsa agent javob beradi, marshrut bo'lmasa logda `refused (NO_ROUTE), hanging up` chiqadi.
- Teardown logida `ALERT: no inbound RTP` chiqsa, bridge app'ga RTP yetkazmayapti: `RTP_LOCAL_IP=app` va bridge logini tekshiring.
- Webhook imzosi: imzosiz `curl -X POST localhost:8080/api/webhooks/livekit -d '{}'` → `403`.

## Kod

| Joy | Vazifa |
|---|---|
| `agent/livekit/LiveKitProperties` | `voice-agent.telephony.livekit.*` (`config/telephony.yml`) |
| `agent/livekit/LiveKitApiClient` | Twirp chaqiruvlari, server JWT, webhook imzosini tekshirish |
| `agent/livekit/LiveKitTelephonyService` | xona yaratish/o'chirish, javobni kutib qo'ng'iroq qilish |
| `agent/livekit/LiveKitBridgeClient` | Go bridge'ni xonaga kiritish va chiqarish |
| `agent/ari/LiveKitCallService` | qo'ng'iroq hayot sikli: jiringlash → sessiya → dialog → teardown |
| `agent/ari/LiveKitOperatorTransfers` | operatorga brauzerdan uzatish: taklif, token, xonaga qo'shilish, tugash |
| `agent/ari/LiveKitGateway#originate` | `LIVEKIT` kompaniyasining chiquvchi qo'ng'irog'i |
| `agent/ari/LiveKitGateway#hangup` | `TelephonyGatewayResolver` LiveKit xonasini taniydi → `DeleteRoom` |
| `siptrunk/…/SipTrunkUseCase#requireCredentialsForCall` | managed trunk'ning host, login, ochilgan paroli (LiveKit uchun) |
| `webhook/…/LiveKitWebhookService` | `participant_joined` / `participant_left` |
| `livekit-bridge/` (Go) | `main.go` HTTP API, `call.go` xona ↔ RTP, `g711.go` |
