# Uysot CRM OAuth — frontend qo'llanmasi

`/api/settings/integrations` · huquq: **INTEGRATION_READ** / **INTEGRATION_EDIT**

Kompaniya o'z Uysot CRM hisobini platformaga ulaydi. Endpoint'larning to'liq tavsifi —
[settings.md §5](settings.md#5-crm-integratsiyalari-apisettingsintegrations); bu fayl esa
frontend bajaradigan oqimni boshidan oxirigacha ko'rsatadi.

---

## 1. Umumiy oqim

```
 Frontend (/settings/integrations)            Backend                          Uysot
 ────────────────────────────────             ───────                          ─────
 1. GET  /api/settings/integrations  ───────▶ holat (status, grants)
 2. PUT  /api/settings/integrations/uysot ──▶ appName + grants saqlanadi   (faqat kerak bo'lsa)
 3. GET  /api/settings/integrations/uysot/authorize-url
                                     ◀─────── { authorizeUrl }  (state Redis'da, 1 soat)
 4. window.location.href = authorizeUrl ───────────────────────────────────▶ rozilik ekrani
                                                                               │ "Ruxsat berish"
 5. /settings/integrations?code=…&state=…  ◀──────────────────────────────────┘
 6. window.location.replace(
      API + /api/settings/integrations/uysot/callback?code=…&state=…) ──▶ code → token
                                                                         (client_secret bilan)
 7. /settings/integrations?crm=connected     ◀─────── 302
    yoki ?crm=error&reason=<ErrorCode>
 8. GET  /api/settings/integrations  ───────▶ status: CONNECTED
```

Asosiy qoida: **Uysot brauzerni frontend sahifasiga qaytaradi, frontend esa `code` va
`state` ni o'zgartirmasdan backend'ning callback manziliga uzatadi.** Kodni tokenga
backend almashtiradi, chunki bu ishda `client_secret` kerak va u frontend'ga hech qachon
chiqmaydi.

---

## 2. Qadamlar

### 2.1 Holatni o'qish

`GET /api/settings/integrations` · `INTEGRATION_READ`

```json
{
  "accept": true,
  "data": {
    "companyId": 1,
    "provider": "UYSOT",
    "appName": "Uysot Voice Agent",
    "grants": [
      { "permission": "LEAD", "scope": "READ" },
      { "permission": "LEAD_NOTE", "scope": "SAVE" },
      { "permission": "CONTRACT", "scope": "READ" },
      { "permission": "CALL", "scope": "SAVE" }
    ],
    "status": "NOT_CONNECTED",
    "connectedAt": null,
    "accountHost": null
  }
}
```

| `status` | UI |
|---|---|
| `NOT_CONNECTED` | "Ulash" tugmasi |
| `CONNECTED` | "Ulangan" + `connectedAt`, "Uzish" tugmasi |
| `ERROR` | "Ulanishda xato" + "Qayta ulash" tugmasi (token almashinuvi yoki yangilanishi muvaffaqiyatsiz tugagan) |

`provider` `UYSOT` bo'lmasa, kompaniya hozir amoCRM/Kommo/Bitrix24 ga ulangan bo'ladi.
Uysot'ni ulash o'sha ulanishning o'rnini egallaydi.

### 2.2 Ilova nomi va ruxsatlarni saqlash (ixtiyoriy)

`PUT /api/settings/integrations/uysot` · `INTEGRATION_EDIT`

Bu qadam faqat quyidagi hollarda kerak: `appName` yoki `grants` bo'sh bo'lsa, kompaniya
boshqa CRM'dan Uysot'ga qaytayotgan bo'lsa yoki foydalanuvchi ruxsatlar ro'yxatini
o'zgartirgan bo'lsa. Birinchi kompaniya uchun seed ma'lumot bu qatorni allaqachon
yaratgan.

```json
{
  "appName": "Uysot Voice Agent",
  "grants": [
    { "permission": "LEAD", "scope": "READ" },
    { "permission": "LEAD_NOTE", "scope": "SAVE" },
    { "permission": "CONTRACT", "scope": "READ" },
    { "permission": "CALL", "scope": "SAVE" }
  ]
}
```

`permission`: `LEAD`, `LEAD_NOTE`, `LEAD_TASK`, `CONTRACT`, `CONTRACT_PAYMENT`, `CALL`.
`scope`: `READ`, `SAVE`, `DELETE`. Standart holatda yuqoridagi to'rtta grant yuboriladi,
chunki platforma aynan shularni ishlatadi (izohi — settings.md).

### 2.3 Avtorizatsiya havolasini olish va Uysot'ga o'tish

`GET /api/settings/integrations/uysot/authorize-url` · `INTEGRATION_READ`

```json
{
  "accept": true,
  "data": {
    "authorizeUrl": "https://app.uysot.uz/oauth/authorize?response_type=code&client_id=uysot_app_…&redirect_uri=…&scope=PERMISSION_OPEN_API_LEAD%3AREAD%20…&state=…"
  }
}
```

Frontend havolani **o'zgartirmasdan** shu oynaning o'zida ochadi:

```ts
window.location.href = data.authorizeUrl;
```

- Popup yoki yangi tab ochilmaydi, chunki Uysot foydalanuvchini shu sahifaga qaytaradi.
- `state` ni frontend saqlashi shart emas: server uni kompaniya bilan birga Redis'da
  **1 soat** saqlaydi.
- Har bir "Ulash" bosilishida yangi havola so'raladi, chunki `state` bir martalik.

**Xatolar** (`ResponseData.messageCode`):

| HTTP | `messageCode` | Sabab / UI |
|---|---|---|
| 400 | `CRM_INTEGRATION_APP_NOT_CONFIGURED` | `appName`/`grants` saqlanmagan — avval 2.2 bajariladi |
| 409 | `ENCRYPTION_KEY_NOT_SET` | serverda shifrlash kaliti yo'q — administratorga murojaat |
| 502 | `UYSOT_OAUTH_NOT_CONFIGURED` | serverda Uysot OAuth sozlanmagan (client id/secret, URL'lar) |
| 502 | `OAUTH_STATE_STORE_UNAVAILABLE` | Redis ishlamayapti — birozdan keyin qayta urinish |

### 2.4 Uysot'dan qaytish: `code` ni backend'ga uzatish

Foydalanuvchi ruxsat bergach, Uysot brauzerni ro'yxatdan o'tgan `redirect_uri` ga
qaytaradi:

```
https://<frontend>/settings/integrations?code=<code>&state=<state>
```

Sahifa ochilganda `code` va `state` ning ikkalasi ham bo'lsa, frontend brauzerni **to'liq
navigatsiya** bilan backend callback'iga yuboradi:

```ts
window.location.replace(
  `${API_BASE_URL}/api/settings/integrations/uysot/callback` +
  `?code=${encodeURIComponent(code)}&state=${encodeURIComponent(state)}`
);
```

`GET /api/settings/integrations/uysot/callback` — **ochiq endpoint, token talab
qilinmaydi.** Kompaniya `state` orqali aniqlanadi.

- `fetch`/`axios` ishlatilmaydi. Javob JSON emas, `302` bo'ladi va brauzer uni o'zi
  kuzatishi kerak.
- `Authorization` header yuborilmaydi, chunki bu oddiy sahifa navigatsiyasi.
- `replace` ishlatiladi, `href` emas: shunda `?code=` qatori tarixda qolmaydi va "Orqaga"
  tugmasi ishlatilgan kodni qayta yubormaydi.
- `code` va `state` hech qayerda saqlanmaydi (localStorage, log, analytics).
- `state` **bir martalik**: ikkinchi marta yuborilsa, backend
  `crm=error&reason=OAUTH_STATE_INVALID` qaytaradi.

> ⚠️ **Marshrut himoyasi.** `/settings/integrations` login talab qiladigan sahifa bo'lsa,
> auth guard foydalanuvchini login'ga yo'naltirganda **query parametrlarni saqlab qolishi
> shart**. Aks holda `code` yo'qoladi. Eng ishonchli yo'l: `code`+`state` bor bo'lsa,
> callback'ga yo'naltirishni auth tekshiruvidan **oldin** bajarish. Callback'ning o'zi
> sessiya talab qilmaydi.

Uysot rozilik berilmaganda RFC 6749 bo'yicha `?error=access_denied&state=…` bilan
qaytarishi mumkin (`code` bo'lmaydi). Bunday holatda backend'ga hech narsa yuborilmaydi:
frontend "Ulanish bekor qilindi" deb ko'rsatadi va URL'ni tozalaydi.

### 2.5 Natijani ko'rsatish

Backend kodni almashtirib bo'lgach, brauzerni yana shu sahifaga yuboradi:

| Natija | URL |
|---|---|
| Muvaffaqiyat | `/settings/integrations?crm=connected` |
| Xato | `/settings/integrations?crm=error&reason=<ErrorCode>` |

`reason` — API'ning boshqa joylaridagi `ErrorCode` nomining o'zi, shuning uchun u
`messageCode` kabi tarjima qilinadi:

| `reason` | Ma'nosi / UI |
|---|---|
| `OAUTH_STATE_INVALID` | havola eskirgan (1 soatdan o'tgan) yoki qayta ishlatilgan — "Qayta ulang" |
| `UYSOT_OAUTH_TOKEN_EXCHANGE_HTTP_ERROR` | Uysot kodni qabul qilmadi (muddati o'tgan kod, `redirect_uri` mos emas) — "Qayta ulang" |
| `UYSOT_OAUTH_TOKEN_EXCHANGE_FAILED` | Uysot bilan aloqa uzildi — "Keyinroq urinib ko'ring" |
| `UYSOT_OAUTH_TOKEN_MISSING` | Uysot javobida token yo'q — administratorga murojaat |
| `UYSOT_OAUTH_NOT_CONFIGURED` | serverda OAuth sozlanmagan |
| `ENCRYPTION_KEY_NOT_SET` | serverda shifrlash kaliti yo'q |
| `CRM_INTEGRATION_NOT_FOUND` | kompaniyaning integratsiya qatori o'chib ketgan — 2.2 dan qayta boshlash |

Keyin:

1. toast/xabar ko'rsatiladi;
2. `history.replaceState` bilan `crm` va `reason` URL'dan olib tashlanadi, shunda sahifa
   yangilanganda xabar qayta chiqmaydi;
3. `GET /api/settings/integrations` qayta o'qiladi. Haqiqiy holat `status` maydonida
   bo'ladi, query parametr esa faqat oxirgi urinish natijasini bildiradi.

### 2.6 Uzish

`DELETE /api/settings/integrations/uysot` · `INTEGRATION_EDIT` → `data: null`.
Token Uysot tomonida ham bekor qilinadi. Shundan keyin `status` `NOT_CONNECTED` bo'ladi.

---

## 3. Namuna (React + TypeScript)

```tsx
import { useEffect, useRef } from "react";
import { useSearchParams } from "react-router-dom";

const API = import.meta.env.VITE_API_BASE_URL; // masalan http://localhost:8080

export function useUysotOAuthReturn(onDone: (result: "connected" | "cancelled" | { error: string }) => void) {
  const [params] = useSearchParams();
  const handled = useRef(false); // StrictMode'da effect ikki marta ishlaydi

  useEffect(() => {
    if (handled.current) return;
    const code = params.get("code");
    const state = params.get("state");
    const crm = params.get("crm");
    const oauthError = params.get("error");

    // 1) Uysot'dan qaytdik — code'ni backend'ga uzatamiz
    if (code && state) {
      handled.current = true;
      window.location.replace(
        `${API}/api/settings/integrations/uysot/callback` +
          `?code=${encodeURIComponent(code)}&state=${encodeURIComponent(state)}`
      );
      return;
    }

    // 2) Foydalanuvchi Uysot'da rad etdi
    if (oauthError) {
      handled.current = true;
      clearQuery();
      onDone("cancelled");
      return;
    }

    // 3) Backend natijani qaytardi
    if (crm) {
      handled.current = true;
      clearQuery();
      onDone(crm === "connected" ? "connected" : { error: params.get("reason") ?? "INTERNAL_SERVER_ERROR" });
    }
  }, [params, onDone]);
}

function clearQuery() {
  window.history.replaceState(null, "", window.location.pathname);
}

export async function startUysotConnect(http: HttpClient) {
  const { data } = await http.get<{ authorizeUrl: string }>("/api/settings/integrations/uysot/authorize-url");
  window.location.href = data.authorizeUrl;
}
```

Sahifada:

```tsx
useUysotOAuthReturn((result) => {
  if (result === "connected") toast.success(t("crm.connected"));
  else if (result === "cancelled") toast.info(t("crm.cancelled"));
  else toast.error(t(`errors.${result.error}`));
  refetchIntegration(); // GET /api/settings/integrations
});
```

---

## 4. Server sozlamasi (backend / DevOps uchun)

Frontend bularni o'zi sozlamaydi, lekin oqim ishlashi uchun ular to'g'ri bo'lishi kerak:

| Env | Qiymat (lokal) | Izoh |
|---|---|---|
| `UYSOT_OAUTH_REDIRECT_URI` | `http://localhost:5173/settings/integrations` | **Frontend sahifasi.** Uysot ilovasida ro'yxatdan o'tgan qiymat bilan belgima-belgi bir xil bo'lishi shart (oxirgi `/` ham hisobga olinadi), aks holda `6902 invalid redirect_uri`. Token almashinuvida ham aynan shu qiymat yuboriladi |
| `UYSOT_OAUTH_CALLBACK_REDIRECT_URL` | `http://localhost:5173/settings/integrations` | Backend almashinuvdan keyin brauzerni shu manzilga yuboradi (`?crm=…`) |
| `UYSOT_OAUTH_AUTHORIZE_URL` | Uysot bergan rozilik sahifasi | dev va prod ilovalarda host har xil |
| `UYSOT_OAUTH_TOKEN_URL`, `UYSOT_OAUTH_REVOKE_URL` | Uysot Open API | authorize bilan bir muhitdan (dev/prod) bo'lishi kerak |
| `UYSOT_OAUTH_CLIENT_ID`, `UYSOT_OAUTH_CLIENT_SECRET` | `uysot_app_…`, `uysot_sec_…` | secret faqat backend'da turadi |

`API_BASE_URL` frontend'da backend'ning brauzer ochadigan manzili bo'lishi kerak.
Callback'ga to'liq navigatsiya qilinadi, shuning uchun CORS bu yerda rol o'ynamaydi.
