# Ishga tushirish (Docker, Windows)

Barcha buyruqlar loyiha ildizida (`robot-call-v2`), PowerShell'da bajariladi.
Windows/Docker Desktop'da RTP ishlashi uchun app Asterisk bilan bir Docker
tarmog'ida turishi kerak — shuning uchun `gradlew bootRun` emas, `docker compose`.

Birinchi marta: `.env.example` dan `.env` yarating va kalitlarni to'ldiring.

## Asosiy

```powershell
# app'ni qayta build qilib ko'tarish (infra allaqachon turgan bo'lsa ham yetadi)
docker compose up -d --build app

# loglarni kuzatish
docker compose logs -f app
```

Butun stack'ni noldan ko'tarish:

```powershell
docker compose up -d --build
docker compose logs -f app
```

## Tekshirish

```powershell
# xato bo'lmasligi kerak — hech narsa qaytarmasa, yaxshi
docker compose logs app | Select-String "initialDelay"

# @Scheduled task'lar o'z scheduler'ida ishlayaptimi (sched-1 / sched-2)
docker compose logs app | Select-String "sched-"

# app javob beryaptimi
curl.exe http://localhost:8080/actuator/health
```

## Boshqarish

```powershell
docker compose restart app   # qayta build'siz restart
docker compose ps            # konteynerlar holati
docker compose stop app      # faqat app'ni to'xtatish
docker compose down          # hammasini to'xtatish (volume'lar saqlanadi)
docker compose down -v       # volume'lar bilan o'chirish (DB ma'lumotlari yo'qoladi)
```

## Portlar

|   | Servis   | Port                                                       |
|:--|----------|------------------------------------------------------------|
|   | app      | 8080                                                       |
|   | Asterisk | 5060/udp (SIP trunk), 5062/udp (SIP softphone), 8088 (ARI) |
|   | RTP      | 10000-10200/udp                                            |
|   | Postgres | 5432                                                       |
|   | Redis    | 6379                                                       |
|   | RabbitMQ | 5672, 15672 (UI)                                           |
|   | MinIO    | 9000 (S3), 9001 (konsol)                                   |

## Softphone

MicroSIP / Zoiper sozlamalari:

|   | Maydon | Qiymat               |
|:--|--------|----------------------|
|   | server | `192.168.0.100:5062` |
|   | user   | `softphone`          |
|   | parol  | `softphone123`       |

Port **5062**, 5060 emas — 5060 trunk uchun band (`pjsip.conf` dagi ikkita transport).

Ro'yxatdan o'tgach ikki yo'nalishni ham sinash mumkin:

```powershell
# 1. Kiruvchi: softphone'dan 600 ni tering -> AI javob beradi

# 2. Chiquvchi: app o'zi softphone'ni chaqiradi (trunk daqiqasi sarflanmaydi)
curl.exe -X POST "http://localhost:8080/api/calls?number=600"

# 3. Chiquvchi, real raqam (trunk orqali) — `+` belgisisiz
curl.exe -X POST "http://localhost:8080/api/calls?number=998953692029"
```

3-4 xonali raqamlar softphone'ga, uzunroqlari trunk'ga yo'naltiriladi
(`voice-agent.asterisk.local-number-pattern`).

## Trunk / SIP debug

SIP paketlari logi `pjsip.conf` dagi `[global] debug = yes` orqali doimiy yoqilgan —
CLI'da `pjsip set logger on` terish shart emas (u har restartda unutiladi).

```powershell
docker compose logs -f asterisk                                    # SIP trace
docker exec -it uysot-asterisk asterisk -rx "pjsip show registrations"
docker exec -it uysot-asterisk asterisk -rx "pjsip show contacts"
docker exec -it uysot-asterisk asterisk -rx "pjsip show channelstats"  # RxPkt/TxPkt
```

Ishlagach `debug = no` qilib qo'ying — aks holda softphone'ning har REGISTER'i logni to'ldiradi.

Tashqi IP, NAT, router'da port forward va "ovoz kelmayapti" muammolari uchun:
[NETWORK.md](NETWORK.md).
