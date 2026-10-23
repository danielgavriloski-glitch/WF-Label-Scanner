# MarK Brain + Live Explorer — технички план vNext (23.10.2026)

**Статус:** ПЛАН / спецификација, не готов EXE/APK и не потврдено инсталирано решение. Не ги менува постојните податоци и работни гранки.

## Цел
MarK да работи локално како главна самостојна платформа за разговор, организација, фото/видео AI анализа, монтажа, учење од кориснички корекции и предлози за следни верзии. **Без Ollama сервер, инсталација или API**. MarK користи сопствена оркестрација и спакуван локален inference runtime; моделските тежини може да бидат од посебен лиценциран отворен модел. Python, ако остане вграден, е приватна техничка зависност, а не друг AI асистент.

## Проверена основа во изворот од 21.10.2026
- `pc/app/mark_core/runtime.py`: локален `llama-server.exe` на 127.0.0.1:8799, со `Qwen3VL-4B-Instruct-Q4_K_M.gguf` и `mmproj`; compat API на 127.0.0.1:8798. Ова **не е Ollama**, иако compat API објавува лажни/legacy ознаки `qwen2.5vl:3b`, `qwen3-vl:8b`, `qwen3:8b`. Самата ознака не докажува кои weights се вчитани.
- `pc/app/td_chat_addon.py`: има SQLite history/memories/corrections и legacy Ollama текст/алијаси. Чување корекција не значи fine-tuning на моделот.
- `pc/app/mark_core/wedding_media_v1.py`: веќе има COPY/MOVE, преглед на дестинации, журнал по фајл и безбедносни проверки. На тоа да се поврзе live event stream.
- `pc/installer_native/start_native.py`: стартува сопствен Python server, но целосен контролер за shutdown на сите **свои** процеси треба да се изработи и тестира.
- Точната тековно инсталирана верзија на корисничкиот Windows компјутер не е проверена. Никаков UI/AI/Windows физички тест не е извршен во оваа спецификација.

## 1. MarK Live Explorer
- Отворање/затворање по желба како dock panel или посебен MarK прозорец. Сортирањето мора да продолжува и кога Explorer е затворен.
- Два панели: **Извор** и **Дестинација** со вистински full paths, структура на папки, датотеки, preview и Live Activities.
- Вистински настани: `scan_started`, `folder_created`, `copy_started`, `bytes_copied`, `file_verified`, `source_deleted_after_verified_move`, `file_completed`, `paused`, `resumed`, `error`, `job_completed`.
- За секој настан: project_id, job_id, timestamp, item_id, source, destination, operation, byte_count, total_bytes, state, error (ако има). UI добива прогрес групиран (на пр. 5-10 Hz), но журналот ги запишува фактите без загуба.
- Копчиња: **Отвори дестинација во Windows Explorer**, **Копирај патека**, **Пауза/Продолжи**, **Стоп без губење на веќе завршеното**, **Покажи грешки**, **Историја**.
- Никогаш не се претставува симулиран прогрес како вистински. Ако апликацијата се затвори среде работа: реконструкција од journal/manifest и јасен recovery статус.
- COPY по подразбирање. MOVE бара посебна потврда со список на датотеки и предупредување. За MOVE оригиналот се брише дури по проверена копија; за rollback на MOVE е потребна посебна сигурна политика (не се ветува Undo без backup).

## 2. MarK Brain — разговор + vision + учење
- **MarK Orchestrator:** еден trusted controller ги повикува локалните модели, точните deterministic photo/video tools, својот memory index и status; моделски текст НЕ е команда за бришење или инсталација.
- **Model Provider:** плагирани локални runtime backends; прва поддршка `llama.cpp` со постоечки Qwen3-VL-4B GGUF. Во UI: **MarK Vision (локално)**, **MarK Chat**, статус/потрошувачка на RAM/VRAM и вистински model ID во дијагностика. Да се отстранат „Ollama“ зборови од видлив UI; legacy endpoint да се мигрира без прекин на Director.
- **Skill Engine:** посебни modules за scene detection, sharpness, eyes/faces in focus, blur, exposure, movement, emotion, audio/music, temporal continuity, thumbnail cache. Vision model не е единствен критерум; confidence + тестирани правила/алгоритми.
- **Learning Pipeline:** (a) собирање на дозволени факти и кориснички исправки; (b) кандидати за правила со објаснување/примери; (c) корисничко одобрување/исклучување; (d) мерење на ефект на test weddings. Не се менуваат автоматски model weights. Fine-tune само како посебен идно одобрен процес со податоци/лиценци.
- Меморијата се дели по клиент и проект; нема несакано пренесување меѓу студиа. Permissions: `No access` / `Read only` / `Read + learn from corrections` по модули и папки. Финансии, Booking, договори, приватни разговори и туѓи податоци НЕ се вклучуваат по default во AI учење. Можност за преглед, извоз, бришење записи и исклучување.
- Поддршка за македонски разговори и кратки јасни предлози. Секое тврдење за завршена задача е засновано на verified tool outcome.

