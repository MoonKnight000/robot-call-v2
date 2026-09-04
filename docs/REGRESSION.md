# REGRESSION — o'zgarish nimani buzganini avtomatik topish

> Ikkita to'r, ikki xil savolga javob beradi. **TurnReplayTool:** "turn qayerda tugadi?"
> — audio, tarmoqsiz, har push'da. **DialogSimulationRunner:** "agent nima qaror qildi?"
> — LLM bilan, kechasi va talab bo'yicha. Ikkalasi birga suhbatning ikki yarmini qoplaydi.

---

## 1. Nima qachon ishlaydi

| To'r | Workflow | Qachon | Nima kerak | Nima ushlaydi |
|------|----------|--------|------------|----------------|
| Unit testlar | `ci.yml` → `build` | har push | hech narsa | oddiy regressiya |
| Turn replay | `ci.yml` → `turn-replay` | har push | korpus + VAD modeli | endpointing sozlamasi turn chegarasini surib yuborgani |
| Persona simulyatsiyasi | `simulation.yml` | kecha 02:00 UTC + qo'lda | `GEMINI_API_KEY` + Postgres | prompt/tool/ssenariy o'zgarishi suhbat natijasini buzgani |

**Nega simulyatsiya har push'da emas.** Har persona — to'liq suhbat, har turn — ikkita LLM
chaqiruvi (agent + mijoz). 20 persona × ~10 turn × 2 = ~400 chaqiruv har push uchun. README
tahriri ham shuncha to'laydi, va provayder yiqilgan kuni hech narsa o'zgarmagan build
qizaradi. Kechasi ishlash ham xuddi shu narsalarni ushlaydi.

---

## 2. Korpus (turn replay uchun)

### 2.1. Nega repoda emas

Yozuvlar — haqiqiy mijoz qo'ng'iroqlari: ism, shartnoma raqami, qarz summasi, ovoz.
**Bularni git'ga qo'yish mumkin emas** — repo klonlangan har joyga PII tarqaladi va
o'chirish tarixdan olib tashlamaydi. Shuning uchun korpus obyekt-xotirada turadi va CI uni
sirlar (secrets) orqali oladi.

Sirlar sozlanmagan bo'lsa, `turn-replay` job'i **skip qiladi va o'tadi** — fork yoki yangi
klon o'zi ololmaydigan ma'lumot tufayli bloklanmaydi.

### 2.2. Kerakli GitHub secrets

| Secret | Nima |
|--------|------|
| `REPLAY_CORPUS_ENDPOINT` | MinIO/S3 ulanish satri (`mc` uchun `MC_HOST_*` formatida) |
| `REPLAY_CORPUS_BUCKET` | korpus turgan bucket nomi |
| `VAD_MODEL_URL` | `silero_vad.onnx` uchun yuklab olish havolasi |

### 2.3. Korpus tuzilishi

```
corpus/
├── manifest.tsv
├── 2026-08-14-promise.wav
├── 2026-08-14-refusal.wav
└── ...
```

`manifest.tsv` — har yozuvda nechta mijoz turn'i yopilishi kerakligi:

```
# fayl                      kutilgan turn soni
2026-08-14-promise.wav      4
2026-08-14-refusal.wav      3
2026-08-15-wrong-number.wav 2
```

Tab yoki bo'shliq bilan ajratiladi, `#` — komment. **Manifestda yo'q fayl tekshirilmaydi**
— o'ynatiladi va hisobotga chiqadi, lekin build'ni qizartira olmaydi. Sabab: korpus
belgilanishidan tez o'sadi, va belgilanmagan yozuv tufayli CI qizarsa, odamlar yozuv
qo'shishni to'xtatadi.

### 2.4. Turn sonini qanday belgilash

1. Yozuvni tinglang va **mijoz** necha marta gapirib to'xtaganini sanang (bot turnlari emas).
2. Yozuv mijoz gapirayotgan joyda uzilgan bo'lsa, o'sha yakuniy turn ham sanaladi.
3. Shubha bo'lsa — **belgilamang.** Noto'g'ri belgilangan yozuv har push'da yolg'on
   ogohlantirish beradi va bir haftadan keyin hech kim to'rga ishonmaydi.

### 2.5. Yiqilgan qo'ng'iroq → yangi test

Sifatsiz o'tgan har bir real qo'ng'iroq (mijoz kesilgan, gap ikkiga bo'lingan) korpusga
qo'shiladi va manifestga to'g'ri turn soni bilan yoziladi. Bu — VOICE-QUALITY-PLAN D.4.

---

## 3. Lokal ishga tushirish

