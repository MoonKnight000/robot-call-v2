# NIDO — Boshqaruv paneli dizayn spetsifikatsiyasi

> **Bu hujjat nima?** AI qo'ng'iroq platformasining veb-panelini **chizish uchun to'liq
> topshiriq**. Dizayner (yoki dizayn generatori) shu hujjatdan boshqa hech narsa
> so'ramasdan barcha ekranlarni chiza olishi kerak: brend, logotip, ranglar, tipografika,
> tarkib (layout), har bir sahifa, har bir komponent, holatlar va o'zaro ta'sir.
>
> **Muhim:** bu **dizayn** topshirig'i, kod topshirig'i emas. Framework tanlash, API
> ulash, state management — bu hujjatning mavzusi emas.

**Versiya:** 1.0 · **Sana:** 2026-07-29 · **Til:** interfeys o'zbekcha (lotin), ikkinchi til — ruscha/inglizcha

---

## Mundarija

1. [Mahsulot haqida](#1-mahsulot-haqida)
2. [Brend: nom, logotip, ovoz](#2-brend-nom-logotip-ovoz)
3. [Dizayn tokenlari](#3-dizayn-tokenlari)
4. [Tipografika](#4-tipografika)
5. [Ikonkalar va illyustratsiya](#5-ikonkalar-va-illyustratsiya)
6. [Umumiy tarkib (App Shell)](#6-umumiy-tarkib-app-shell)
7. [Chap panel (Sidebar) — batafsil](#7-chap-panel-sidebar--batafsil)
8. [Xodim kartochkasi va hover-profil](#8-xodim-kartochkasi-va-hover-profil)
9. [Yuqori panel (Topbar)](#9-yuqori-panel-topbar)
10. [Sahifalar — ekran-ekran](#10-sahifalar--ekran-ekran)
11. [Komponentlar kutubxonasi](#11-komponentlar-kutubxonasi)
12. [Holatlar: bo'sh, yuklanmoqda, xato](#12-holatlar-bosh-yuklanmoqda-xato)
13. [Animatsiya va harakat](#13-animatsiya-va-harakat)
14. [Qorong'i rejim](#14-qorongi-rejim)
15. [Moslashuvchanlik (responsive)](#15-moslashuvchanlik-responsive)
16. [Qulaylik (accessibility)](#16-qulaylik-accessibility)
17. [Matn uslubi (microcopy)](#17-matn-uslubi-microcopy)
18. [Chizish tartibi va yetkazib berish](#18-chizish-tartibi-va-yetkazib-berish)

---

## 1. Mahsulot haqida

**Nido** — sun'iy intellekt asosidagi telefon qo'ng'iroqlari platformasi. Robot operator
mijozga o'zi qo'ng'iroq qiladi (outbound) yoki kiruvchi qo'ng'iroqqa javob beradi
(inbound), tirik odamdek suhbatlashadi, natijani yozib qo'yadi.

**Kim ishlatadi:**

| Rol | Kim | Nima qiladi | Panelda ko'proq nima ochadi |
|-----|-----|-------------|------------------------------|
| **Admin** | kompaniya rahbari / IT | hammasini sozlaydi | Sozlamalar, Foydalanuvchilar, Billing |
| **Operator** | call-markaz xodimi | kampaniya yuritadi, jonli qo'ng'iroqni kuzatadi | Jonli, Kampaniyalar, Qo'ng'iroqlar |
| **Kuzatuvchi** | menejer / analitik | faqat o'qiydi | Boshqaruv paneli, Hisobotlar |

**Ish sharoiti (dizayn uchun muhim):** kun bo'yi ochiq turadigan panel, ko'pincha
ikkinchi monitorda. Ekranda **ko'p qator ma'lumot** bo'ladi (minglab qo'ng'iroq).
Shuning uchun: zich (compact) jadvallar, ko'zni qamashtirmaydigan ranglar, jonli
o'zgarishlar tinch animatsiya bilan.

**Dizayn tamoyillari:**

1. **Jimjitlik — asosiy holat.** Rang faqat ma'noni bildiradi. Qizil — muammo, yashil —
   jonli qo'ng'iroq. Bezak uchun rang ishlatilmaydi.
2. **Suhbat — mahsulotning yuragi.** Transkript va to'lqin (waveform) eng puxta
   chizilgan ekran bo'lishi kerak.
3. **Zichlik, lekin bo'g'iq emas.** 8px grid, jadval qatori 44px — ko'p ma'lumot,
   yetarli havo.
4. **Har bir raqam bosiladigan.** Har qanday metrika o'zining tafsilot ekraniga olib
   boradi.
5. **Robot ekanligi yashirilmaydi.** Interfeysda ham, qo'ng'iroqda ham AI ekani
   ochiq aytiladi — bu huquqiy talab va brend pozitsiyasi.

---

## 2. Brend: nom, logotip, ovoz

### 2.1 Nom

**NIDO** — "nido" = chorlov, ovoz, murojaat. Qisqa (4 harf), o'zbekcha va ruscha
o'qilishi bir xil, xalqaro domenda ham yoqimli, "call/voice" ma'nosini beradi.

- Yozilishi: **Nido** (odatiy matnda), **NIDO** (logotip wordmark'da).
- Hech qachon: "NiDo", "nido AI", "Nido.ai" — faqat **Nido**.
- Slogan (ixtiyoriy, marketing uchun): *"Sizning o'rningizga gaplashadi"*.

### 2.2 Logotip — asosiy g'oya

**Mark: "Chorlov to'lqini" (Signal Handset).**

Yumaloqlangan kvadrat maydon ichida — **telefon go'shagi siluetidan o'sib chiqqan ovoz
to'lqini**. Ya'ni bitta uzluksiz shakl bir vaqtning o'zida ikki narsani o'qiydi:
qo'ng'iroq (go'shak) va ovoz/AI (to'lqin).

```
   ┌──────────────────────┐
   │                      │
   │        ╱⌒╲     ▎     │   ← go'shakning yuqori uchi
   │       ╱    ╲   ▎ ▎   │   ← undan tarqalayotgan
   │      │      │  ▎ ▎ ▎ │     3 ta to'lqin ustuni
   │       ╲    ╱   ▎ ▎   │     (past → baland → past)
   │        ╲⌒╱     ▎     │
   │                      │
   └──────────────────────┘
```

**Aniq geometriya (24×24 grid, dizayner shu bo'yicha chizadi):**

- Maydon: 24×24, safe zone 2px (chizma 20×20 ichida).
- **Go'shak:** klassik telefon go'shagi silueti **-45° burchakka burilgan** (qo'ng'iroq
  qilinayotgan holat), chiziq qalinligi **2.25px**, uchlari **dumaloq** (round cap),
  burchaklari **radius 1.5px**. Go'shak chizmaning **chap-past** qismini egallaydi
  (taxminan 13×13 maydon).
- **To'lqin:** go'shakning yuqori-o'ng uchidan **3 ta vertikal ustun** chiqadi —
  balandliklari **6px / 11px / 7px**, kengligi **2.25px**, radius to'liq (pill),
  oralig'i **2.5px**. Ustunlar go'shakdan **3px** masofada boshlanadi.
- **Muhim nuans:** o'rtadagi eng baland ustun go'shakning yuqori uchidan **biroz
  chiqib turadi** — bu "ovoz go'shakdan tashqariga chiqyapti" hissini beradi.
- To'lqin ustunlari **gradient** bilan bo'yaladi (pastdan tepaga: `primary-500` →
  `accent-500`), go'shak esa **bir xil** `primary-600` rangda. Monoxrom versiyada
  hammasi bitta rang.

**Nima uchun shunday:**

- Go'shak — "qo'ng'iroq"ni **hech qanday izohsiz** anglatadi, hamma tanigan belgi.
- To'lqin — "ovoz + AI"ni qo'shadi, mahsulotni oddiy dialerdan ajratadi.
- Kichik o'lchamda (16px favicon) to'lqin uchta nuqtaga aylanadi, go'shak esa
  o'qilib qolaveradi — ya'ni **hech qachon tanib bo'lmas holga kelmaydi**.

**Wordmark:**

`NIDO` — Inter Display / Söhne Bold, harflar orasi **-0.02em**, mark bilan orasi
**mark balandligining 0.5 qismi**. Baseline mark markazi bilan tekislanadi.
"i" harfining nuqtasi **`accent-500` rangda** bo'ladi (kichik, ammo esda qoladigan
detal — to'lqin bilan bir rangda).

**Logotip variantlari (hammasini chizish kerak):**

| Variant | Qachon | O'lcham |
|---------|--------|---------|
| Lockup gorizontal (mark + NIDO) | login sahifasi, hujjatlar sarlavhasi | balandligi 32px |
| Mark + wordmark, sidebar versiyasi | ochiq sidebar tepasi | mark 28px |
| Faqat mark | yopilgan sidebar, favicon, app icon | 28 / 32 / 16px |
| Monoxrom oq | qorong'i fonda, rasmiy hujjatda | — |
| Monoxrom qora | oq-qora chop etish | — |

**Logotipda qilinmaydi:**

- Mark ichiga "AI" yozuvi qo'shish
- Robot, mikrofon, naushnik, chat-bubble qo'shish (klishe va shovqinli)
- Gradientni go'shakka ham yoyish (kichik o'lchamda loyqalanadi)
- Markni cho'zish, aylantirish, soya berish
- Wordmark'ni mark'siz `accent` rangda berish

**Favicon / app icon:** to'q `primary-700` fon (`#3B34A8`) ustida **oq mark**, radius
`22%` (superellipse), padding `18%`. Push-bildirishnoma ikonkasi ham shu.

### 2.3 Brend ovozi (matn uslubi)

- **Xotirjam va aniq.** "Kampaniya to'xtatildi" — "Voy, nimadir noto'g'ri ketdi!" emas.
- **Sizlash.** Interfeysda doim "siz": "Kampaniyani ishga tushirasizmi?"
- **Texnik atamalar tarjima qilinmaydi:** trunk, SIP, webhook, CSV, API kalit.
  Qolgani o'zbekcha: qo'ng'iroq, kampaniya, ssenariy, nishon (target), transkript.
- **Robotni "u" deb atash yo'q.** "AI operator", "robot" yoki shunchaki "Nido".

---

## 3. Dizayn tokenlari

### 3.1 Ranglar — brend

| Token | HEX | Ishlatilishi |
|-------|-----|--------------|
| `primary-50` | `#EEF0FF` | juda yengil fon, tanlangan qator |
| `primary-100` | `#E0E3FF` | badge foni |
| `primary-200` | `#C6CBFC` | chegaralar (light) |
| `primary-300` | `#A3A9F7` | ikonka (disabled), fokus halqasi |
| `primary-400` | `#8286F0` | dark rejimda asosiy matn-havola |
| `primary-500` | `#6366E8` | gradient boshi, hover |
| **`primary-600`** | **`#4F4CD6`** | **asosiy brend rangi, tugmalar, logotip** |
| `primary-700` | `#3B34A8` | tugma bosilganda, to'q fon |
| `primary-800` | `#2E2A85` | — |
| `primary-900` | `#211E5E` | — |

**Accent (jonli qo'ng'iroq, ovoz, muvaffaqiyat):**

| Token | HEX | Ishlatilishi |
|-------|-----|--------------|
| `accent-100` | `#D1FAE5` | badge foni |
| `accent-400` | `#34D399` | dark rejimdagi matn |
| **`accent-500`** | **`#10B981`** | **jonli indikator, to'lqin gradienti** |
| `accent-600` | `#059669` | matn light rejimda |

### 3.2 Ranglar — semantik

| Token | Light | Dark | Ma'no |
|-------|-------|------|-------|
| `success` | `#059669` | `#34D399` | muvaffaqiyat, va'da olindi |
| `warning` | `#D97706` | `#FBBF24` | e'tibor, limitga yaqin |
| `danger` | `#DC2626` | `#F87171` | xato, rad javob, o'chirish |
| `info` | `#0284C7` | `#38BDF8` | ma'lumot, tizim xabari |
| `neutral` | `#64748B` | `#94A3B8` | javob yo'q, noma'lum |

### 3.3 Ranglar — sirtlar

| Token | Light | Dark |
|-------|-------|------|
| `bg` (sahifa foni) | `#F6F7FB` | `#0B0D12` |
| `surface` (kartochka) | `#FFFFFF` | `#12151D` |
| `surface-raised` (modal, popover) | `#FFFFFF` | `#1A1E28` |
| `surface-sunken` (jadval sarlavhasi, kod) | `#F1F3F9` | `#0F1218` |
| `sidebar-bg` | `#FFFFFF` | `#0E1017` |
| `border` | `#E5E8F0` | `#242936` |
| `border-strong` | `#CFD4E2` | `#333A4A` |
| `text` | `#111524` | `#F2F4F9` |
| `text-muted` | `#5C6579` | `#98A1B5` |
| `text-subtle` | `#8A93A6` | `#6B7488` |
| `overlay` (modal orqasi) | `rgba(17,21,36,.45)` | `rgba(0,0,0,.6)` |

### 3.4 Qo'ng'iroq natijasi ranglari (disposition)

Bu maxsus palitra — **faqat** qo'ng'iroq natijasi uchun, boshqa joyda ishlatilmaydi.
Har bir natija: nuqta (8px doira) + matn. Fon rang **faqat** badge'da.

| Natija | Rang | Nuqta |
|--------|------|-------|
| Jonli (davom etmoqda) | `accent-500` | **pulsatsiyalanadi** |
| To'lov va'dasi olindi | `success` | ● |
| Suhbat bo'ldi, natijasiz | `info` | ● |
| Rad javob | `danger` | ● |
| Javob bermadi | `neutral` | ○ (ichi bo'sh) |
| Band / texnik xato | `warning` | ● |
| Avtojavob (AMD) | `#8B5CF6` | ◐ |
| Boshqa odam | `#D97706` | ● |
| Qo'ng'iroq qilinmasin (DNC) | `#334155` | ■ |

### 3.5 O'lchamlar

**Grid: 8px.** Barcha bo'shliqlar 4 ning karrasi: `4, 8, 12, 16, 20, 24, 32, 40, 48, 64`.

| Token | px |
|-------|-----|
| `radius-sm` | 6 |
| `radius-md` | 10 |
| `radius-lg` | 14 |
| `radius-xl` | 20 |
| `radius-full` | 9999 |

| Token | Qiymat |
|-------|--------|
| `shadow-sm` | `0 1px 2px rgba(17,21,36,.06)` |
| `shadow-md` | `0 4px 12px rgba(17,21,36,.08)` |
| `shadow-lg` | `0 12px 32px rgba(17,21,36,.12)` |
| `shadow-popover` | `0 8px 28px rgba(17,21,36,.14), 0 0 0 1px var(--border)` |

> Qorong'i rejimda soyalar deyarli ko'rinmaydi — ularning o'rniga **`border` va
> `surface-raised` farqi** bilan qatlam ajratiladi.

**Asosiy o'lchamlar:**

| Element | px |
|---------|-----|
| Sidebar (ochiq) | **264** |
| Sidebar (yopiq) | **72** |
| Topbar balandligi | **60** |
| Kontent maksimal kengligi | **1440** (markazda), jadvallar — to'liq kenglik |
| Kontent padding | 24 (≥1280), 16 (<1280) |
| Jadval qatori | **44** (zich rejimda 36) |
| Tugma balandligi | 36 (md), 32 (sm), 44 (lg) |
| Input balandligi | 38 |

---

## 4. Tipografika

**Shrift: Inter** (o'zbek lotin diakritikalari va kirill uchun to'liq qamrov).
Muqobil: Manrope. Raqamlar uchun **`font-variant-numeric: tabular-nums`** — jadvalda
raqamlar ustun bo'ylab tekislanishi shart.

**Mono shrift: JetBrains Mono** — ID, telefon raqam, timestamp, JSON, kod uchun.

| Uslub | O'lcham / qator | Og'irlik | Qayerda |
|-------|-----------------|----------|---------|
| Display | 30 / 38 | 600 | login, bo'sh holat sarlavhasi |
| H1 | 22 / 30 | 600 | sahifa sarlavhasi |
| H2 | 18 / 26 | 600 | bo'lim sarlavhasi, modal sarlavhasi |
| H3 | 15 / 22 | 600 | kartochka sarlavhasi |
| Body | 14 / 21 | 400 | asosiy matn, jadval |
| Body-strong | 14 / 21 | 500 | jadvaldagi asosiy ustun |
| Small | 13 / 19 | 400 | yordamchi matn, label |
| Micro | 12 / 16 | 500 | badge, jadval sarlavhasi (`+0.03em`, UPPERCASE) |
| Metric | 28 / 32 | 600 | KPI raqami, `tabular-nums` |
| Metric-lg | 36 / 40 | 600 | Boshqaruv panelidagi asosiy raqam |
| Mono | 13 / 20 | 400 | ID, raqam, vaqt |

**Qoidalar:**

- Bir ekranda **ikkitadan ortiq** sarlavha darajasi bo'lmasin.
- Uzun matn kengligi **72 belgi**dan oshmasin.
- Telefon raqami doim **mono** va bir xil formatda: `+998 90 123 45 67`.
- Sana/vaqt: `29.07.2026, 14:32` — jadvalda; `2 daqiqa oldin` — jonli ro'yxatda.
- Davomiylik: `2:14` (mm:ss), soatlik bo'lsa `1:02:14`.

---

## 5. Ikonkalar va illyustratsiya

**Ikonka to'plami: Lucide** (yoki shu uslubdagi: stroke 1.75px, 24×24 grid, round cap).
Interfeysda **20px** (jadval/tugma ichida 16px, sidebar'da 20px).

Sidebar uchun asosiy ikonkalar:

| Sahifa | Ikonka |
|--------|--------|
| Boshqaruv paneli | `layout-dashboard` |
| Jonli qo'ng'iroqlar | `radio` (pulsatsiyalanadigan) |
| Qo'ng'iroqlar tarixi | `phone` |
| Kampaniyalar | `megaphone` |
| Ssenariylar | `git-branch` |
| Kontaktlar | `users` |
| Kiruvchi marshrutlar | `phone-incoming` |
| Hisobotlar | `bar-chart-3` |
| Sozlamalar | `settings` |
| Foydalanuvchilar | `user-cog` |
| Audit | `scroll-text` |
| Billing | `credit-card` |

**Illyustratsiya uslubi (bo'sh holatlar uchun):** minimal chiziqli, bitta rang
(`primary-300`) + bitta accent detal, 160×160, hech qanday odam figurasi yo'q —
faqat obyektlar (to'lqin, go'shak, ro'yxat, bo'sh papka).

---

## 6. Umumiy tarkib (App Shell)

```
┌────────────┬──────────────────────────────────────────────────────────┐
│            │  TOPBAR (60px)                                           │
│  SIDEBAR   ├──────────────────────────────────────────────────────────┤
│  264px     │                                                          │
│            │                                                          │
│  ┌──────┐  │   KONTENT                                                │
│  │ logo │  │   padding 24px                                           │
│  └──────┘  │   max-width 1440 (markazda)                              │
│            │                                                          │
│  navigatsi-│                                                          │
│  ya        │                                                          │
│            │                                                          │
│            │                                                          │
│  ─ ─ ─ ─ ─ │                                                          │
│  [avatar]  │                                                          │
│  Xodim ismi│                                                          │
└────────────┴──────────────────────────────────────────────────────────┘
```

- Sidebar **butun balandlikda** (topbar'dan ham yuqori) — logotip doim yuqori chap
  burchakda turadi.
- Sidebar va kontent orasida **1px `border`** (soya emas).
- Sidebar **scroll qilmaydi** (navigatsiya sig'adi); agar sig'masa — o'rta qismi
  scroll bo'ladi, logotip va xodim kartochkasi **yopishib qoladi** (sticky).
- Kontent qismi mustaqil scroll bo'ladi; topbar **sticky**.

---

## 7. Chap panel (Sidebar) — batafsil

### 7.1 Tuzilishi

```
┌──────────────────────────────┐  264px
│                              │
│  ◗▎▎  NIDO           [«]     │  ← 60px: logotip + yig'ish tugmasi
│                              │
├──────────────────────────────┤
│                              │
│  ┌────────────────────────┐  │
│  │ 🏢 Uysot Group      ⌄ │  │  ← kompaniya tanlagich (faqat bir nechta
│  └────────────────────────┘  │     kompaniyaga ruxsati bo'lsa ko'rinadi)
│                              │
│  ▸ ASOSIY                    │  ← bo'lim sarlavhasi (Micro, text-subtle)
│  ┌────────────────────────┐  │
│  │ ▦ Boshqaruv paneli     │  │
│  │ ◉ Jonli qo'ng'iroqlar 3│  │  ← jonli soni (accent badge, pulsatsiya)
│  │ ☎ Qo'ng'iroqlar        │  │
│  └────────────────────────┘  │
│                              │
│  ▸ ISH                       │
│  │ 📣 Kampaniyalar       2│  │  ← faol kampaniyalar soni (kulrang badge)
│  │ ⑂ Ssenariylar          │  │
│  │ 👥 Kontaktlar          │  │
│  │ ↙ Kiruvchi marshrutlar │  │
│                              │
│  ▸ TAHLIL                    │
│  │ ▤ Hisobotlar           │  │
│                              │
│  ▸ TIZIM                     │
│  │ ⚙ Sozlamalar          ⌄│  │  ← ochiladigan (accordion)
│  │    Telefoniya          │  │
│  │    Ovoz va til         │  │
│  │    AI model            │  │
│  │    Integratsiyalar     │  │
│  │    API kalitlar        │  │
│  │ ⛭ Foydalanuvchilar     │  │
│  │ ▤ Audit jurnali        │  │
│  │ ▭ Hisob-kitob          │  │
│                              │
│         (bo'sh joy)          │
│                              │
├──────────────────────────────┤  ← 1px border
│  ┌────────────────────────┐  │
│  │ (AB)  Aziz Bekmurodov  │  │  ← XODIM KARTOCHKASI (§8)
│  │       Operator       ⋯ │  │
│  └────────────────────────┘  │
└──────────────────────────────┘
```

### 7.2 Logotip qismi (yuqori)

- Balandligi **60px** (topbar bilan bir xil), padding `0 16px`.
- Mark 28px + wordmark. Bosilganda → Boshqaruv paneliga.
- O'ng chekkada **yig'ish tugmasi** (`panel-left-close`, 20px, `text-subtle`) —
  faqat sidebar'ga hover qilinganda ko'rinadi (opacity 0 → 1, 120ms).

### 7.3 Kompaniya tanlagich

- Faqat foydalanuvchi bir nechta kompaniyaga a'zo bo'lsa ko'rinadi.
- 40px balandlik, `radius-md`, `surface-sunken` fon, ichida: 20px kompaniya logotipi
  (yoki initsial kvadrat), nomi (Body-strong, bir qatorga sig'masa `…`), o'ngda `⌄`.
- Bosilganda dropdown: kompaniyalar ro'yxati + qidiruv (5 tadan ko'p bo'lsa).

### 7.4 Navigatsiya elementlari

**Bo'lim sarlavhasi:** Micro, UPPERCASE, `text-subtle`, padding `16px 16px 6px`.

**Oddiy element:**

- Balandlik **38px**, padding `0 12px`, `radius-md`, margin `0 8px 2px`.
- Ikonka 20px (`text-muted`) + matn (Body, `text`) + o'ngda badge (ixtiyoriy).
- Ikonka va matn orasi **10px**.

**Holatlar:**

| Holat | Ko'rinishi |
|-------|------------|
| Odatiy | fon shaffof, ikonka `text-muted`, matn `text` |
| Hover | fon `surface-sunken` (dark: `#161A24`), 120ms |
| **Aktiv** | fon `primary-50` (dark: `rgba(79,76,214,.16)`), matn va ikonka **`primary-600`** (dark: `primary-400`), matn og'irligi **500**, chap chekkada **3px vertikal chiziq** `primary-600`, `radius-full`, balandligi 20px, markazda |
| Fokus (klaviatura) | 2px `primary-400` halqa, 2px offset |
| Disabled (ruxsat yo'q) | opacity .45, kursor `not-allowed`, hover yo'q |

**Badge (element o'ngida):**

- Jonli soni: `accent-500` fon, oq matn, Micro, `radius-full`, min-width 20px,
  balandlik 18px. Yonida **pulsatsiyalanuvchi nuqta** (§13).
- Oddiy son: `surface-sunken` fon, `text-muted` matn.
- Diqqat talab qiladigan (xato): `danger` fon, oq matn.

**Ochiladigan element (Sozlamalar):**

- O'ngda `chevron-down`, ochilganda 180° buriladi (180ms).
- Ichki elementlar: chapdan **44px** padding (ikonkasiz), balandlik 34px,
  Small o'lchamda, aktiv holatda faqat matn rangi o'zgaradi (fon yo'q).

### 7.5 Yopilgan (collapsed) sidebar — 72px

- Faqat ikonkalar, markazda, 40×40 tegish maydoni.
- Matn o'rniga **tooltip** o'ngda (200ms kechikish bilan).
- Bo'lim sarlavhalari o'rniga **1px ajratuvchi chiziq**.
- Logotip → faqat mark, markazda.
- Xodim kartochkasi → faqat avatar (32px), markazda. Hover-popover baribir ishlaydi.
- Badge → ikonkaning o'ng-yuqori burchagida **8px nuqta** (raqamsiz).
- O'tish animatsiyasi: kenglik 200ms `ease-out`, matn opacity 100ms.

---

## 8. Xodim kartochkasi va hover-profil

> Bu — mijoz alohida so'ragan qism, shuning uchun eng batafsil yozilgan.

### 8.1 Kartochka (sidebar eng pastida)

```
├──────────────────────────────┤  ← 1px border-top
│  ┌────────────────────────┐  │
│  │ ╭──╮                   │  │
│  │ │AB│  Aziz Bekmurodov  │  │   ← 12px padding, 56px balandlik
│  │ ╰──╯  Operator      ⋮  │  │
│  └────────────────────────┘  │
└──────────────────────────────┘
```

- **Konteyner:** butun kenglik, `padding: 8px`, ichkarida `radius-md` bosiladigan
  maydon (`8px 10px`), balandlik **56px**.
- **Avatar:** 34px, `radius-full`. Rasm bo'lmasa — initsiallar (`AB`), fon
  foydalanuvchi ID'sidan deterministik tanlanadi (6 ta yumshoq rangdan:
  `#4F4CD6, #0EA5E9, #10B981, #F59E0B, #EC4899, #8B5CF6`), matn oq, Micro, 600.
- **Avatar ustida status nuqtasi:** o'ng-past burchakda 10px doira, 2px `sidebar-bg`
  halqa bilan. Ranglar: `accent-500` — onlayn, `warning` — band (qo'ng'iroqda),
  `neutral` — oflayn.
- **Ism:** Body-strong, bir qator, sig'masa `…`.
- **Rol:** Small, `text-subtle` (`Administrator` / `Operator` / `Kuzatuvchi`).
- **O'ngda:** `more-vertical` ikonka (16px, `text-subtle`) — hover'da `text-muted`.
- **Hover:** butun maydon foni `surface-sunken`, 120ms.

### 8.2 Hover-popover: "Profil sozlamalari"

**Ochilish qoidalari:**

| Xususiyat | Qiymat |
|-----------|--------|
| Trigger | kartochka ustiga sichqoncha kelishi (hover) **yoki** klaviatura fokusi **yoki** bosish |
| Ochilish kechikishi | **150ms** (tasodifiy o'tib ketishda ochilmasligi uchun) |
| Yopilish kechikishi | **250ms** (sichqonchani popover'ga olib borishga ulguradi) |
| Joylashuvi | kartochkadan **yuqorida**, chap chekkasi kartochka bilan tekis, orasi **8px** |
| Yuqoriga sig'masa | o'ng tomonga ochiladi (side: right, align: end) |
| Kenglik | **268px** |
| Fon | `surface-raised`, `radius-lg`, `shadow-popover` |
| Ko'prik | kartochka va popover orasidagi 8px bo'shliq **hover zonasiga kiradi** — sichqoncha o'tayotganda yopilmaydi |
| Yopilishi | sichqoncha chiqib ketsa, `Esc` bosilsa, tashqariga bosilsa, sahifa o'zgarsa |
| Animatsiya | opacity 0→1 + `translateY(6px)→0`, **160ms `ease-out`** |

**Popover tarkibi:**

```
┌────────────────────────────────────┐
│  ╭────╮                            │
│  │ AB │  Aziz Bekmurodov           │  ← 44px avatar
│  ╰────╯  aziz@uysot.uz             │  ← Small, text-muted
│          ┌──────────┐              │
│          │ Operator │              │  ← rol badge, primary-100 fon
│          └──────────┘              │
├────────────────────────────────────┤
│  Bugun                             │  ← Micro, text-subtle
│  ┌──────────┬──────────┬─────────┐ │
│  │    24    │   18m    │   92%   │ │  ← mini statistika
│  │ qo'ng'ir.│  efirda  │ sifat   │ │     (H3 raqam, Micro label)
│  └──────────┴──────────┴─────────┘ │
├────────────────────────────────────┤
│  👤  Profilim                      │  ← 36px qator
│  🔔  Bildirishnomalar          ⌄  │
│  🎨  Ko'rinish            [◐]     │  ← inline tema tanlagich
│  🌐  Til                 O'zbek ⌄ │  ← inline til tanlagich
│  ⌨   Tezkor tugmalar        ⌘K   │
├────────────────────────────────────┤
│  ⚙   Kompaniya sozlamalari         │  ← faqat admin uchun
│  ❓  Yordam va qo'llanma           │
├────────────────────────────────────┤
│  ⏻   Chiqish                       │  ← danger rangda
└────────────────────────────────────┘
```

**Popover ichidagi qatorlar:**

- Balandlik **36px**, padding `0 12px`, ikonka 18px (`text-muted`) + matn (Body).
- Hover: fon `surface-sunken`, `radius-sm`, matn `text`.
- O'ngdagi qiymat/tanlagich: Small, `text-muted`.
- Ajratuvchi chiziqlar: 1px `border`, `margin: 6px 0`.
- **"Chiqish"** qatori: ikonka va matn `danger` rangda, hover'da fon
  `rgba(220,38,38,.08)`.

**Ko'rinish (tema) tanlagichi:** inline segmented control, 3 ta ikonka —
`sun` / `moon` / `monitor` (avtomatik), balandlik 26px, tanlangan segment
`surface` fon + `shadow-sm`.

**Til tanlagichi:** bosilganda o'ng tomonda kichik submenu — `O'zbek` / `Русский` /
`English`, tanlangani yonida `check` ikonkasi.

**Bildirishnomalar:** bosilganda submenu — har biri toggle: "Kampaniya tugadi",
"Xato yuz berdi", "Operatorga so'rov", "Kunlik hisobot".

**Muhim qulaylik qoidasi:** popover **faqat hover** bilan ochilmaydi — kartochkani
bosish ham xuddi shu popover'ni ochadi (mobil va klaviatura uchun). Popover ichida
`Tab` bilan yurish mumkin, `Esc` yopadi, fokus kartochkaga qaytadi.

### 8.3 "Profilim" — to'liq sahifa

Popover'dagi "Profilim" bosilganda **modal emas, alohida sahifa** ochiladi
(`/profil`), chap tomonda ichki tab'lar bilan:

- **Umumiy** — avatar (yuklash/o'chirish), ism, familiya, telefon, email (o'zgarmas,
  agar SSO bo'lsa), lavozim.
- **Xavfsizlik** — parol o'zgartirish, ikki bosqichli tasdiqlash, faol sessiyalar
  ro'yxati (qurilma, IP, oxirgi faollik, "Sessiyani tugatish").
- **Bildirishnomalar** — kanal (panel / email / Telegram) × hodisa turi matritsasi.
- **Ish jadvali** — operator qaysi soatlarda qo'ng'iroq qabul qiladi (inbound
  transfer uchun).

---

## 9. Yuqori panel (Topbar)

```
┌──────────────────────────────────────────────────────────────────────┐
│  Kampaniyalar / Qarzdorlik iyul          🔍 Qidirish ⌘K   ⟳  🔔  ⏻  │
└──────────────────────────────────────────────────────────────────────┘
```

- Balandlik **60px**, `surface` fon, pastida 1px `border`, **sticky**.
- **Chapda:** breadcrumb (Small, `text-muted`; oxirgi element `text`, 500) yoki
  sahifa nomi (H1) — ikkilanmang: **breadcrumb faqat ichki sahifalarda**, ildiz
  sahifalarda H1.
- **O'ngda (chapdan o'ngga):**
  1. **Global qidiruv** — 240px input, ikonka + "Qidirish", o'ngda `⌘K` kbd belgisi.
     Bosilganda **command palette** ochiladi (§11.9).
  2. **Jonli holat indikatori** — `● 3 ta jonli qo'ng'iroq` (accent, pulsatsiya),
     bosilganda Jonli sahifasiga.
  3. **Bildirishnomalar** — qo'ng'iroq ikonkasi (`bell`), o'qilmagani bo'lsa
     o'ng-yuqorida `danger` nuqta. Bosilganda o'ng tomondan panel.
  4. Avatar **takrorlanmaydi** — u faqat sidebar'da (chalkashlik bo'lmasligi uchun).

---

## 10. Sahifalar — ekran-ekran

### 10.1 Kirish (Login)

To'liq ekran, chapda **brend paneli**, o'ngda forma.

- **Chap panel (52%):** `primary-700` → `primary-900` diagonal gradient, ustida juda
  past kontrastli katta **to'lqin naqshi** (logotipdagi to'lqin ulkan qilib, opacity
  .07). Markazda: oq logotip lockup, ostida Display matn: *"Sizning o'rningizga
  gaplashadi"*, va 3 ta kichik statistika (`12 400 qo'ng'iroq`, `98% yetkazish`,
  `24/7`).
- **O'ng panel (48%):** markazda 360px forma — H1 "Xush kelibsiz", Small izoh,
  email, parol (ko'rsatish ikonkasi bilan), "Meni eslab qol" + "Parolni unutdingizmi?",
  asosiy tugma **"Kirish"** (to'liq kenglik, 44px), ajratuvchi *"yoki"*, ikkinchi
  darajali tugma **"Uysot bilan kirish"** (Uysot logotipi bilan).
- <1024px da chap panel yo'qoladi, logotip formadan yuqoriga chiqadi.

### 10.2 Boshqaruv paneli (Dashboard)

```
┌──────────────────────────────────────────────────────────────────────┐
│  Boshqaruv paneli                    [Bugun ⌄]  [Kampaniya: hammasi ⌄]│
├──────────────────────────────────────────────────────────────────────┤
│ ┌────────────┐┌────────────┐┌────────────┐┌────────────┐             │
│ │ 1 248      ││ 63%        ││ 2:41       ││ 184        │             │
│ │ Qo'ng'iroq ││ Javob berdi││ O'rtacha   ││ Va'da      │             │
│ │ ▲ 12%      ││ ▼ 3%       ││ ▲ 0:08     ││ ▲ 21%      │             │
│ │ ▁▂▃▅▄▆█    ││ ▃▄▃▅▄▄▅    ││ ▂▃▂▄▃▃▄    ││ ▁▃▄▄▆▇█    │             │
│ └────────────┘└────────────┘└────────────┘└────────────┘             │
├──────────────────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────┐┌──────────────────────────────┐ │
│ │ Qo'ng'iroqlar dinamikasi         ││ ● JONLI QO'NG'IROQLAR    (3) │ │
│ │                                  ││ ┌──────────────────────────┐ │ │
│ │   [stacked area chart, 24 soat]  ││ │(AK) Anvar K.      0:42 ▶│ │ │
│ │                                  ││ │     Qarzdorlik iyul      │ │ │
│ │                                  ││ │     ▁▃▅▂▄▆▃  "...to'lay" │ │ │
│ └──────────────────────────────────┘│ ├──────────────────────────┤ │ │
│ ┌──────────────────────────────────┐│ │(SM) Sardor M.     1:58 ▶│ │ │
│ │ Natijalar taqsimoti              ││ └──────────────────────────┘ │ │
│ │  [gorizontal bar / donut]        ││                              │ │
│ └──────────────────────────────────┘└──────────────────────────────┘ │
├──────────────────────────────────────────────────────────────────────┤
│  Faol kampaniyalar                                    [Hammasi →]    │
│  [3 ta kampaniya kartochkasi, progress bar bilan]                    │
├──────────────────────────────────────────────────────────────────────┤
│  Oxirgi qo'ng'iroqlar                                 [Hammasi →]    │
│  [8 qatorli ixcham jadval]                                           │
└──────────────────────────────────────────────────────────────────────┘
```

**KPI kartochkasi:** `surface`, `radius-lg`, 1px `border`, padding 20px.
Ichida: Micro label (`text-muted`), Metric-lg raqam, o'zgarish badge
(▲ yashil / ▼ qizil, Small), pastda **sparkline** (40px balandlik, `primary-400`
chiziq, ostida 12% opacity to'ldirish). Butun kartochka bosiladi.

**Jonli qo'ng'iroqlar paneli** — o'ng ustunda, sticky. Har bir qator: avatar,
mijoz ismi, kampaniya nomi (Small), taymer (mono, har soniya yangilanadi), jonli
**mini-waveform** (real vaqt, 6-8 ta ustun), oxirgi gap parchasi (Small, kursiv,
`text-muted`, bir qator), o'ngda "Tinglash" (`play`) tugmasi. Yangi qo'ng'iroq
qo'shilsa — yuqoridan **slide-in 240ms** bilan kiradi.

### 10.3 Jonli qo'ng'iroqlar

To'liq ekranli **monitoring** ko'rinishi — ikkinchi monitorda ochib qo'yish uchun.

- Yuqorida: 4 ta ixcham KPI (`Jonli: 3`, `Navbatda: 47`, `Bugungi: 248`,
  `Kanal bandligi: 3/20`).
- Asosiy qism: **kartochkalar grid** (auto-fill, min 320px). Har bir jonli qo'ng'iroq
  kartochkasi:
  - Yuqorida: mijoz ismi (H3) + telefon (mono, Small), o'ngda taymer va jonli nuqta.
  - O'rtada: **jonli to'lqin** (28 ustun, `accent-500`, real vaqtda harakatlanadi) —
    ikkita qator: yuqorida robot ovozi (`primary-500`), pastda mijoz ovozi
    (`accent-500`), ko'zgu ko'rinishida.
  - Ostida: **jonli transkript** (oxirgi 3 gap, avtoscroll, yangi gap fade-in bilan).
  - Pastda: bosqich (stage) badge (`Qarzdorlik xabari`), va tugmalar:
    **"Tinglash"** (audio), **"Operatorga uzatish"** (ikkinchi darajali),
    **"Tugatish"** (danger, matnli, tasdiq bilan).
- Bo'sh holat: markazda to'lqin illyustratsiyasi + "Hozircha jonli qo'ng'iroq yo'q".

### 10.4 Qo'ng'iroqlar (tarix)

Asosiy **jadval** ekrani — eng ko'p ishlatiladigan sahifa.

```
┌──────────────────────────────────────────────────────────────────────┐
│  Qo'ng'iroqlar                              [⇩ Eksport]  [Ustunlar ⚙]│
├──────────────────────────────────────────────────────────────────────┤
│  🔍 Raqam yoki ism    [Kampaniya ⌄][Natija ⌄][Sana ⌄][Davomiylik ⌄]  │
│  Filtrlar: (Qarzdorlik iyul ✕) (Rad javob ✕)          Tozalash       │
├────┬──────────────┬────────────┬──────────┬──────┬─────────┬────────┤
│ ☐  │ MIJOZ        │ KAMPANIYA  │ NATIJA   │ VAQT │ DAVOM.  │        │
├────┼──────────────┼────────────┼──────────┼──────┼─────────┼────────┤
│ ☐  │(AK) Anvar K. │ Qarzdorlik │ ● Va'da  │14:32 │  2:14   │ ▶  ⋯   │
│    │+998901234567 │ iyul       │ 05.08 ga │      │ ▁▃▅▂▄   │        │
├────┼──────────────┼────────────┼──────────┼──────┼─────────┼────────┤
│ ☐  │(SM) Sardor M.│ Qarzdorlik │ ○ Javob  │14:28 │  0:00   │    ⋯   │
│    │+998971112233 │ iyul       │   yo'q   │      │         │        │
└────┴──────────────┴────────────┴──────────┴──────┴─────────┴────────┘
│  1–25 / 1 248                       [‹] 1 2 3 … 50 [›]   [25 ⌄]     │
└──────────────────────────────────────────────────────────────────────┘
```

- Qator balandligi **56px** (ikki qatorli: ism + raqam). "Zich" rejimda 40px
  (bitta qator) — foydalanuvchi almashtiradi, tanlovi eslab qolinadi.
- **Natija ustuni:** rangli nuqta + matn + ostida qo'shimcha (Small, `text-muted`).
- **Davomiylik ustuni:** vaqt (mono) + ostida **mini-waveform** (24px kenglik,
  statik, faqat javob berilgan qo'ng'iroqlarda).
- **Hover:** qator foni `surface-sunken`, o'ngdagi tugmalar (`play`, `more`)
  paydo bo'ladi (odatda opacity .0).
- **Bosilganda:** o'ngdan **drawer** ochiladi (560px) — qo'ng'iroq tafsiloti
  (§10.5). Sahifa almashmaydi — bu muhim, ro'yxatdagi joy yo'qolmaydi.
- Ustun sarlavhasi bosilsa saralash (`chevron` bilan), ustunlarni yashirish/ko'rsatish
  `Ustunlar` menyusidan, tartibini sudrab o'zgartirish mumkin.
- **Ommaviy amal:** qator tanlansa yuqorida floating panel chiqadi — `3 ta tanlandi`,
  tugmalar: "Qayta qo'ng'iroq", "DNC ro'yxatiga", "Eksport", "Bekor qilish".

### 10.5 Qo'ng'iroq tafsiloti (drawer / sahifa)

Mahsulotning **eng muhim ekrani** — bu yerga eng ko'p vaqt sarflansin.

```
┌───────────────────────────────────────────────┐ 560px drawer
│  ✕   Anvar Karimov            ● To'lov va'dasi│
│      +998 90 123 45 67 · Qarzdorlik iyul      │
├───────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────┐  │
│  │ ▁▃▅▇▄▂▁▃▅▄▆█▃▂▁▄▅▃▂▁▃▄▅▆▄▃▂▁▃▄▂▁▃▅▄▂  │  │  ← to'lqin pleyer
│  │ ▔▔▔▔▔▔▔▔▔●▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔  │  │     (2 rangli: robot/mijoz)
│  │  ▶  0:47 / 2:14        1.0× ⌄   ⇩  ⋯  │  │
│  └─────────────────────────────────────────┘  │
├───────────────────────────────────────────────┤
│  [ Transkript ] [ Natija ] [ Texnik ]         │  ← tab'lar
├───────────────────────────────────────────────┤
│                                               │
│   0:02  ╭─────────────────────────────────╮   │
│         │ Assalomu alaykum! Men Uysot     │   │  ← ROBOT (chapda)
│         │ kompaniyasining AI yordamchisi- │   │     primary-50 fon
│         │ man. Anvar aka bilan gaplashy- │   │     radius: 14 14 14 4
│         │ apmanmi?                        │   │
│         ╰─────────────────────────────────╯   │
│                                               │
│              ╭──────────────────────────╮ 0:09│
│              │ Ha, menman. Nima gap?    │     │  ← MIJOZ (o'ngda)
│              ╰──────────────────────────╯     │     surface-sunken fon
│                                               │
│   0:12  ╭─────────────────────────────────╮   │
│         │ ⚙ Bosqich: QARZDORLIK XABARI    │   │  ← TIZIM hodisasi
│         ╰─────────────────────────────────╯   │     markazda, kichik
│                                               │
│   0:38  ╭─────────────────────────────────╮   │
│         │ 🔧 recordPaymentPromise         │   │  ← TOOL chaqiruvi
│         │    date: 2026-08-05             │   │     mono, border-dashed
│         │    amount: 1 200 000            │   │
│         ╰─────────────────────────────────╯   │
└───────────────────────────────────────────────┘
```

**Transkript qoidalari:**

- Robot — **chapda**, `primary-50` fon (dark: `rgba(79,76,214,.14)`), matn `text`.
- Mijoz — **o'ngda**, `surface-sunken` fon.
- Tizim hodisasi (bosqich o'zgarishi, VAD, barge-in) — **markazda**, Micro,
  `text-subtle`, fon yo'q, ikkala tomonda ingichka chiziq.
- Tool chaqiruvi — mono shrift, `border-dashed`, ikonka bilan, yig'iladi/ochiladi.
- **Vaqt belgisi** har bir gapning yonida (mono, Micro, `text-subtle`) — **bosilsa
  audio o'sha joydan ijro etiladi**.
- **Audio ijro etilayotganda joriy gap yoritiladi** (fon 8% `primary`), avtoscroll.
- Transkript ichida **qidirish** (tab yuqorisida input), topilgan so'z `warning`
  fon bilan belgilanadi.
- O'ng-yuqorida: "Nusxa olish", "TXT yuklab olish".

**"Natija" tab'i:** strukturali maydonlar — Natija kodi, To'lov va'dasi sanasi,
summa, Rad sababi, AI xulosasi (2-3 gap, `surface-sunken` blokda), Sifat bahosi
(agar bor bo'lsa), "CRM ga yuborildi ✓" statusi.

**"Texnik" tab'i:** `Call ID` (mono, nusxa olish tugmasi bilan), kanal, trunk,
HangupCause, AMD natijasi, STT/TTS provayder, LLM modeli, token sarfi, latency
(o'rtacha javob vaqti), xatolar jurnali. Bu tab **faqat admin uchun**.

### 10.6 Kampaniyalar

**Ro'yxat:** kartochka grid (min 340px) — jadval emas, chunki kampaniya soni kam
va har birida boy holat bor.

**Kampaniya kartochkasi:**

```
┌────────────────────────────────────────┐
│  ● Ishlamoqda          Qarzdorlik iyul │  ← status badge + nom (H3)
│  Ssenariy: Qarz undirish · v3          │  ← Small, text-muted
│                                        │
│  ████████████████░░░░░░░░  68%         │  ← progress (accent)
│  842 / 1 240 nishon                    │
│                                        │
│  ┌──────┬──────┬──────┬──────┐         │
│  │ 63%  │ 184  │ 2:41 │  47  │         │  ← 4 ta mini-metrika
│  │javob │va'da │o'rtac│navbat│         │
│  └──────┴──────┴──────┴──────┘         │
│                                        │
│  🕐 09:00–18:00 · Du–Ju                │  ← dial window
│  ─────────────────────────────────────  │
│  [⏸ To'xtatish]  [Ochish →]        ⋯   │
└────────────────────────────────────────┘
```

Status badge ranglari: `Qoralama` (neutral) · `Ishlamoqda` (accent, pulsatsiya) ·
`To'xtatilgan` (warning) · `Tugadi` (info) · `Xato` (danger).

**Kampaniya yaratish — 4 bosqichli sehrgar (wizard), modal emas, alohida sahifa:**

1. **Asosiy** — nom, tavsif, kompaniya (agar bir nechta bo'lsa).
2. **Ssenariy** — kartochkalar ro'yxati (Qarz undirish / Lead saralash / Xabarnoma /
   So'rovnoma / Uchrashuv eslatmasi + custom). Tanlangandan keyin o'ngda **oldindan
   ko'rish**: bosqichlar zanjiri, kerakli faktlar, natija maydonlari.
3. **Nishonlar** — CSV yuklash (drag&drop maydon) yoki CRM segmentidan.
   Yuklangandan keyin **ustun moslashtirish** jadvali: CSV ustuni → ssenariy
   fakti; xatolar (noto'g'ri raqam, DNC ro'yxatida) alohida ko'rsatiladi:
   `1 240 qator · 1 198 to'g'ri · 31 noto'g'ri raqam · 11 DNC`.
4. **Jadval va limitlar** — qo'ng'iroq oynasi (soat oralig'i slider), hafta kunlari
   (chip'lar), kunlik limit, urinishlar soni va oraliq, parallel qo'ng'iroqlar,
   caller ID.

Sehrgar yuqorisida **stepper** (4 qadam, tugallangan qadam `check` bilan),
pastda `Orqaga` / `Davom etish`, oxirgi qadamda `Yaratish va ishga tushirish`
(asosiy) + `Qoralama sifatida saqlash` (ikkinchi darajali).

**Kampaniya tafsiloti sahifasi:** yuqorida sarlavha + boshqaruv tugmalari
(`Ishga tushirish` / `To'xtatish` / `Tahrirlash` / `⋯`), ostida tab'lar:
**Umumiy** (progress + grafik + metrikalar) · **Nishonlar** (jadval, holat
ustuni bilan) · **Qo'ng'iroqlar** (filtrlangan qo'ng'iroqlar jadvali) ·
**Sozlamalar**.

### 10.7 Ssenariylar

- **Ro'yxat:** ikki bo'lim — *"Tayyor shablonlar"* (read-only, `lock` ikonkasi bilan,
  "Nusxalash" tugmasi) va *"Mening ssenariylarim"* (tahrirlanadi, versiya badge'i
  bilan: `v3 · faol`).
- **Ssenariy kartochkasi:** ikonka + nom + tavsif (2 qator) + bosqichlar soni +
  qaysi kampaniyalarda ishlatilayotgani (`3 ta kampaniya`).
- **Tahrirlagich (muhim ekran):** ikki panelli.
  - **Chapda — bosqichlar oqimi:** vertikal zanjir, har bir bosqich kartochka
    (nom, maqsad, ruxsat etilgan o'tishlar strelka bilan). Bosqichni sudrab
    tartibini o'zgartirish mumkin. Pastda `+ Bosqich qo'shish`.
  - **O'ngda — tanlangan bosqich sozlamasi:** nom, maqsad (prompt matni,
    kengayadigan textarea), o'tishlar (chip + shart), tool'lar (checkbox ro'yxati).
  - **Yuqorida tab'lar:** `Bosqichlar` · `Faktlar` (sxema jadvali: nom, tur,
    majburiy) · `Natija` (outcome maydonlari) · `Rol va qoidalar` (rolePrompt
    textarea + qo'shimcha guardrails ro'yxati) · `JSON` (mono muharrir,
    validatsiya xatolari qizil chiziq bilan).
  - **O'ng-yuqorida:** `Sinov qo'ng'irog'i` (telefon raqam so'raydigan modal),
    `Saqlash` (yangi versiya yaratadi — buni matn bilan ogohlantiring).
  - **Muzlatilgan qism:** umumiy guardrails va disclosure matni **kulrang, qulf
    ikonkasi bilan** ko'rsatiladi — tahrirlab bo'lmaydi, ostida izoh:
    *"Bu qoidalar platforma darajasida majburiy va o'chirilmaydi"*.

### 10.8 Kontaktlar

- Tab'lar: **Barcha kontaktlar** · **Qo'ng'iroq qilinmasin (DNC)**.
- Jadval: ism, telefon (mono), kompaniya/manzil, oxirgi qo'ng'iroq, natijasi,
  teglar (chip), amallar.
- Kontakt bosilganda drawer: profil + **qo'ng'iroqlar tarixi taymlayni**
  (vertikal, har biri natija nuqtasi bilan) + eslatmalar + "DNC ga qo'shish".
- DNC tab'ida: qo'shilgan sana, sabab (Mijoz so'radi / Noto'g'ri raqam /
  Qonuniy talab), kim qo'shgani, "Ro'yxatdan chiqarish" (tasdiq bilan).
- Yuqorida: `+ Kontakt` va `⇧ CSV import`.

### 10.9 Kiruvchi marshrutlar

- Jadval: **DID raqam** (mono, katta) → **Ssenariy** → **Til** → **Ish vaqti** →
  **Ish vaqtidan tashqari** (xabar / voicemail / operator) → holat toggle.
- Qator bosilganda drawer: shu raqamga tushgan qo'ng'iroqlar statistikasi +
  sozlamalar.
- Yuqorida bo'sh holat: "Kiruvchi qo'ng'iroqlarni qabul qilish uchun raqam
  biriktiring" + `+ Marshrut qo'shish`.

### 10.10 Hisobotlar

- Yuqorida **global filtr paneli** (sticky): sana oralig'i (preset'lar bilan:
  Bugun / 7 kun / 30 kun / Oy / Ixtiyoriy), kampaniya, ssenariy, operator.
- Grafiklar (2 ustunli grid, har biri `surface` kartochkada, sarlavha + `⋯` menyu
  bilan → "PNG yuklab olish", "CSV yuklab olish"):
  1. **Qo'ng'iroqlar dinamikasi** — stacked area (javob berdi / bermadi / xato).
  2. **Natijalar taqsimoti** — gorizontal bar (disposition ranglari bilan).
  3. **Soatlar bo'yicha samaradorlik** — heatmap (kun × soat, javob berish foizi).
     Bu — operator uchun eng qimmatli grafik, chunki qo'ng'iroq oynasini shunga
     qarab sozlaydi.
  4. **Kampaniyalar taqqoslash** — jadval + mini bar.
  5. **Suhbat davomiyligi taqsimoti** — histogram.
  6. **Voronka** — Qo'ng'iroq → Javob → Shaxs tasdiqlandi → Suhbat → Natija.
- Har bir grafik ostida asosiy raqam va o'zgarish.
- Yuqori o'ngda: `⇩ Hisobotni yuklab olish` (PDF/CSV/XLSX tanlovi bilan) va
  `📅 Jadval bo'yicha yuborish` (email'ga muntazam hisobot).

**Grafik uslubi:** grid chiziqlari juda ingichka (`border`, 1px), o'q matnlari
Micro `text-subtle`, tooltip `surface-raised` + `shadow-lg`, legend tepada
gorizontal. Ranglar — disposition palitrasidan. **Animatsiya:** birinchi
chizilganda 400ms `ease-out` bilan o'sadi, filtr o'zgarsa 200ms morph.

### 10.11 Sozlamalar

Chapda **ichki navigatsiya** (sidebar ichidagi sidebar emas — kontent ichida,
200px), o'ngda forma. Bo'limlar:

| Bo'lim | Ichida |
|--------|--------|
| **Kompaniya** | nom, logotip, timezone, standart til, manzil |
| **Telefoniya** | SIP trunk'lar (jadval: nom, host, holat indikatori, `Sinash` tugmasi), caller ID / DID raqamlar, parallel qo'ng'iroq limiti |
| **Ovoz va til** | TTS provayder (kartochka tanlov: Yandex / Google), ovoz tanlash — **har biri yonida ▶ tinglash tugmasi**, tezlik/tembr sliderlari, STT provayderi, til |
| **AI model** | model tanlovi, harorat (slider), maksimal token, javob uzunligi, qo'ng'iroqning maksimal davomiyligi va navbatlari (qattiq limitlar — ogohlantirish bilan) |
| **Integratsiyalar** | CRM kartochkalari (Uysot / REST / Yo'q) ulanish holati bilan, webhook URL'lar, `Ulanishni sinash` tugmasi |
| **API kalitlar** | jadval: nom, prefiks (`nid_live_a3f…`, mono, qolgani yashirin), huquq (to'liq / faqat o'qish), oxirgi ishlatilgan, `Yaratish` (yaratilganda kalit **bir marta** ko'rsatiladigan modal — nusxa olish tugmasi va qizil ogohlantirish bilan), `Bekor qilish` |
| **Bildirishnomalar** | kanal × hodisa matritsasi (kompaniya darajasida) |

**Forma qoidalari:** label tepada (Small, 500), input ostida yordamchi matn
(Small, `text-subtle`), xato — input chegarasi `danger` + ostida qizil matn +
`alert-circle` ikonka. Har bir bo'lim pastida **sticky saqlash paneli** paydo
bo'ladi (o'zgarish bo'lsa): `Saqlanmagan o'zgarishlar` + `Bekor qilish` + `Saqlash`.

### 10.12 Foydalanuvchilar

- Jadval: avatar + ism, email, rol (badge), holat (Faol / Taklif yuborilgan /
  Bloklangan), oxirgi kirish, amallar.
- `+ Foydalanuvchi taklif qilish` → modal: email, rol tanlash (radio kartochkalar,
  har birining huquqlari qisqacha yozilgan), ixtiyoriy xabar.
- Rol o'zgartirish — inline dropdown, tasdiq bilan.

### 10.13 Audit jurnali

- Vertikal **taymlayn** (jadval emas): har bir yozuv — vaqt (mono), aktor
  (avatar + ism yoki `API kalit: …`), amal (matn), obyekt (havola), IP (mono, Small).
- Chapda filtr paneli: sana, aktor, amal turi, obyekt turi.
- Xavfli amallar (o'chirish, kalit yaratish, DNC dan chiqarish) — chap chekkada
  `danger` rangli vertikal chiziq bilan belgilanadi.

### 10.14 Hisob-kitob (Billing)

- Yuqorida: joriy tarif kartochkasi (nom, narx, davr), **sarf progress bar'lari**
  (daqiqalar, tokenlar, TTS belgilari — har biri limitga nisbatan; 80% dan oshsa
  `warning`, 95% dan oshsa `danger`).
- Ostida: oylik sarf grafigi, hisob-fakturalar jadvali (sana, summa, holat,
  `⇩ PDF`).

---

## 11. Komponentlar kutubxonasi

Har bir komponentning **barcha holatlari** chizilishi shart:
`default / hover / active / focus / disabled / loading`.

### 11.1 Tugmalar

| Turi | Fon | Matn | Chegara | Qachon |
|------|-----|------|---------|--------|
| **Primary** | `primary-600` | oq | — | sahifada **bitta** asosiy amal |
| **Secondary** | `surface` | `text` | 1px `border-strong` | ikkinchi darajali |
| **Ghost** | shaffof | `text-muted` | — | jadval ichidagi amal |
| **Danger** | `danger` | oq | — | o'chirish, tugatish |
| **Danger-ghost** | shaffof | `danger` | — | ro'yxat ichidagi o'chirish |

- Hover: fon 1 pog'ona to'qroq (`primary-700`), 120ms.
- Active: `transform: translateY(1px)`, fon yana to'qroq.
- Focus: 2px `primary-300` halqa, 2px offset.
- Disabled: opacity .5, kursor `not-allowed`.
- Loading: matn joyida qoladi (kenglik sakramasin!), chapida 14px spinner,
  tugma `pointer-events: none`.
- Ikonka + matn: ikonka 16px, orasi 6px. Faqat ikonkali tugma: 36×36 kvadrat,
  `radius-md`, **tooltip majburiy**.

### 11.2 Inputlar

Balandlik 38px, `radius-md`, 1px `border`, padding `0 12px`, fon `surface`.
Focus: chegara `primary-500` + 3px `rgba(79,76,214,.12)` halqa.
Xato: chegara `danger` + 3px `rgba(220,38,38,.10)`.
Ichki ikonka (chapda qidiruv, o'ngda tozalash) — 16px, `text-subtle`.

### 11.3 Badge / Chip

- **Badge (status):** balandlik 22px, padding `0 8px`, `radius-full`, Micro 500,
  fon = semantik rangning 12% opacity'si, matn = to'q variant.
- **Chip (filtr):** balandlik 28px, `radius-full`, `surface-sunken` fon, matn +
  `✕` (12px). Hover'da `✕` to'qlashadi.
- **Kbd:** mono, 11px, `surface-sunken` fon, 1px `border`, `radius-sm`, padding
  `2px 5px`.

### 11.4 Jadval

- Sarlavha: `surface-sunken` fon, Micro UPPERCASE `text-muted`, balandlik 40px,
  **sticky**.
- Qatorlar orasida 1px `border` (yuqori chegara), zebra **yo'q**.
- Hover: `surface-sunken`.
- Tanlangan: `primary-50` fon + chapda 2px `primary-600` chiziq.
- Birinchi ustun (checkbox) 44px, oxirgi ustun (amallar) o'ngga yopishgan
  (sticky right), gradient bilan tugaydi.
- Gorizontal scroll bo'lsa — birinchi ma'lumot ustuni ham sticky.

### 11.5 Drawer (o'ng panel)

- Kengligi 560px (keng kontentda 720px), balandligi to'liq, `surface` fon,
  chapda 1px `border`, `shadow-lg`.
- Ochilishi: `translateX(100%) → 0`, 240ms `cubic-bezier(.2,0,0,1)`, orqasida
  `overlay` fade 160ms.
- Sarlavha qismi sticky (`✕` chapda yoki o'ngda — **doim bir xil joyda**,
  tavsiya: o'ngda), pastda amallar paneli sticky bo'lishi mumkin.
- `Esc` yopadi. Ochilganda fokus drawer ichiga o'tadi (focus trap).

### 11.6 Modal

- Kengligi: 420 (tasdiq) / 560 (forma) / 720 (murakkab), markazda, vertikal
  markazlash (uzun bo'lsa yuqoridan 64px).
- `radius-xl`, `shadow-lg`, `overlay` orqada.
- Ochilishi: `scale(.97) → 1` + opacity, 180ms.
- **Tasdiq modali:** yuqorida rangli ikonka doirasi (48px, semantik rangning
  12% foni), sarlavha (H2), matn (Body, `text-muted`), pastda o'ngga tekislangan
  tugmalar (`Bekor qilish` ghost + asosiy amal). **Xavfli amalda**: obyekt nomini
  yozdirish talab qilinadi (masalan, kampaniya nomini).

### 11.7 Toast (bildirishnoma)

- O'ng-yuqorida (topbar ostida), 380px, `surface-raised`, `shadow-lg`,
  `radius-md`, chapida 3px semantik rangli chiziq.
- Ichida: ikonka + sarlavha (Body-strong) + matn (Small) + `✕` + ixtiyoriy
  amal havolasi ("Bekor qilish", "Ko'rish").
- Avtomatik yopilish: 5s (xato — **yopilmaydi**, foydalanuvchi yopadi).
- Bir vaqtda maksimal 3 ta, ustma-ust (stack), yangi tepadan kiradi.

### 11.8 To'lqin pleyer (signature komponent)

Bu — mahsulotning **eng taniqli vizual elementi**, alohida e'tibor bering.

- Balandligi 64px, `surface-sunken` fon, `radius-md`, padding 12px.
- To'lqin: **vertikal ustunlar**, kengligi 2px, oralig'i 1px, `radius-full`.
- **Ikki rang:** robot gapirgan qismlar `primary-500`, mijoz gapirgan qismlar
  `accent-500`. Bu — juda muhim: foydalanuvchi to'lqinga qarab kim ko'p gapirganini
  darrov ko'radi.
- Ijro etilmagan qism: rangning **35% opacity**'si; ijro etilgan qism — to'liq.
- Kursor (playhead): 2px vertikal chiziq `text`, ustida 10px doira.
- Hover'da kursor ostida vaqt tooltip'i chiqadi.
- Pastda boshqaruv: `▶/⏸` (32px doira, `primary-600` fon, oq ikonka), vaqt
  (mono, `0:47 / 2:14`), o'ngda tezlik (`1.0× ⌄` — 0.5/1/1.25/1.5/2),
  `⇩` yuklab olish, `⋯`.
- **Jonli rejimda:** ustunlar o'ngdan kirib chapga suriladi, oxirgi ustunlar
  yorqinroq (opacity gradienti), kursor yo'q.

### 11.9 Command palette (⌘K)

- Markazda, yuqoridan 120px, 560px kenglik, `surface-raised`, `shadow-lg`,
  `radius-xl`.
- Tepada katta input (48px, ikonkasiz, placeholder: "Qidirish yoki buyruq…").
- Ostida guruhlangan natijalar: **Sahifalar**, **Kampaniyalar**, **Qo'ng'iroqlar**
  (raqam bo'yicha), **Buyruqlar** (`Yangi kampaniya`, `Temani almashtirish`,
  `Chiqish`).
- Har bir qator: ikonka + nom + o'ngda kontekst (Small, `text-subtle`).
  Tanlangan qator `primary-50` fon.
- `↑↓` yurish, `Enter` tanlash, `Esc` yopish.

### 11.10 Boshqa mayda komponentlar

`Tooltip` (to'q fon `#1F2433`, oq matn Micro, `radius-sm`, 200ms kechikish) ·
`Segmented control` · `Toggle` (44×24, `accent-500` yoqilganda) · `Checkbox` /
`Radio` (18px, `primary-600`) · `Slider` · `Progress bar` (6px, `radius-full`) ·
`Progress ring` · `Pagination` · `Breadcrumb` · `Tabs` (pastida 2px aktiv chiziq) ·
`Date range picker` · `Multi-select dropdown` (checkbox + qidiruv bilan) ·
`File dropzone` (dashed 2px `border-strong`, hover'da `primary-400`) ·
`Skeleton` · `Avatar group` (ustma-ust, `+3`).

---

## 12. Holatlar: bo'sh, yuklanmoqda, xato

**Har bir ro'yxat/jadval/grafik uchun 4 ta holat chizilishi shart.**

### Bo'sh holat (birinchi marta)

Markazda: 160px illyustratsiya, H2 sarlavha, Body izoh (max 2 qator, `text-muted`),
asosiy tugma. Misollar:

| Ekran | Sarlavha | Izoh | Tugma |
|-------|----------|------|-------|
| Kampaniyalar | "Hali kampaniya yo'q" | "Birinchi kampaniyani yarating — mijozlar ro'yxatini yuklang, ssenariy tanlang, qolganini Nido bajaradi." | `+ Kampaniya yaratish` |
| Qo'ng'iroqlar | "Qo'ng'iroqlar hali yo'q" | "Kampaniya ishga tushganda qo'ng'iroqlar shu yerda paydo bo'ladi." | `Kampaniyalarga o'tish` |
| Jonli | "Hozir jonli qo'ng'iroq yo'q" | "Faol kampaniya qo'ng'iroq boshlaganda shu yerda real vaqtda ko'rasiz." | — |

### Natija topilmadi (filtrdan keyin)

Kichikroq: 96px ikonka, "Hech narsa topilmadi", "Filtrlarni o'zgartirib ko'ring",
`Filtrlarni tozalash` (ghost tugma). **Illyustratsiya boshqacha** bo'lsin —
foydalanuvchi bo'sh holat bilan chalkashtirmasin.

### Yuklanmoqda

- **Skeleton** (spinner emas) — jadval uchun 5 qator skeleton, KPI uchun
  kartochka skeleton'i, grafik uchun to'lqinsimon blok.
- Skeleton rangi: `surface-sunken`, ustidan chapdan o'ngga 1.4s `shimmer`.
- Faqat qisqa amallarda (tugma bosilganda) spinner ishlatiladi.

### Xato

- **Sahifa darajasida:** markazda `alert-triangle` ikonka (`danger`, 48px),
  "Ma'lumotni yuklab bo'lmadi", texnik izoh (Small, mono, yig'ilgan holda),
  `Qayta urinish` tugmasi.
- **Blok darajasida:** kartochka ichida qizil chegara + qisqa xabar + `⟳`.
- **Ulanish uzildi (WebSocket):** topbar ostida `warning` chiziq —
  "Real vaqt ulanishi uzildi. Qayta ulanmoqda…" + spinner.

---

## 13. Animatsiya va harakat

| Nom | Davomiylik | Egri chiziq | Qayerda |
|-----|-----------|-------------|---------|
| Instant | 100ms | `ease-out` | rang, opacity |
| Fast | 140ms | `ease-out` | hover, tooltip |
| Default | 180ms | `cubic-bezier(.2,0,0,1)` | popover, dropdown, modal |
| Slow | 240ms | `cubic-bezier(.2,0,0,1)` | drawer, sidebar yig'ilishi |
| Chart | 400ms | `ease-out` | grafik chizilishi |

**Maxsus animatsiyalar:**

- **Jonli nuqta pulsatsiyasi:** 8px doira, atrofida ikkinchi doira
  `scale(1) → scale(2.4)` + `opacity(.5) → 0`, **2s**, cheksiz, `ease-out`.
- **Jonli to'lqin:** har 100ms da yangi ustun o'ngdan kiradi, ustun balandligi
  yumshoq o'zgaradi (100ms), chapga chiqib ketayotgani opacity bilan so'nadi.
- **Yangi qator kirishi (jonli ro'yxatda):** balandlik 0→auto + opacity, 240ms,
  fon bir marta `accent` 8% dan shaffofga (highlight flash, 800ms).
- **Raqam o'zgarishi (KPI):** eski raqam yuqoriga chiqib ketadi, yangisi pastdan
  keladi (roll), 200ms. **Faqat** jonli KPI'larda.
- **Sahifa almashishi:** kontent `opacity 0→1` + `translateY(4px)→0`, 160ms.
  Sidebar **hech qachon** animatsiyalanmaydi.

**`prefers-reduced-motion: reduce`** — barcha `transform` animatsiyalari
o'chiriladi, faqat opacity qoladi; pulsatsiya statik nuqtaga aylanadi.

---

## 14. Qorong'i rejim

**Bu keyinroq qo'shiladigan narsa emas — ikkalasi ham birga chiziladi.** Operatorlar
kechqurun ham ishlaydi, aksariyati qorong'i rejimni tanlaydi.

Qoidalar:

1. **Sof qora yo'q** — eng to'q fon `#0B0D12`.
2. **Sirt ko'tarilgan sari yorug'lashadi:** `bg #0B0D12` → `surface #12151D` →
   `surface-raised #1A1E28`. Soya emas, **yorug'lik** qatlamni bildiradi.
3. **Brend rangi yorug'lashadi:** tugma foni `primary-600` qoladi, ammo matn-havola
   va aktiv ikonka `primary-400`.
4. **Rangli fonlar shaffoflik bilan:** `primary-50` o'rniga
   `rgba(79,76,214,.16)` — chunki tayyor to'q rang fon ustida kir ko'rinadi.
5. **Chegaralar ko'rinarli:** `#242936` — light rejimdagidan nisbatan kontrastliroq.
6. **Grafik ranglari yorqinlashadi** (semantik jadvaldagi dark ustuni).
7. **Rasm va avatarlar** ustiga hech qanday filter qo'yilmaydi.
8. Logotip qorong'i rejimda: mark **oq** (gradient saqlanadi, ammo yorqinroq
   variantda), wordmark oq.

---

## 15. Moslashuvchanlik (responsive)

| Kenglik | Xatti-harakat |
|---------|---------------|
| **≥1536** | Sidebar ochiq, kontent markazda (max 1440), grafik 2 ustun, drawer 640px |
| **1280–1535** | Sidebar ochiq, kontent to'liq kenglik, drawer 560px |
| **1024–1279** | Sidebar **avtomatik yig'iladi** (72px), grafik 2 ustun, KPI 4 ustun |
| **768–1023** | Sidebar **overlay drawer** (topbar'da `menu` tugmasi), KPI 2 ustun, grafik 1 ustun, jadval gorizontal scroll |
| **<768** | Faqat **kuzatuv rejimi**: Boshqaruv paneli, Jonli, Qo'ng'iroqlar (kartochka ko'rinishida, jadval emas), Qo'ng'iroq tafsiloti (to'liq ekran). Kampaniya yaratish/ssenariy tahrirlash — "Bu sahifa katta ekranda ochilsin" xabari. Pastda 4 elementli tab-bar (Panel / Jonli / Qo'ng'iroq / Profil) |

**Mobil xodim kartochkasi:** sidebar drawer ichida pastda o'sha joyda qoladi,
ammo hover o'rniga **bosish** popover'ni ochadi (bottom sheet ko'rinishida).

---

## 16. Qulaylik (accessibility)

Maqsad: **WCAG 2.1 AA**.

- **Kontrast:** oddiy matn ≥ 4.5:1, katta matn va ikonka ≥ 3:1. `text-subtle`
  faqat 13px+ da ishlatilsin.
- **Rang yolg'iz ma'no tashimasin:** har bir disposition rangi yonida **matn**
  va **nuqta shakli** bor (to'ldirilgan / bo'sh / kvadrat).
- **Fokus:** har bir interaktiv elementda ko'rinadigan halqa (2px `primary-400`,
  2px offset). Fokus tartibi vizual tartibga mos.
- **Hover-only ma'lumot yo'q:** §8 dagi popover bosish bilan ham ochiladi;
  jadval qatoridagi yashirin tugmalar klaviatura fokusida ham ko'rinadi.
- **Tegish maydoni** ≥ 36×36 (mobilda 44×44).
- **Jonli hududlar:** yangi qo'ng'iroq, toast va xato xabarlari
  `aria-live="polite"` (xato — `assertive`).
- **Modal va drawer:** focus trap, `Esc`, yopilganda fokus qaytadi.
- **Skip link:** "Asosiy kontentga o'tish" — `Tab` bosilganda birinchi chiqadi.
- **Klaviatura yorliqlari:** `⌘K` qidiruv · `G+D` panel · `G+C` qo'ng'iroqlar ·
  `G+K` kampaniyalar · `N` yangi (kontekstga qarab) · `?` yorliqlar ro'yxati ·
  `Esc` yopish · `Space` audio ijro (tafsilot ochiq bo'lganda).

---

## 17. Matn uslubi (microcopy)

**Umumiy qoidalar:**

- Tugmada **fe'l**: `Saqlash`, `Ishga tushirish`, `Yuklab olish` — `OK` emas.
- Sarlavhada nuqta qo'yilmaydi. Izoh gapida qo'yiladi.
- Raqamlarda bo'shliq ajratgich: `1 248`, `1 200 000 so'm`.
- Xato xabari **nima qilish kerakligini** aytadi:
  ❌ "Xatolik yuz berdi"
  ✅ "CSV faylni o'qib bo'lmadi. Fayl UTF-8 da va birinchi qator ustun nomlari
  ekaniga ishonch hosil qiling."
- Tasdiq so'rovi **oqibatni** aytadi:
  ✅ "Kampaniya to'xtatiladi. Ketayotgan 3 ta qo'ng'iroq tugagunicha davom etadi,
  navbatdagi 47 ta nishonga qo'ng'iroq qilinmaydi."

**Asosiy atamalar lug'ati (dizaynda aynan shu so'zlar ishlatilsin):**

| O'zbekcha | Ma'nosi |
|-----------|---------|
| Kampaniya | qo'ng'iroqlar to'plami |
| Ssenariy | suhbat mantiqi |
| Nishon | kampaniyadagi bitta mijoz-yozuv |
| Bosqich | ssenariy ichidagi holat (stage) |
| Natija | qo'ng'iroq yakuni (disposition) |
| Transkript | suhbat matni |
| Qo'ng'iroq oynasi | ruxsat etilgan soatlar |
| Qo'ng'iroq qilinmasin | do-not-call ro'yxati |
| Jonli | hozir ketayotgan qo'ng'iroq |

---

## 18. Chizish tartibi va yetkazib berish

### 18.1 Ustuvorlik

**1-to'plam (asos, birinchi chiziladi):**
1. Logotip (barcha variantlar) + favicon
2. Dizayn tokenlari (rang, tipografika, radius, soya) — style sheet sifatida
3. App Shell: sidebar (ochiq + yopiq) + topbar
4. **Xodim kartochkasi + hover-popover** (barcha holatlar bilan)
5. Boshqaruv paneli
6. Qo'ng'iroqlar jadvali + tafsilot drawer'i (to'lqin pleyer bilan)

**2-to'plam:**
7. Jonli qo'ng'iroqlar
8. Kampaniyalar (ro'yxat + kartochka + 4 qadamli sehrgar + tafsilot)
9. Hisobotlar
10. Komponentlar kutubxonasi (barcha holatlar)

**3-to'plam:**
11. Ssenariy tahrirlagichi
12. Sozlamalar (barcha bo'limlar)
13. Kontaktlar, Kiruvchi marshrutlar, Foydalanuvchilar, Audit, Billing
14. Login, Profil sahifasi
15. Bo'sh / xato / yuklanish holatlari
16. Mobil ko'rinishlar

### 18.2 Har bir ekran uchun talab

- **Light + Dark** — ikkalasi ham.
- Barcha interaktiv elementlar holatlari alohida ko'rsatilgan.
- Real ma'lumot bilan to'ldirilgan — `Lorem ipsum` **yo'q**. O'zbekcha ismlar
  (Anvar Karimov, Sardor Mahmudov, Nilufar Yo'ldosheva), `+998` raqamlar,
  haqiqiy kampaniya nomlari (`Qarzdorlik iyul`, `Yangi lidlar Q3`).
- Jadvallarda kamida 8 qator, har xil natija turlari bilan.
- Uzun matnlar bilan **stress-test**: juda uzun ism, juda uzun kampaniya nomi —
  qanday qisqarishi ko'rsatilsin.

### 18.3 Fayl tashkiloti

```
Nido Design System
├── 00 · Brend          (logotip, ranglar, tipografika, ikonkalar)
├── 01 · Tokenlar       (rang / o'lcham / soya / animatsiya)
├── 02 · Komponentlar   (har biri barcha holatlari bilan)
├── 03 · Naqshlar       (shell, jadval, forma, bo'sh holat)
├── 04 · Ekranlar       (sahifa-sahifa, light + dark)
└── 05 · Mobil
```

---

## Qisqacha xulosa (agar faqat bir necha jumla o'qilsa)

**Nido** — AI qo'ng'iroq platformasi. Logotipi: yumaloqlangan maydon ichida
**-45° burilgan telefon go'shagi**, undan **3 ta ovoz to'lqini ustuni** chiqadi
(gradient: indigo → yashil). Rang: brend **indigo `#4F4CD6`**, jonli qo'ng'iroq
**yashil `#10B981`**. Chapda **264px sidebar** — logotip, kompaniya tanlagich,
4 bo'limga guruhlangan sahifalar, eng pastda **xodim avatari + ismi + roli**;
ustiga sichqoncha kelganda (150ms kechikish bilan) **yuqorida 268px popover**
ochiladi — profil ma'lumoti, bugungi statistika, Profilim / Bildirishnomalar /
Ko'rinish / Til / Yordam va qizil **Chiqish**. Interfeys zich, tinch va ma'lumotga
yo'naltirilgan; eng puxta chizilishi kerak bo'lgan ekran — **qo'ng'iroq tafsiloti**
(ikki rangli to'lqin pleyer + chap-o'ng transkript). Light va dark rejim — ikkalasi
teng huquqli.
