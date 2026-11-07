# Notifications Architecture & Push Specification

This document details the on-device notification triggers, creative messages, schedule windows, deep-link behaviors, and in-app update notifications for **Arrow Escape** (`com.clevercorner.arrowescape`).

---

## 1. System Architecture

Arrow Escape implements a **100% on-device, local-only, privacy-first** notification system using Android Jetpack **WorkManager** and **NotificationManagerCompat**. No remote push servers, personal IDs, or external tokens are involved.

```
+------------------------------------------------------------------------------------------+
|                                    WorkManager Job                                       |
|  * Interval: Periodic check every 6 hours (with 1-hour flex window)                      |
|  * Worker: DailyReminderWorker                                                           |
+------------------------------------------------------------------------------------------+
                                              |
                                              v
+------------------------------------------------------------------------------------------+
|                              DailyReminderUseCase Evaluation                             |
|                                                                                          |
|  [Global Anti-Spam Gatekeepers]                                                          |
|   1. Is notificationsEnabled == true in Settings? (If false: Abort & cancel job)         |
|   2. Is POST_NOTIFICATIONS runtime permission granted? (If false: Abort)                 |
|   3. Has a notification been sent in the last 20 hours? (If true: Abort)                 |
|   4. Is current local time within quiet hours (10:00 PM – 10:00 AM)? (If true: Abort)    |
|                                                                                          |
|  [Trigger Priority Ladder]                                                               |
|   Priority 1: Inactivity Win-Back (>= 3 days without opening app)                        |
|   Priority 2: Weekend Zen (Saturday or Sunday, 11:00 AM – 4:00 PM)                       |
|   Priority 3: Evening Streak Saver (Daily, 6:00 PM – 10:00 PM, Streak >= 1)              |
|   Priority 4: Alternate-Day Challenge (Daily/Alternate, 5:00 PM – 10:00 PM, Streak == 0) |
+------------------------------------------------------------------------------------------+
                                              |
                                              v
+------------------------------------------------------------------------------------------+
|                                  Notification Display                                    |
|  * Channel: "Arrow Escape" (general)                                                     |
|  * Style: BigTextStyle (prevents message truncation in drawer)                          |
|  * Icon: ic_launcher_foreground                                                          |
|  * Tap Action: Opens MainActivity with deep-link destination extra                       |
|  * GA4 Events: notification_shown & notification_opened                                  |
+------------------------------------------------------------------------------------------+
```

---

## 2. Notification Triggers, Timing & Message Matrix

