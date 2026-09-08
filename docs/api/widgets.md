# Website Widgets API

The Website Widget module allows embedding real-time AI voice agents directly into external websites using a light script embed snippet or customizable WebRTC/WebSocket audio component.

---

## 1. Create Website Widget for Agent

`POST /api/ai-agents/{agentId}/widgets`

**Authorization:** `Bearer <token>` (SUPER_ADMIN, COMPANY_ADMIN, OPERATOR)

### Request Body:
```json
{
  "name": "Front Landing Widget",
  "enabled": true,
  "allowedOrigins": ["https://example.com", "http://localhost:3000"],
  "theme": {
    "primaryColor": "#002FA7",
    "accentColor": "#0F172A",
    "surfaceColor": "#FFFFFF",
    "textColor": "#111827",
    "mutedTextColor": "#667085",
    "buttonTextColor": "#FFFFFF",
    "borderColor": "#DADDE3",
    "position": "bottom-right",
    "launcherSize": "comfortable",
    "panelWidth": 340,
    "borderRadius": 16,
    "defaultOpen": false,
    "showAvatar": true,
    "avatarImageUrl": "https://example.com/avatar.png",
    "avatarOrbColor1": "#002FA7",
    "avatarOrbColor2": "#00F0FF",
    "brandName": "QuickVoice",
    "actionText": "Talk to us",
    "welcomeText": "Talk with our voice agent.",
    "startButtonText": "Start call",
    "endButtonText": "End call",
    "connectingText": "Connecting",
    "listeningText": "Listening",
    "speakingText": "Assistant speaking",
    "endedText": "Call ended",
    "whiteLabel": false
  },
  "consentRequired": true,
  "consentText": "This voice call may be recorded and transcribed."
}
```

### Response:
```json
{
  "accept": true,
  "message": null,
  "messageCode": null,
  "errors": null,
  "data": {
    "id": 1,
    "widgetKey": "wgt_98ab76cd43ef1201",
    "companyId": 10,
    "agentId": 5,
    "agentName": "Sales Representative",
    "name": "Front Landing Widget",
    "enabled": true,
    "allowedOrigins": ["https://example.com", "http://localhost:3000"],
    "theme": { ... },
    "consentRequired": true,
    "consentText": "Ushbu ovozli suhbat sifatni yaxshilash uchun yozib olinadi va matnga o'giriladi.",
    "embedSnippet": "<script src=\"/widgets/v1/widget.js\" data-widget-key=\"wgt_98ab76cd43ef1201\" async></script>",
    "scriptUrl": "/widgets/v1/widget.js",
    "createdAt": "2026-09-06T12:00:00Z",
    "updatedAt": "2026-09-06T12:00:00Z"
  }
}
```

---

## 2. List Widgets for Agent

`GET /api/ai-agents/{agentId}/widgets`

**Response:** List of `WidgetRow` objects.

---

## 3. Get Widget Details

`GET /api/widgets/{widgetId}`

**Response:** `WidgetRow`

---

## 4. Update Website Widget

`PUT /api/widgets/{widgetId}`

### Request Body:
```json
{
  "name": "Updated Sales Widget",
  "enabled": true,
  "allowedOrigins": ["*"],
  "theme": { ... },
  "consentRequired": true,
  "consentText": "Qo'ng'iroq sifati maqsadida suhbat yozib olinadi."
}
```

---

## 5. Delete Widget

`DELETE /api/widgets/{widgetId}`

---

## 6. Public Widget Configuration (No Auth Required)

`GET /api/public/widgets/{widgetKey}/config`

Used by the embedded web widget script on the client's browser. Declared `permitAll` in
`SecurityConfig`; the widget key plus the allowed-origins list is the only credential.

**Request Headers:**
- `Origin`: `https://example.com`

The `Origin` header is **required**: a request without one is refused with
`403 AGENT_WIDGET_ORIGIN_FORBIDDEN`, the same as a request from an origin that is not on
the widget's list. An **empty** `allowedOrigins` list therefore refuses everything — the
widget key is printed in the customer's own page, so a widget with nothing tying it to a
site would take calls from anywhere; `"*"` is how a widget says it wants that on purpose.
This is why `allowedOrigins` is required when creating a widget and cannot be emptied
afterwards.

A disabled widget answers `409 AGENT_WIDGET_DISABLED`, an unknown key
`404 AGENT_WIDGET_NOT_FOUND`.

**Response:**
```json
{
  "accept": true,
  "message": null,
  "messageCode": null,
  "errors": null,
  "data": {
    "widgetKey": "wgt_98ab76cd43ef1201",
    "name": "Front Landing Widget",
    "agentName": "Sales Representative",
    "theme": { ... },
    "consentRequired": true,
    "consentText": "This voice call may be recorded and transcribed."
  }
}
```

