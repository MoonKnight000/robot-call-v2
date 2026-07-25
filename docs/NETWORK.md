# Tarmoq, IP va router sozlamalari (NAT / port forwarding)

Bu fayl `pjsip.conf` dagi IP maydonlarini to'g'ri to'ldirish, router'da port
forward qilish va NAT bilan bog'liq muammolarni topish uchun.

Qisqa javob eng ko'p beriladigan savolga: **hozirgi outbound-only rejim uchun
router'da port forward qilish SHART EMAS, lekin barqarorlik uchun tavsiya
etiladi. Inbound (kiruvchi) qo'ng'iroq kerak bo'lganda — SHART.** Batafsili
[§4](#4-asterisk-uchun-port-forward-kerakmi) da.

---

## 1. Tarmoq xaritasi

Hozirgi dev muhitida to'rt qatlam bor. Har birida o'z IP'si, va aynan shu
qatlamlarni aralashtirib yuborish eng ko'p uchraydigan xato.

```
                    ┌─────────────────────── INTERNET ───────────────────────┐
                    │                                                        │
       SIP provayder (Skyline)                                    Yandex / LLM proxy
       pbx.skyline.uz → 91.203.175.6                              (faqat outbound HTTPS)
                    │
                    │  SIP 5060/udp + RTP 10000-10200/udp
                    ▼
        ╔═══════════════════════════╗
        ║  WAN: 90.156.197.92       ║   ← internetda ko'rinadigan IP (Uzbektelekom)
        ║  ROUTER (NAT #1)          ║      pjsip.conf → external_*_address
        ║  LAN: 192.168.88.1        ║
        ╚═══════════════════════════╝
                    │  192.168.88.0/24
                    ▼
        ┌───────────────────────────┐
        │  Windows PC               │   ← 192.168.88.72
        │  ┌─────────────────────┐  │
        │  │ Docker (NAT #2)     │  │      port publishing: 5060, 8088, 10000-10200, 8080
        │  │  asterisk ◄──► app  │  │   ← konteynerlar bir-birini DNS nomi bilan ko'radi
        │  └─────────────────────┘  │      (RTP_LOCAL_IP=app)
        └───────────────────────────┘
```

### Uchta IP — hech qachon aralashtirmang

| IP turi | Qiymat (hozir) | Qayerda ishlatiladi | Kim ko'radi |
|---|---|---|---|
| **Public / WAN** | `90.156.197.92` | `pjsip.conf` → `external_media_address`, `external_signaling_address` | Internet, SIP provayder |
| **LAN** | `192.168.88.72` (PC), `192.168.88.1` (router) | `local_net`, port forward maqsadi, softphone testi | Faqat uy/ofis tarmog'i |
| **Docker ichki** | `app`, `asterisk`, `postgres`… (DNS nomlari) | `RTP_LOCAL_IP=app`, `ARI_URL=http://asterisk:8088/` | Faqat konteynerlar |

> `90.156.197.92` — **kompyuterning IP'si emas, routerning WAN IP'si**. Shuning
> uchun `http://90.156.197.92:8080` ga kirish ishlamaydi: router bu paketni
> kimga uzatishni bilmaydi. `http://localhost:8080` va
> `http://192.168.88.72:8080` esa ishlaydi.

---

## 2. Public IP'ni aniqlash va CGNAT tekshiruvi

### 2.1. Hozirgi public IP

```powershell
curl.exe -s https://ipinfo.io/json
# yoki eng qisqasi:
curl.exe -s https://ifconfig.me
```

Chiqishdagi `ip` maydoni — aynan `external_*_address` ga yozilishi kerak
bo'lgan qiymat.

### 2.2. CGNAT bormi? (eng muhim tekshiruv)

Router'ga kirib **WAN interfeysning IP'siga** qarang va public IP bilan
solishtiring:

| Router WAN IP | Ma'nosi | Port forward ishlaydimi |
|---|---|---|
| `90.156.197.92` (public IP bilan bir xil) | Real public IP, bitta NAT | ✅ Ha |
| `10.x.x.x` | Provayder NAT'i ostidasiz | ❌ Yo'q |
| `100.64.x.x` – `100.127.x.x` | **CGNAT** (RFC 6598) | ❌ Yo'q |
| `192.168.x.x` / `172.16–31.x.x` | Double NAT (modem + router) | ❌ Yo'q (modemda ham forward kerak) |

**Agar CGNAT bo'lsa**, router'da nima qilsangiz ham tashqaridan kirib
bo'lmaydi. Yechimlar:

1. Uzbektelekom'dan **statik (public) IP** xizmatini buyurtma qilish — eng
   to'g'ri yo'l.
2. Loyihani **VPS/serverga** ko'chirish (u yerda NAT umuman yo'q — [§9](#9-vps--serverga-kochirilganda)).
3. Vaqtinchalik: reverse tunnel (ngrok / Cloudflare Tunnel). Faqat TCP/HTTP
   uchun ishlaydi — **SIP/RTP (UDP) uchun yaramaydi**, ya'ni trunk muammosini
   hal qilmaydi.

### 2.3. MikroTik'da tez tekshirish

`192.168.88.1` — MikroTik'ning zavod sozlamasidagi manzili, ya'ni router
ehtimol MikroTik. Winbox yoki SSH orqali:

```
/ip address print          # interfeyslar va IP'lar (WAN IP shu yerda)
/ip route print            # default gateway qayoqqa ketadi
/ip cloud print            # MikroTik o'zi aniqlagan public IP ("public-address")
```

`/ip cloud print` dagi `public-address` router WAN IP'sidan farq qilsa — CGNAT
ostidasiz.

---

## 3. `pjsip.conf` dagi IP maydonlari

Fayl: `asterisk/etc/asterisk/pjsip.conf`, `[transport-udp]` bo'limi.

```ini
[transport-udp]
type = transport
protocol = udp
bind = 0.0.0.0:5060
external_media_address = 90.156.197.92
external_signaling_address = 90.156.197.92
local_net = 192.168.88.0/24
```

### Nima uchun kerak

Asterisk konteyner ichida turadi va o'z IP'sini `172.x.x.x` deb biladi. Agar
SIP/SDP ichida shu manzilni e'lon qilsa, provayder RTP'ni `172.x.x.x` ga
yuborishga urinadi va **ovoz kelmaydi (one-way audio)**. `external_*_address`
Asterisk'ga "SDP/Contact ichida bu manzilni yoz" deb aytadi.

- `external_signaling_address` → SIP sarlavhalari (`Contact`, `Via`) uchun.
- `external_media_address` → SDP `c=` qatori (RTP qayerga kelishi) uchun.
- `local_net` → bu subnetdagi manzillarga yozishmada NAT almashtirish
  qilinmaydi. Softphone LAN ichida bo'lgani uchun shart.

### Qaysi holatda nima yozish

| Holat | `external_*_address` | `local_net` |
|---|---|---|
| **Real trunk** (provayder internet orqali) | Public IP: `90.156.197.92` | LAN subnet: `192.168.88.0/24` |
| **Faqat LAN softphone** (trunk'siz, `600` ga qo'ng'iroq) | LAN IP: `192.168.88.72` | `192.168.88.0/24` |
| **VPS / public IP'li server** | Serverning public IP'si (yoki umuman olib tashlash — [§9](#9-vps--serverga-kochirilganda)) | Kerak bo'lmaydi |

Transport bittа bo'lgani uchun **ikkalasini bir vaqtda ishlatib bo'lmaydi** —
faylda tayyor kommentlangan variantlar bor, kerakligini yoqib, ikkinchisini
o'chiring. O'zgartirgandan keyin:

```powershell
docker compose restart asterisk
```

---

## 4. Asterisk uchun port forward kerakmi?

### 4.1. Nazariya: NAT teshigi (pinhole)

Router NAT'i **outbound** paket ketganda avtomatik "teshik" ochadi va o'sha
teshik orqali javob paketlarini qaytaradi. Ya'ni:

- Asterisk provayderga `REGISTER` yuboradi → 5060/udp uchun teshik ochiladi →
  provayder javobi qaytib kiradi. **Forward kerak emas.**
- Asterisk qo'ng'iroq boshlaydi (`INVITE`) va RTP yuboradi → har bir RTP porti
  uchun teshik ochiladi → provayderning RTP'si qaytadi. Bunga `pjsip.conf`
  dagi `rtp_symmetric = yes` yordam beradi: Asterisk provayder SDP'da nima
  yozganini emas, RTP **haqiqatda qaysi manzildan kelganini** ishlatadi.

### 4.2. Amaliy javob

| Rejim | Port forward | Izoh |
|---|---|---|
| **Outbound-only** (hozirgi MVP: app qo'ng'iroq qiladi) | ❌ Shart emas, ✅ tavsiya etiladi | Registratsiya va RTP outbound teshiklar orqali ishlaydi. Lekin teshik **vaqt bilan yopiladi** ([§4.3](#43-outbound-only-boyicha-ogohlantirishlar)) |
| **Inbound / DID** (tashqaridan qo'ng'iroq qabul qilish) | ✅ **Shart** | Provayder o'zi `INVITE` boshlaydi, teshik yo'q |
| **Tashqi softphone** (uydan ofis Asterisk'iga registratsiya) | ✅ **Shart** | Yuqoridagi bilan bir xil sabab |
| **Faqat LAN softphone** | ❌ Kerak emas | Trafik router'ning WAN tomoniga chiqmaydi |

Loyihaning hozirgi qamrovi outbound-only: `extensions.conf` dagi
`[from-trunk]` konteksti kiruvchi qo'ng'iroqni ataylab `Hangup()` qiladi. Ya'ni
**forward'siz ham trunk ishlashi kerak.** Agar ishlamasa — sabab ko'pincha
forward emas, balki SIP ALG ([§6](#6-sip-alg--albatta-ochiring)) yoki
`external_*_address` xato bo'lishi.

### 4.3. Outbound-only bo'yicha ogohlantirishlar

Forward'siz ishlaganda uchraydigan uch muammo:

**(a) NAT teshigi yopiladi.** Ko'p routerlar UDP mapping'ini 30–180 sekunddan
keyin o'chiradi (MikroTik: `udp-stream-timeout` = 3 daqiqa). Registratsiya esa
default `3600` sekundda bir yangilanadi → oradagi vaqtda provayder sizga yetib
kelolmaydi.

Yechim — Asterisk'ni tez-tez outbound paket yuborishga majburlash. **Bu
sozlamalar `pjsip.conf` ga allaqachon qo'shilgan:**

```ini
[trunk-provider]        ; registration
expiration = 120        ; default 3600 o'rniga — har 2 daqiqada REGISTER

[trunk-aor]             ; aor
qualify_frequency = 30  ; har 30s da OPTIONS ping
qualify_timeout = 5.0   ; default 3.0 internet trunk uchun juda qisqa
```

`qualify_frequency` ikki vazifani bajaradi: teshikni REGISTER'lar orasida
tirik saqlaydi va trunk holatini ko'rsatadi (`pjsip show contacts` →
`Reachable` / `Unreachable`). Agar provayder OPTIONS'ga javob bermasa, contact
`Unreachable` deb turadi — bu bizda **qo'ng'iroqni buzmaydi** (`originate`
contact statusini filtrlamaydi), lekin xohlasangiz `qualify_frequency = 0`
qilib o'chirasiz.

> **Diqqat:** pjsip'ning `keep_alive_interval` (global) sozlamasi bu yerda
> yordam bermaydi — u faqat **connection-oriented** transportlarga (TCP/TLS)
> CRLF yuboradi, bizning transport esa UDP. UDP uchun yuqoridagi ikki
> mexanizm ishlatiladi.

**(b) Port remapping.** Ba'zi routerlar (symmetric NAT) tashqi portni
o'zgartiradi: ichki `5060` → tashqi `54321`. Bunda `external_signaling_address`
faqat IP'ni to'g'rilaydi, portni emas. `force_rport = yes` va
`rewrite_contact = yes` (faylda bor) provayder tomonida buni hal qiladi, lekin
port forward qo'ysangiz muammo umuman tug'ilmaydi.

**(c) Diagnostika qiyinlashadi.** Forward bo'lsa, muammo NAT'dami yoki
Asterisk'dami — ajratish osonroq.

### 4.4. Forward qilinadigan portlar

| Port | Proto | Nimaga | Forward |
|---|---|---|---|
| `5060` | UDP | SIP signalizatsiya | Trunk uchun ✅ |
| `10000-10200` | UDP | Asterisk RTP (`asterisk/etc/asterisk/rtp.conf`) | Trunk uchun ✅ |
| `20000-20500` | UDP | App'ning externalMedia RTP (`application.yml` → `voice.rtp.port-range-*`) | ❌ **Kerak emas** — Asterisk↔app trafigi Docker ichida (`RTP_LOCAL_IP=app`), router'ga chiqmaydi |
| `8088` | TCP | Asterisk ARI / WebSocket | ❌ **Hech qachon ochmang** — parol bilan to'liq PBX boshqaruvi |
| `8080` | TCP | App REST API + actuator | ❌ Ochmang ([§8](#8-xavfsizlik--nimalarni-ochmaslik-kerak)) |
| `5432`, `6379`, `5672`, `15672`, `9000`, `9001` | TCP | Postgres, Redis, RabbitMQ, MinIO | ❌ **Hech qachon** |

RTP diapazoni SIP portidan ancha keng — har bir suhbat 2 port oladi. 100
parallel qo'ng'iroq uchun 10000–10200 yetadi.

---

## 5. Port forward qilish

Barcha misollarda maqsad: **router WAN → `192.168.88.72`** (Windows PC, Docker
u yerda portlarni publish qilgan).

> **Muhim:** PC'ning LAN IP'si DHCP orqali o'zgarib ketmasligi kerak, aks holda
> forward "bo'sh" manzilga ishlaydi. Router'da `192.168.88.72` ni PC'ning
> MAC'iga **static lease / DHCP reservation** qilib bog'lab qo'ying.

### 5.1. MikroTik (CLI — Winbox → New Terminal yoki SSH)

```
# WAN interfeys nomini aniqlang (odatda ether1 yoki pppoe-out1)
/interface print

# --- SIP ---
/ip firewall nat add chain=dstnat in-interface=ether1 protocol=udp \
    dst-port=5060 action=dst-nat to-addresses=192.168.88.72 to-ports=5060 \
    comment="asterisk SIP"

# --- RTP ---
/ip firewall nat add chain=dstnat in-interface=ether1 protocol=udp \
    dst-port=10000-10200 action=dst-nat to-addresses=192.168.88.72 \
    comment="asterisk RTP"
```

`dst-address=90.156.197.92` emas, `in-interface=ether1` ishlatilgani bejiz
emas: IP dinamik bo'lsa ham qoida buzilmaydi.

Tekshirish — qoida ishlayaptimi (`Packets` o'sib borishi kerak):

```
/ip firewall nat print stats
```

**Hairpin NAT** (LAN ichidan public IP orqali kirish uchun — [§7.3](#73-hairpin-nat-testi)):

```
/ip firewall nat add chain=srcnat src-address=192.168.88.0/24 \
    dst-address=192.168.88.72 out-interface=bridge action=masquerade \
    comment="hairpin"
```

### 5.2. Umumiy uy routerlari (TP-Link, ASUS, Keenetic, Huawei…)

Web-interfeysda bo'lim nomi har xil: **Port Forwarding**, **Virtual Server**,
**NAT Forwarding**, **Переадресация портов**.

Ikkita yozuv qo'shing:

| Nom | Tashqi port | Ichki IP | Ichki port | Protokol |
|---|---|---|---|---|
| `asterisk-sip` | 5060 | 192.168.88.72 | 5060 | UDP |
| `asterisk-rtp` | 10000–10200 | 192.168.88.72 | 10000–10200 | UDP |

Diapazon qo'llab-quvvatlanmasa, RTP'ni toraytirish mumkin — `rtp.conf` da
`rtpend = 10020` qilib, forward'ni ham `10000–10020` qilasiz (≈10 parallel
qo'ng'iroq).

### 5.3. Windows Firewall

Docker Desktop portni publish qilganda Windows odatda ruxsat so'raydi, lekin
"Public network" profilida blok qolib ketishi mumkin. LAN IP orqali test
o'tgani ([§7](#7-tekshirish-va-diagnostika)) ruxsat borligini ko'rsatadi. Qo'lda
qo'shish kerak bo'lsa (PowerShell, **administrator**):

```powershell
New-NetFirewallRule -DisplayName "Asterisk SIP"  -Direction Inbound -Protocol UDP -LocalPort 5060 -Action Allow
New-NetFirewallRule -DisplayName "Asterisk RTP"  -Direction Inbound -Protocol UDP -LocalPort 10000-10200 -Action Allow
```

Mavjud qoidalarni ko'rish:

```powershell
Get-NetFirewallRule -Direction Inbound -Enabled True |
  Where-Object DisplayName -match 'Asterisk|Docker' |
  Select-Object DisplayName, Profile, Action
```

---

## 6. SIP ALG — albatta o'chiring

Ko'p routerlarda **SIP ALG** (Application Layer Gateway) yoqilgan bo'ladi. U
"yordam berish" uchun SIP paketlarining ichiga kirib IP'larni o'zgartiradi va
deyarli har doim buzadi: `external_*_address` bilan konflikt, bir tomonlama
ovoz, registratsiya uzilishlari.

**Belgilar:** registratsiya ketadi-keladi; qo'ng'iroq ulanadi lekin jim; SIP
loglarida siz yozmagan IP paydo bo'ladi.

O'chirish:

```
# MikroTik
/ip firewall service-port set sip disabled=yes

# Boshqa routerlar: Advanced / NAT / ALG bo'limida "SIP ALG" ni Disable qilish
```

MikroTik'da `h323` helper ham keraksiz — o'chirib qo'ysa bo'ladi.

---

## 7. Tekshirish va diagnostika

### 7.1. Pastdan yuqoriga — qatlam qatlam

```powershell
# 1) App tirikmi (loopback)
curl.exe -s -o NUL -w "%{http_code}`n" http://127.0.0.1:8080/actuator/health

# 2) LAN'dan ko'rinadimi (firewall tekshiruvi)
curl.exe -s -o NUL -w "%{http_code}`n" http://192.168.88.72:8080/actuator/health

# 3) Konteynerlar holati
docker compose ps

# 4) Asterisk PC'da portlarni egallaganmi
netstat -ano | Select-String "5060|8088|10000"
```

### 7.2. Asterisk tomonidan

```powershell
# Trunk registratsiyasi — "Registered" bo'lishi kerak
docker compose exec asterisk asterisk -rx "pjsip show registrations"

# Endpoint holati
docker compose exec asterisk asterisk -rx "pjsip show endpoints"

# Transport qanday manzil e'lon qilyapti (external_* to'g'ri o'qilganini tekshirish)
docker compose exec asterisk asterisk -rx "pjsip show transport transport-udp"

# SIP paketlarini jonli ko'rish (NAT muammosini shu yerda ko'rasiz)
docker compose exec asterisk asterisk -rx "pjsip set logger on"
docker compose logs -f asterisk

# RTP oqimi bormi
docker compose exec asterisk asterisk -rx "rtp set debug on"
```

SIP loglarida `Contact:` va SDP `c=` qatorlarida `90.156.197.92` turishi kerak.
Agar u yerda `172.x.x.x` ko'rinsa → `external_*_address` o'qilmagan yoki
Asterisk restart qilinmagan.

### 7.3. Hairpin NAT testi

Bu **eng ko'p chalkashtiradigan narsa**: LAN ichidan turib
`http://90.156.197.92:8080` ni sinash. Ko'p routerlar bunga ruxsat bermaydi
(hairpin/NAT loopback yo'q), shuning uchun **forward to'g'ri qo'yilgan bo'lsa
ham bu test muvaffaqiyatsiz bo'ladi**.

To'g'ri test — **tashqaridan**:

- Telefonni Wi-Fi'dan uzib, mobil internetdan `http://90.156.197.92:8080/actuator/health`.
- Yoki `https://www.yougetsignal.com/tools/open-ports/` kabi tekshirgich
  (faqat **TCP** portlar uchun ishonchli).

UDP portini (5060) tashqaridan tekshirish qiyin — tekshirgichlar UDP'ni
ishonchli ko'rsatmaydi. Amaliy usul: `pjsip set logger on` yoqib, provayderdan
paket kelayotganini loglarda kuzatish.

---

## 8. Xavfsizlik — nimalarni ochmaslik kerak

`docker-compose.yml` barcha servis portlarini host'ga chiqargan — bu **lokal
dev** uchun qulay, internetga ochish uchun emas.

- **`5060/udp` ochilishi bilan** avtomatik skanerlar sizni topadi va SIP
  brute-force boshlanadi (`friendly-scanner`, o'g'irlangan hisob bilan xalqaro
  qo'ng'iroqlar → real pul zarari). Shuning uchun:
  - Router'da forward'ni **faqat provayder IP'sidan** ruxsat bering:
    ```
    /ip firewall nat add chain=dstnat in-interface=ether1 protocol=udp \
        dst-port=5060 src-address=91.203.175.6 action=dst-nat \
        to-addresses=192.168.88.72 to-ports=5060
    ```
  - `pjsip_auth.conf` dagi parol kuchli bo'lsin.
  - Asterisk'da `fail2ban` yoki `/etc/asterisk/` security loglarini kuzatish.
- **`8088` (ARI)** — bu port + parol = PBX'ni to'liq boshqarish (qo'ng'iroq
  qilish, ovozni yozib olish). Hech qachon internetga chiqarmang.
- **`8080`** — actuator endpoint'lari va CRM integratsiyasi. Kerak bo'lsa
  faqat reverse proxy (nginx/Caddy) + HTTPS + autentifikatsiya orqali.
- **`15672` (RabbitMQ UI), `9001` (MinIO console), `5432`, `6379`** — default
  parollar bilan turadi (`.env.example` ga qarang). Ochilsa — to'liq
  kompromitatsiya.

Ochish kerak bo'lganda `docker-compose.yml` da publishni loopback'ga
cheklashni o'ylab ko'ring: `"127.0.0.1:15672:15672"`.

---

## 9. VPS / serverga ko'chirilganda

Public IP'li serverda (Uzbek hosting, Hetzner, DigitalOcean…) NAT muammosining
katta qismi yo'qoladi:

1. `external_media_address` / `external_signaling_address` — serverning public
   IP'sini yozing. Server IP'ni to'g'ridan-to'g'ri interfeysda tutsa
   (NAT'siz), bu ikki qatorni **umuman olib tashlash** ham to'g'ri.
2. `local_net` — kerak bo'lmaydi (yoki server ichki subnetini yozing).
3. Router forward'i o'rniga **provayder firewall'i / security group** da
   `5060/udp` va `10000-10200/udp` ni oching.
4. Linux'da `network_mode: host` ishlaydi — `docker-compose.yml` da
   `asterisk` servisi uchun izoh sifatida yozib qo'yilgan. Host networking
   NAT #2 qatlamini butunlay olib tashlaydi va SIP/RTP uchun eng barqaror
   variant. Bunda `RTP_LOCAL_IP` ni `app` emas, `127.0.0.1` yoki host IP'ga
   o'zgartirish kerak bo'ladi.

---

## 10. Dinamik IP muammosi

Uzbektelekom uy ulanishlarida IP odatda dinamik: router restart bo'lsa yoki
DHCP lease yangilansa `90.156.197.92` o'zgaradi. O'shanda `pjsip.conf` eski
IP'ni e'lon qilishda davom etadi → **qo'ng'iroq ulanadi, lekin ovoz yo'q**.

Yechimlar, yaxshiroqdan boshlab:

1. **Statik public IP** — provayderdan xizmat sifatida. Eng ishonchli.
2. **DDNS + hostname.** Asterisk `external_signaling_address` da domen nomini
   qabul qiladi, lekin **faqat start paytida** hal qiladi (resolve). Ya'ni IP
   o'zgarganda ham `docker compose restart asterisk` kerak. MikroTik'da tekin
   DDNS:
   ```
   /ip cloud set ddns-enabled=yes
   /ip cloud print          # → xxxxxxxx.sn.mynetname.net
   ```
3. **Qo'lda tekshirish** (IP o'zgardi deb gumon qilganda):
   ```powershell
   curl.exe -s https://ifconfig.me     # yangi IP
   # pjsip.conf:15-16 ni yangilash, keyin:
   docker compose restart asterisk
   ```

STUN (`asterisk/etc/asterisk/pjsip.conf` da `stun_server`) — bu yerda tavsiya
qilinmaydi: Asterisk'ning STUN qo'llashi cheklangan va `external_*_address`
bilan konflikt beradi. Statik IP yoki VPS ancha barqaror.

---

## 11. Muammolar jadvali

| Belgi | Ehtimoliy sabab | Nima qilish |
|---|---|---|
| Public IP:8080 ga kirilmayapti | Port forward yo'q, yoki hairpin ishlamayapti, yoki CGNAT | [§2.2](#22-cgnat-bormi-eng-muhim-tekshiruv), [§5](#5-port-forward-qilish), [§7.3](#73-hairpin-nat-testi) |
| `pjsip show registrations` → `Rejected` | Login/parol xato (`pjsip_auth.conf`), yoki provayder IP'ni bloklagan | SIP loggerni yoqib `401/403` javobini ko'rish |
| Registratsiya bor-yo'q bo'lib turadi | SIP ALG yoqilgan, yoki NAT teshigi yopilyapti | [§6](#6-sip-alg--albatta-ochiring), [§4.3](#43-outbound-only-boyicha-ogohlantirishlar) keepalive |
| Qo'ng'iroq ulanadi, **ovoz yo'q ikki tomondan** | `external_media_address` xato yoki eski IP | `pjsip show transport transport-udp`, [§3](#3-pjsipconf-dagi-ip-maydonlari) |
| Mijoz eshitadi, AI eshitmaydi (yoki teskari) | Bir tomonda RTP bloklangan — RTP forward yoki `rtp_symmetric` | `rtp set debug on`, RTP diapazon forward'i |
| Softphone `600` ga qo'ng'iroq qiladi, jim | `external_media_address` public IP'da qolgan (softphone LAN'da) | LAN IP variantiga o'tish — [§3](#3-pjsipconf-dagi-ip-maydonlari) jadvali |
| Asterisk log'ida `172.x.x.x` ko'rinadi | Docker IP e'lon qilinyapti; `external_*` o'qilmagan | `docker compose restart asterisk` |
| Tashqi qo'ng'iroq umuman kelmayapti | Inbound uchun forward shart + `[from-trunk]` `Hangup()` qilyapti | [§4.2](#42-amaliy-javob), `extensions.conf:15-17` |
| Kutilmagan xalqaro qo'ng'iroqlar / hisobdan pul ketishi | 5060 internetga ochiq, brute-force | Darhol forward'ni o'chirish, parolni almashtirish — [§8](#8-xavfsizlik--nimalarni-ochmaslik-kerak) |

---

## 12. Tez ma'lumotnoma

```
Public / WAN IP ........ 90.156.197.92   (Uzbektelekom, AS8193)
PC LAN IP .............. 192.168.88.72
Router / gateway ....... 192.168.88.1
LAN subnet ............. 192.168.88.0/24
SIP provayder .......... pbx.skyline.uz → 91.203.175.6 (Lit-Tel, AS47141)
SIP akkaunt ............ 781137492

Forward qilinadigan (trunk uchun):  5060/udp, 10000-10200/udp → 192.168.88.72
Hech qachon ochmaslik:              8088, 8080, 5432, 6379, 5672, 15672, 9000, 9001
```

Tegishli fayllar:

- `asterisk/etc/asterisk/pjsip.conf` — transport, external IP, trunk, softphone
- `asterisk/etc/asterisk/rtp.conf` — Asterisk RTP diapazoni (10000–10200)
- `asterisk/etc/asterisk/extensions.conf` — dialplan (`outbound-ai`, `from-trunk`, `from-internal`)
- `src/main/resources/application.yml` → `voice.rtp` — app'ning externalMedia sozlamalari
- `docker-compose.yml` — port publishing, `RTP_LOCAL_IP`
- `docs/RUN.md` — ishga tushirish buyruqlari