## 3. Assistant за подобрувања на MarK
- Нова страница **MarK Development / Предлози**: собира грешки, проблеми, честопати повторени корекции и предложени features; ги рангира според безбедност, корисност и трошок.
- UI flow: **Предлог** → **Види измена/функции/ризик** → **Одобри подготовка** → isolated source branch / staging patch → build/tests → **Одобри инсталација** → **Backup + Update** → smoke tests → **Rollback** ако не успее.
- Не дозволува LLM да пишува или извршува код директно во production, да менува извршни фајлови додека MarK работи, или сам да инсталира без посебна корисничка потврда. Не се тврди дека модел може сигурно да прави самоподобрувања без тест.
- Нема пуштање приватни wedding media, модели, лични податоци, PIN-ови или signing keys во GitHub.

## 4. Комплетно изгаси MarK
- Посебна команда во главно мени и system tray: **Целосно изгаси MarK**.
- При активен COPY/MOVE: прво checkpoint/пауза, затворање на активни file handles и flush на journal; ако тоа не успее, јасна опција Stop job без лажна потврда.
- Процесен менаџер ги следи **само децата стартувани од конкретниот MarK instance** преку PID+creation time/Windows Job Object; редослед: UI → file jobs → API server → AI server → private Python workers → tray. Не гаси други `python.exe`, `llama-server.exe` или Adobe процеси на компјутерот.
- Проверен stop пред updater да заменува binaries. Независен updater работи дури откако главниот MarK е сопрен.
- Одвоени команди **Затвори прозорец** (однесување според setting: tray) и **Целосно изгаси MarK**.

## 5. Инсталација и постојана меморија
Предлог со избран root `D:\MarK\` или друг диск:

```text
D:\MarK\
  App\                 # заменлив UI, services, modules
  Runtime\             # приватни Python/llama/FFmpeg binaries
  Models\              # GGUF и други одобрени локални модели
  Brain\                # ТРАЈНИ: memory/, learning/, rules/, indexes/, profiles/
  Data\                 # Booking db, projects metadata, settings, logs
  Workspace\            # работни проекти, journal, tmp и cache
  Updates\              # incoming/, staged/, rollback/
  Backups\              # шифрирани/верзионирани резервни копии
```

- `App` и делови од `Runtime` се заменливи. `Brain`, `Data`, `Models`, `Workspace`, `Backups` **никогаш не се бришат при update**. Data migrations со backup и tested rollback; двонасочен Android sync со conflict-safe правила.
- Содржината за учење е неоверлив input: нема извршување скрипти од `Brain`, нема дозвола за системски папки, нема автоматско читање на цели дискови.
- Ако корисникот избере `C:\Program Files\MarK`, writable state живее во посебен per-user/AppData или администриран ProgramData root (не во заштитен Program Files).
- Поддршка за кориснички избор на диск, export/import, migration и recovery. Не се препишуваат стари TD Studio/Bridge и Android потписниот клуч.

## 6. Редослед и услови за прифаќање
1. **Foundation**: инспекција на актуелен инсталиран build; backup; вистински runtime/model status; UI без лажни Ollama имиња; чист shutdown на *сопствени* процеси.
2. **Explorer**: event journal+stream за COPY/MOVE, реална дестинација, независен прозорец, pause/cancel/checkpoint, 1.500+ photo/video mixed test.
3. **Brain/memory**: модулски дозволи и project isolation, corrections/approvals, Chat + Vision on same local runtime, benchmark against real wedding selection.
4. **Update advisor**: proposal UI, source branch, tests, approvals, signed installer, rollback, offline-first update.
5. **Integration**: компактен стабилен UI, преместување кон сите модули, Premiere 2026, Booking PC/Android sync, regression tests.

### Не смее да се прогласи за готово без
- Реален Windows тест: clean install + update over user's matching version + shutdown without orphan MarK child processes.
- Копирање и преместување со рестарт/снеман диск/дупликат имиња/непристапна папка; journal и оригинали без загуби.
- GPU CPU тест на RTX 3060 12GB со точна VRAM потрошувачка и квалитет спореден со првиот свадбен тест.
- No Ollama process, no Ollama API or install dependency across an actual running build.
- Учење само по дозвола и правило само по потврда. Нема самостојно деплојирање код.