---

## 7. Public Session — start a call

`POST /api/public/widgets/{widgetKey}/session?language=uz-UZ`

Called by the embed script when the visitor presses the call button. No token: the
`Origin` header is checked against the widget's allowed-origins list exactly as in §6,
and on top of that the agent's own `concurrentCallsLimit` and `dailyCallsLimit` are
applied — this is the one endpoint a stranger can spend the company's minutes on.

An agent that set **no** limit of its own is not unlimited here: it falls back to
**5 concurrent** and **200 daily** widget calls. Raise them by setting the agent's own
`concurrentCallsLimit` / `dailyCallsLimit` ([ai-agents.md](ai-agents.md)).

`language` is optional; without it the call runs in the agent's own language.

**Response:**
```json
{
  "accept": true,
  "message": null,
  "messageCode": null,
  "errors": null,
  "data": {
    "sessionId": "6f1c2a4e-3b7d-4c1e-9a0f-2d5e8b7c1a90",
    "wsUrl": "wss://voice.example.uz:8089/ws",
    "sipUser": "webtest",
    "sipPassword": "webtest123",
    "dialNumber": "700",
    "sessionHeader": "X-Web-Test"
  }
}
```

The browser connects a SIP-over-WebSocket client to `wsUrl` as `sipUser`/`sipPassword`
and INVITEs `dialNumber` carrying `sessionHeader: sessionId`. From Asterisk on it is an
ordinary call: Stasis → RTP → STT/LLM/TTS. The attempt is recorded with phone `WIDGET`.

The session is **single-use** and is forgotten after **5 minutes** if the browser never
dials in.

> ⚠️ `sipUser`/`sipPassword` are the **shared** WebRTC account and are handed to every
> visitor, so treat the password as published. They are not a capability on their own:
> that Asterisk endpoint sits in the `[from-webrtc]` context, whose only extension is
> `dialNumber`, and a call there is hung up unless it carries a session id this
> application issued. Never point the `[webtest]` endpoint at a context that can dial a
> trunk (`asterisk/etc/asterisk/pjsip.conf`) — that would turn this response into toll
> fraud.

**Errors:** `403 AGENT_WIDGET_ORIGIN_FORBIDDEN`, `404 AGENT_WIDGET_NOT_FOUND`,
`409 AGENT_WIDGET_DISABLED`, `409 AGENT_WIDGET_BUSY` (concurrent limit),
`409 AGENT_WIDGET_DAILY_LIMIT_REACHED`, `502 WEB_TEST_NOT_CONFIGURED`
(`voice-agent.asterisk.web-test.ws-url` is blank on the server).

> ⚠️ The limits are counted **per application instance**, not in the database. They are a
> safety ceiling against a runaway page, not billing: behind two instances each enforces
> its own allowance.

---

## 8. The embed script

Served from this application at `/widgets/v1/widget.js` — no build step, no framework, no
CDN required for the widget itself. This is what a customer pastes into their page:

```html
<script src="https://voice.example.uz/widgets/v1/widget.js"
        data-widget-key="wgt_0123456789abcdef" async></script>
```

`WidgetRow.embedSnippet` returns exactly this string, ready to copy.

| Attribute | Required | Meaning |
|---|---|---|
| `data-widget-key` | ✅ | The widget's key |
| `data-api-base` | ❌ | API origin, when the script itself is served from a CDN on another host. Defaults to the script's own origin |
| `data-language` | ❌ | Force the call language (`uz-UZ`, `ru-RU`); default is the agent's |
| `data-sip-js` | ❌ | A self-hosted SIP.js URL, for a deployment with no public CDN access |

**What it does:** fetches `/config`, draws a launcher styled from the widget's theme,
shows the consent text as a checkbox that gates the button when `consentRequired`, asks
for the microphone, calls `/session`, then connects over WebRTC with SIP.js.

**How it behaves:**

- Everything renders inside a **shadow root**, so the host page's CSS cannot break the
  widget and the widget's cannot leak into the page.
- SIP.js (~270 KB) is loaded from a CDN **only when a visitor actually starts a call** —
  a page that nobody calls from pays nothing for it.
- Microphone permission is asked **before** the session is booked: a visitor who declines
  never spends a call from the agent's daily allowance.
- A widget that is disabled, deleted, or embedded on an origin it was not issued for
  renders **nothing at all**; the host page keeps working and the console says why.
- Leaving the page sends a BYE, so a visitor who navigates away does not leave a channel
  open on Asterisk.

**Server requirements:** `voice-agent.asterisk.web-test.*` must be configured (the same
settings the operator console's browser test call uses), and the WebSocket must be `wss://`
whenever the customer's page is served over `https` — a browser refuses a plain `ws://`
connection from a secure page.