### Trigger 1: Evening Streak Saver
* **Goal**: Prevent players with active daily puzzle streaks from unintentionally losing their progress before midnight.
* **Trigger Points & Preconditions**:
  - `currentPrefs.daily.streak >= 1` (Player has an active streak of at least 1 day)
  - `!currentPrefs.daily.isCleared(today)` (Today's daily puzzle is NOT yet solved)
  - `LocalTime.now().hour in 18..21` (Evening window between 6:00 PM and 10:00 PM)
* **Time & Window**: **6:00 PM – 10:00 PM** daily
* **String Resources**:
  - **Title**: `notification_streak_saver_title`
  - **Body**: `notification_streak_saver_body` (Accepts `%1$d` integer formatting for current streak)
* **English Message**:
  - **Title**: `🔥 Don't break your streak!`
  - **Body**: `Your %1$d-day streak is waiting on you. The arrows won't untangle themselves!`
  - *(Example: "Your 7-day streak is waiting on you. The arrows won't untangle themselves!")*
* **Deep Link Destination**: `daily` ➔ Directly opens the **Daily Challenge Screen**.
* **GA4 Telemetry**: `notification_shown` with `type="streak_saver"`.

---

### Trigger 2: Inactivity Win-Back
* **Goal**: Re-engage dormant players who haven't launched the game in several days with playful, humorous copy.
* **Trigger Points & Preconditions**:
  - `daysSinceLastAppOpen >= 3` (72+ hours since last app session recorded in `last_app_open_time`)
  - `LocalTime.now().hour in 10..21` (Any daytime hour between 10:00 AM and 10:00 PM)
* **Time & Window**: Triggered on the first daytime WorkManager evaluation once 72 hours of inactivity have elapsed.
* **String Resources**:
  - **Title**: `notification_inactivity_title`
  - **Body**: `notification_inactivity_body`
* **English Message**:
  - **Title**: `The arrows miss your touch! 🏹`
  - **Body**: `They got tangled up and started arguing. Come untangle them in 60 seconds!`
* **Deep Link Destination**: `home` ➔ Opens the **Main Home Screen**.
* **GA4 Telemetry**: `notification_shown` with `type="inactivity_winback"`.

---

### Trigger 3: Weekend Brain Escape
* **Goal**: Encourage relaxed, pressure-free gameplay during the weekend leisure hours.
* **Trigger Points & Preconditions**:
  - `DayOfWeek == SATURDAY` or `DayOfWeek == SUNDAY`
  - `LocalTime.now().hour in 11..16` (Midday window between 11:00 AM and 4:00 PM)
  - `!currentPrefs.daily.isCleared(today)` (Hasn't played/cleared today's puzzle yet)
* **Time & Window**: **11:00 AM – 4:00 PM** on Saturdays and Sundays.
* **String Resources**:
  - **Title**: `notification_weekend_title`
  - **Body**: `notification_weekend_body`
* **English Message**:
  - **Title**: `Weekend brain escape ☕`
  - **Body**: `No timers, no rush. Just smooth, satisfying arrow untangling.`
* **Deep Link Destination**: `home` ➔ Opens the **Main Home Screen**.
* **GA4 Telemetry**: `notification_shown` with `type="weekend_zen"`.

---

### Trigger 4: Alternate-Day / Curiosity Challenge
* **Goal**: Nudge players who don't have an active streak to try today's unique board without daily spam.
* **Trigger Points & Preconditions**:
  - `currentPrefs.daily.streak == 0` (No active streak at risk)
  - `!currentPrefs.daily.isCleared(today)` (Today's puzzle is not solved)
  - `daysSinceLastAppOpen >= 1` (At least 24 hours since last play session)
  - `LocalTime.now().hour in 17..21` (Late afternoon/evening between 5:00 PM and 10:00 PM)
* **Time & Window**: **5:00 PM – 10:00 PM** on alternate days.
* **String Resources**:
  - **Title**: `notification_daily_fun_title`
  - **Body**: `notification_daily_fun_body`
* **English Message**:
  - **Title**: `Today's puzzle thinks it's clever 😏`
  - **Body**: `A fresh board just landed. Can you solve it in under 2 minutes?`
* **Deep Link Destination**: `daily` ➔ Directly opens the **Daily Challenge Screen**.
* **GA4 Telemetry**: `notification_shown` with `type="daily_challenge"`.

---

## 3. Global Anti-Spam & User Respect Rules

| Rule | Enforcement Mechanism | Purpose |
| :--- | :--- | :--- |
| **20-Hour Cooldown** | Guarded by `nowMillis - lastNotificationTime >= 20.hours`. | Guarantees at most **1 notification per calendar day**. |
| **Strict Quiet Hours** | Guarded by `hour !in 10..21`. | Completely disables pushes between **10:00 PM and 10:00 AM**. |
| **Same-Day Play Silence** | Checked via `todayCleared` and `daysSinceOpen`. | If the user already completed today's daily puzzle or is actively playing, zero reminder notifications will fire. |
| **Instant Opt-Out** | Toggle in **Settings ➔ Daily Reminders**. | Unchecking the setting immediately cancels all scheduled background workers via `WorkManager.cancelUniqueWork()`. |

---

## 4. Multi-Language Translations (All 15 Locales)

All messages are translated, verified, and escaped across all supported locales:

| Language (Code) | Streak Saver Title & Body | Inactivity Title & Body | Weekend Zen Title & Body | Fun Challenge Title & Body |
| :--- | :--- | :--- | :--- | :--- |
| **English** (`values`) | **🔥 Don't break your streak!**<br>Your %1$d-day streak is waiting on you. The arrows won't untangle themselves! | **The arrows miss your touch! 🏹**<br>They got tangled up and started arguing. Come untangle them in 60 seconds! | **Weekend brain escape ☕**<br>No timers, no rush. Just smooth, satisfying arrow untangling. | **Today's puzzle thinks it's clever 😏**<br>A fresh board just landed. Can you solve it in under 2 minutes? |
| **German** (`values-de`) | **🔥 Lass deine Serie nicht reißen!**<br>Deine Serie von %1$d Tagen wartet auf dich. Die Pfeile entwirren sich nicht von allein! | **Die Pfeile vermissen dich! 🏹**<br>Sie haben sich verheddert und streiten schon. Hilf ihnen in 60 Sekunden! | **Wochenend-Entspannung für den Kopf ☕**<br>Kein Stress, kein Timer. Einfach herrlich entspannende Pfeilrätsel. | **Das heutige Rätsel hält sich für schlau 😏**<br>Ein neues Spielbrett ist da. Kannst du es in unter 2 Minuten lösen? |
| **Spanish** (`values-es`) | **🔥 ¡No pierdas tu racha!**<br>Tu racha de %1$d días te está esperando. ¡Las flechas no se van a desenredar solas! | **¡Las flechas te extrañan! 🏹**<br>Se enredaron y empezaron a discutir. ¡Ven a desenredarlas en 60 segundos! | **Descanso mental de fin de semana ☕**<br>Sin prisas ni temporizadores. Solo rompecabezas relajantes y satisfactorios. | **El acertijo de hoy se cree muy listo 😏**<br>Nuevo tablero disponible. ¿Podrás resolverlo en menos de 2 minutos? |
| **French** (`values-fr`) | **🔥 Ne brisez pas votre série !**<br>Votre série de %1$d jours vous attend. Les flèches ne vont pas se démêler toutes seules ! | **Les flèches s\'ennuient sans vous ! 🏹**<br>Elles se sont emmêlées et se disputent. Venez les démêler en 60 secondes ! | **Pause détente du week-end ☕**<br>Sans stress ni chrono. Juste le plaisir de débloquer des flèches fluides. | **Le puzzle du jour se prend pour un génie 😏**<br>Un nouveau plateau vient d\'arriver. Pouvez-vous le résoudre en moins de 2 minutes ? |
| **Italian/Indonesian** (`values-id`/`in`) | **🔥 Jangan putuskan rentetanmu!**<br>Rentetan %1$d harimu menunggumu. Panah-panah ini tidak bisa lepas sendiri! | **Panah-panah merindukanmu! 🏹**<br>Mereka kusut dan saling menuduh. Ayo lepaskan mereka dalam 60 detik! | **Relaksasi otak akhir pekan ☕**<br>Tanpa buru-buru, tanpa batas waktu. Nikmati teka-teki panah yang memuaskan. | **Teka-teki hari ini merasa pintar 😏**<br>Papan baru telah siap. Bisakah kamu menyelesaikannya dalam 2 menit? |
| **Portuguese** (`values-pt-rBR`) | **🔥 Não quebre sua sequência!**<br>Sua sequência de %1$d dias está esperando por você. As setas não vão se desatar sozinhas! | **As setas estão com saudades! 🏹**<br>Elas se enrolaram todas e começaram a brigar. Venha desemaranhar em 60 segundos! | **Relaxamento mental de fim de semana ☕**<br>Sem pressa e sem cronômetro. Apenas quebra-cabeças satisfatórios de setas. | **O quebra-cabeça de hoje se acha esperto 😏**<br>Um novo tabuleiro chegou. Consegue resolver em menos de 2 minutos? |
| **Russian** (`values-ru`) | **🔥 Не потеряй серию!**<br>Твоя серия из %1$d дн. ждет тебя. Стрелочки сами себя не распутают! | **Стрелочки соскучились! 🏹**<br>Они запутались и начали спорить. Приходи и распутай их за минуту! | **Передышка на выходных ☕**<br>Без спешки и таймеров. Только плавные и приятные головоломки со стрелками. | **Сегодняшняя головоломка возомнила себя хитрой 😏**<br>Новая доска уже готова. Сможешь решить быстрее чем за 2 минуты? |
| **Turkish** (`values-tr`) | **🔥 Serini kaybetme!**<br>%1$d günlük serin seni bekliyor. Oklar kendi kendine çözülmeyecek! | **Oklar seni özledi! 🏹**<br>Birbirine dolandılar ve tartışıyorlar. Gel ve 60 saniyede onları çöz! | **Hafta sonu zihin molası ☕**<br>Acele yok, süre sınırı yok. Sadece rahatlatıcı ve keyifli ok bulmacaları. | **Bugünün bulmacası kendini akıllı sanıyor 😏**<br>Yeni tahta hazır. 2 dakikadan kısa sürede çözebilir misin? |
| **Japanese** (`values-ja`) | **🔥 連続記録を途切れさせないで！**<br>%1$d日間の連勝記録が待っています。矢印たちは自力では脱出できません！ | **矢印たちが寂しがっています！🏹**<br>絡まり合って動けなくなっています。サクッと1分で助け出そう！ | **週末の脳トレタイム☕**<br>制限時間なし、焦りゼロ。心地よいパズルでリフレッシュしましょう。 | **今日のパズルは手強そうですよ😏**<br>新しいボードが登場しました。2分以内にクリアできますか？ |
| **Korean** (`values-ko`) | **🔥 연속 기록을 지키세요!**<br>%1$d일 연속 기록이 기다리고 있어요. 화살표들은 스스로 풀리지 않아요! | **화살표들이 기다리고 있어요! 🏹**<br>화살표들이 뒤엉켜 있어요. 60초 만에 시원하게 풀어보세요! | **주말 힐링 퍼즐 타임 ☕**<br>시간제한 없이 여유롭게. 부드럽고 만족스러운 퍼즐을 즐겨보세요. | **오늘의 퍼즐이 꽤 까다로워 보이네요 😏**<br>새로운 보드가 준비되었습니다. 2분 안에 해결할 수 있을까요? |
| **Chinese** (`values-zh-rCN`) | **🔥 别让连胜纪录断掉！**<br>你的 %1$d 天连胜正在等你。箭头们可不会自己解开！ | **箭头们想念你了！🏹**<br>它们全缠在一起吵起来了，快来用 60 秒解开它们！ | **周末大脑放松时光 ☕**<br>无需赶时间，没有倒计时。只有丝滑治愈的箭头解谜。 | **今天的谜题好像有点小聪明 😏**<br>新棋盘已就绪。你能在 2 分钟内解开它吗？ |
| **Hindi** (`values-hi`) | **🔥 अपनी स्ट्रीक को टूटने न दें!**<br>आपकी %1$d दिनों की स्ट्रीक आपका इंतज़ार कर रही है। तीर अपने आप नहीं सुलझेंगे! | **तीर आपको याद कर रहे हैं! 🏹**<br>वे आपस में उलझ गए हैं। आइए और 60 सेकंड में उन्हें सुलझाइए! | **वीकेंड पर दिमाग को आराम दें ☕**<br>कोई जल्दबाजी नहीं, कोई टाइमर नहीं। बस सुकून देने वाली तीर पहेलियां। | **आज की पहेली खुद को बहुत चालाक समझ रही है 😏**<br>एक नया बोर्ड तैयार है। क्या आप इसे 2 मिनट में हल कर सकते हैं? |
| **Arabic** (`values-ar`) | **🔥 لا تدع سلسلتك تنكسر!**<br>سلسلتك لمدة %1$d أيام في انتظارك. الأسهم لن تفك تشابكها بنفسها! | **الأسهم تفتقدك! 🏹**<br>لقد تشابكت وبدأت بالجدال. تعال وحررها في 60 ثانية! | **استراحة ذهنية في عطلة نهاية الأسبوع ☕**<br>لا وقت محدد ولا استعجال. فقط ألغاز أسهم مريحة وممتعة. | **لغز اليوم يظن نفسه ذكياً 😏**<br>لوحة جديدة بانتظارك. هل يمكنك حلها في أقل من دقيقتين؟ |
| **Thai** (`values-th`) | **🔥 อย่าให้สถิติต่อเนื่องหลุด!**<br>สถิติ %1$d วันของคุณกำลังรออยู่ ลูกศรไม่ได้คลี่คลายเองนะ! | **ลูกศรคิดถึงคุณแล้วนะ! 🏹**<br>พวกมันพันกันยุ่งเหยิงไปหมด มาช่วยคลี่คลายใน 60 วินาทีกันเถอะ! | **ผ่อนคลายสมองในวันหยุด ☕**<br>ไม่ต้องรีบ ไม่มีจับเวลา แค่เพลิดเพลินกับปริศนาลูกศรโค้งแสนเพลิน | **ปริศนาวันนี้ดูท้าทายไม่เบา 😏**<br>กระดานใหม่มาแล้ว คุณจะแก้ได้ภายใน 2 นาทีไหม? |
| **Vietnamese** (`values-vi`) | **🔥 Đừng để đứt chuỗi chiến thắng!**<br>Chuỗi %1$d ngày của bạn đang chờ. Những mũi tên không tự gỡ rối được đâu! | **Những mũi tên đang nhớ bạn đấy! 🏹**<br>Chúng bị rối vào nhau rồi. Hãy vào gỡ rối chỉ trong 60 giây! | **Thư giãn cuối tuần ☕**<br>Không vội vã, không giới hạn thời gian. Chỉ có những mũi tên thư giãn và êm dịu. | **Câu đố hôm nay có vẻ rất thông minh 😏**<br>Bảng mới đã sẵn sàng. Bạn có thể giải nó trong dưới 2 phút không? |

---

## 5. In-App Notification: New App Update

In addition to background lockscreen notifications, the app includes an on-screen **In-App Update Banner** driven by the official Google Play Core In-App Updates API (`com.google.android.play:app-update-ktx`).

### Behavior & Features
1. **Non-Intrusive Banner**: Placed at the top of the Home screen without obscuring gameplay or buttons.
2. **Session Dismissal**: Tapping `[Later]` smoothly hides the banner for the duration of the current session.
3. **Flexible Download**: The update downloads in the background while the player continues playing.
4. **Fallback Handling**: If Google Play In-App Updates are unsupported (e.g. sideloaded or custom ROM), tapping `[Update Now]` cleanly opens the Google Play Store app listing.
5. **Unified Settings Control (3-Option Selector)**:
   - The developer simulation option is placed exclusively in **Settings ➔ Developer Tools ➔ "Simulate App Update"**.
   - Features 3 persistent modes:
     - **`[Off]`**: Normal production behavior (no mock).
     - **`[Available 🚀]`**: Persistent: shows the "Update Available" banner with `[Update Now]` on the Home screen across all launches.
     - **`[Downloaded ✨]`**: Persistent: shows the "Update Downloaded" banner with `[Restart & Play]` on the Home screen across all launches.
6. **Minimal, Non-Spammy Analytics**:
   - `app_update_shown`: Throttled via `RateLimitedAnalyticsTracker` to at most **1 per session** (eliminating noise from screen rotations or tab switches).
   - `app_update_action`: Fired only upon explicit user button tap (`"update"`, `"restart"`, or `"dismiss"`).
