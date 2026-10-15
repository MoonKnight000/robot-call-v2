# robot-call-v2

AI voice agent: SIP orqali mijozlarga qo'ng'iroq qiladi (va kiruvchi qo'ng'iroqlarni qabul
qiladi), o'zbek/rus tilida suhbatlashadi va natijani CRM ga yozadi.

**Stack:** Java 21 · Spring Boot 4.1 · Spring AI 2.0 · Asterisk 20 (ARI) · PostgreSQL + Flyway ·
RabbitMQ · Redis · MinIO · Netty (RTP) · ONNX Runtime (VAD, turn detection)

## Tez boshlash

```powershell
cp .env.example .env      # CHANGE_ME qiymatlarini to'ldiring
docker compose up -d --build
docker compose logs -f app
```

App `http://localhost:8080` da, Swagger UI — `/swagger-ui.html`. Portlar, kirish tokeni,
softphone va brauzerdan test qilish: [docs/RUN.md](docs/RUN.md).

## Build va testlar

```powershell
./gradlew unitTest        # Docker'siz unit testlar + JaCoCo hisoboti
./gradlew test            # hammasi, Testcontainers testlari ham (Docker kerak)
./gradlew turnReplay --args="corpus --vad-model=models/silero_vad.onnx"
```

Qamrov hisoboti: `build/reports/jacoco/jacocoUnitTestReport/html/index.html`.

## Hujjatlar

| Qayerda                                  | Nima                                                            |
|------------------------------------------|-----------------------------------------------------------------|
| [CLAUDE.md](CLAUDE.md)                   | Arxitektura va kod qoidalari (paket tuzilmasi, nomlash, xatolar) |
| [docs/api/](docs/api/README.md)          | REST API — frontend shunga qarab ishlaydi                       |
| [docs/RUN.md](docs/RUN.md)               | Ishga tushirish, portlar, debug                                 |
| [docs/NETWORK.md](docs/NETWORK.md)       | Tarmoq: SIP, RTP, Asterisk                                      |
| [docs/ONPREM.md](docs/ONPREM.md)         | Mijoz serverida o'rnatish (bank/MFO): lokal nima, tashqi nima, firewall |
| [docs/claude/CLAUDE.md](docs/claude/CLAUDE.md) | Bosqichlar tarixi (arxiv)                                 |

## Tuzilma

Har bir biznes feature `uz.murodjon.robotcallv2.<feature>` ostida hexagonal qatlamlarga
bo'lingan (`presentation` → `application` → `domain`, `infrastructure` → `application`);
etalon — `aimodel`. Voice pipeline (`agent/`) esa texnik tamoyil bo'yicha bo'lingan: `ari`,
`rtp`, `vad`, `stt`, `tts`, `dialog`, … Qoidalarni `ArchitectureTest` build vaqtida tekshiradi.