```bash
# Turn replay — bitta fayl yoki butun papka
./gradlew turnReplay --args="corpus \
  --vad-model=models/silero_vad.onnx \
  --manifest=corpus/manifest.tsv"

# Persona simulyatsiyasi (LLM sarflaydi, Postgres kerak)
GEMINI_API_KEY=... \
SIMULATION_ENABLED=true \
SIMULATION_PERSONAS=docs/simulation-personas.json \
SIMULATION_SCENARIO_ID=<qarzdorlik ssenariysi id> \
./gradlew bootRun
```

Ikkalasi ham xato bo'lganda **1 qaytaradi** — log o'qish shart emas.

---

## 4. A.1 qarori: endpointing provayderda qoladimi yoki gate'ga ko'chadimi

Bu — VOICE-QUALITY-PLAN A.1 va auditdagi F-02 ning ochiq qismi. Qaror **ikkita mustaqil
o'lchov** talab qiladi, va ularning birortasini ham taxmin bilan almashtirib bo'lmaydi.

### 4.1. Muhim cheklov

`TurnReplayTool` **provayderning endpointing'ini o'lchay olmaydi.** Yandex'ning qarori
uning serverida qabul qilinadi; offline replay uni takrorlay olmaydi. Replay faqat bitta
savolga javob beradi: *agar biz gate'ga o'tsak, turn chegaralari joyida qoladimi?*

### 4.2. Birinchi yarim — gate turn chegaralarini ushlaydimi (offline)

```bash
scripts/replay-sweep.sh corpus models/silero_vad.onnx models/smart_turn_v3.onnx
```

Skript bir nechta gate sozlamasini korpus ustida yuritadi va jadval chiqaradi:

```
config              checked   failed    avg eou
shipped                  30        0      980ms
post-roll-700            30        1      690ms
post-roll-500            30        4      500ms
adaptive-600             30        0      720ms
smart-early-300          30        0      480ms
```

**O'qish qoidasi:** `failed > 0` — konfiguratsiya rad etiladi. Bitta surilgan turn chegarasi
— bu bitta gapning ikkiga bo'linib, ikki marta javob berilishi. `failed = 0` bo'lganlar
orasidan eng past `avg eou` yutadi.

Agar **hech bir** konfiguratsiya `failed = 0` bermasa — savol yopildi: gate uz-UZ nutqida
turn chegarasini ushlay olmaydi, endpointing provayderda qoladi. Bu haqiqiy natija, muvaffaqiyatsizlik emas.

### 4.3. Ikkinchi yarim — provayder bugun qanchaga tushyapti (jonli)

F-01 dan keyin bu o'lchanadi. 100+ qo'ng'iroqdan keyin Prometheus'dan:

```
voice.turn.stage.latency{stage="eou"}        # VAD "mijoz jim bo'ldi" degan payt
voice.turn.stage.latency{stage="stt_final"}  # o'shandan final transkriptgacha
```

Mijoz kutgan haqiqiy endpointing vaqti — **shu ikkisining yig'indisi** (p50 va p95).

### 4.4. Qaror jadvali

| 4.2 natijasi | 4.3 natijasi (eou + stt_final p50) | Qaror |
|--------------|-------------------------------------|-------|
| hech biri `failed = 0` emas | ahamiyatsiz | **Provayderda qoladi.** `turn.*` inert bo'lib qolaveradi (startdagi WARN shuni aytadi) |
| bor, eng yaxshi avg eou ≥ jonli qiymat | — | **Provayderda qoladi.** Ko'chishdan yutuq yo'q |
| bor, eng yaxshi avg eou jonli qiymatdan ≥200 ms past | — | **Gate'ga ko'chiriladi** (pastga qarang) |

### 4.5. Ko'chirish qanday amalga oshiriladi

Uchta bayroq **birga** yoqiladi — bittasi yolg'iz hech narsa qilmaydi (F-02 ning asli shu edi):

```
STT_VAD_GATING=true          # gate umuman quriladi
STT_ENDPOINTING=true         # gate EOU haqida qaror qabul qiladi
TURN_DETECTOR=true           # Smart Turn kutishni uzaytiradi/qisqartiradi
STT_VAD_POST_ROLL_MS=<4.2 dagi g'olib qiymat>
TURN_EARLY_WAIT_MS=<g'olibda early-close bo'lsa>
```

Yoqilgandan keyin **birinchi ish** — startda F-02 WARN'lari **yo'qolganiga** ishonch hosil
qilish. Ular hamon chiqsa, gate qurilmayapti va sozlama hech narsa qilmayapti.

Keyin bir hafta kuzatuv: `voice.stt.turn.extended` va `voice.stt.turn.closed.early`
hisoblagichlari o'ssin, `voice.turnaround.latency` p95 tushsin, va — eng muhimi — bitta
gapning ikkita turn'ga bo'linishi 5% dan oshmasin (D.3 LLM-judge `talked_over` bayrog'i
buni ko'rsatadi).

Orqaga qaytarish — deploy'siz: `STT_VAD_GATING=false`.
