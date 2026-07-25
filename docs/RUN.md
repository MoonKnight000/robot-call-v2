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

| Servis     | Port                          |
|------------|-------------------------------|
| app        | 8080                          |
| Asterisk   | 5060/udp (SIP), 8088 (ARI)    |
| RTP        | 10000-10200/udp               |
| Postgres   | 5432                          |
| Redis      | 6379                          |
| RabbitMQ   | 5672, 15672 (UI)              |
| MinIO      | 9000 (S3), 9001 (konsol)      |

Softphone bilan trunk'siz test qilish uchun `600` raqamiga qo'ng'iroq qiling.

Tashqi IP, NAT, router'da port forward va "ovoz kelmayapti" muammolari uchun:
[NETWORK.md](NETWORK.md).
