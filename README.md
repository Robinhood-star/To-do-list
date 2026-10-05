# My Tasks – a private, offline Android to-do app

A native Android to-do list that runs **100% on your phone**: no account, no login, no backend, no cloud, no internet permission.

## 1. What the app does

- Dashboard with greeting, date and a "today / completed / remaining (/overdue)" summary
- Sections: **Overdue, Today, Upcoming, No date, Completed**
- Swipe **right** to complete (or re-open), swipe **left** to delete, both with an **Undo** snackbar
- Quick add with offline natural-language parsing: *"Call Pankaj tomorrow 10 AM"* fills in date and time
- Priorities (Low / Medium / High / Urgent), categories (editable), subtasks with `2/4` progress
- Recurring tasks: daily, weekdays, weekly, monthly, custom "every N days"
- Local reminders (AlarmManager) that survive app restart, phone reboot, app update, and clock/timezone changes
- Search (title, notes, category), filters (All, Today, Upcoming, Overdue, Completed, High priority, Category), sorting (due time, priority, newest)
- Date picker (calendar icon) to see tasks for any day
- Light / Dark / System theme, JSON export & import through the Android file picker

## 2. Tech stack

Kotlin 2.0 · Jetpack Compose + Material 3 · Room (KSP) · ViewModel + StateFlow · Navigation Compose · Coroutines · AlarmManager · Gradle Kotlin DSL + version catalog.
AGP 8.7.3, Gradle 8.10.2, JDK 17, compileSdk/targetSdk 35, minSdk 26 (Android 8.0+).
There are no analytics, ads or networking libraries. WorkManager is intentionally **not** used: nothing needs to run periodically, so there is no background work at all (cleanup runs at app start; reminders are exact alarms).

## 3. Project structure

```
app/src/main/java/com/personal/todo/
  TodoApp.kt, AppContainer.kt, MainActivity.kt
  data/        Room entities, DAOs, database, TaskRepository, SettingsStore, BackupJson
  domain/      Models, TaskLogic (sections/filter/search/sort), RecurrenceLogic, NaturalDateParser, ReminderTime
  reminders/   AlarmReminderScheduler, ReminderReceiver, BootReceiver, NotificationHelper
  ui/          Theme, HomeScreen, EditTaskScreen, SettingsScreen, ViewModels, AppNav
app/src/test/          JVM unit tests (logic, parser, backup JSON, retention)
app/src/androidTest/   Room repository tests + a Compose UI test (need an emulator/device)
.github/workflows/     build-apk.yml (debug APK on every push), release.yml (signed/unsigned release APK + AAB)
```

Layering: `UI → ViewModel → TaskRepository → Room DAO → SQLite`.

## 4. Open in Android Studio

1. Install the current stable Android Studio (it bundles JDK 17).
2. *File → Open* and choose this folder. Let Gradle sync.
   (The repository does not include `gradle-wrapper.jar`; Android Studio handles this. To create the wrapper from a terminal: `gradle wrapper --gradle-version 8.10.2`, then commit the generated files.)

## 5. Build the APK locally

```
gradle testDebugUnitTest      # unit tests (or ./gradlew … once the wrapper exists)
gradle assembleDebug
```
The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## 6. Install the APK on your phone

1. Copy `app-debug.apk` to the phone (USB, Drive, email to yourself…) and open it.
2. Allow "Install unknown apps" for the app you opened it from when Android asks.
3. Open **My Tasks**. When you first switch a reminder on, allow notifications.
   For on-time reminders also allow *Exact alarms* (Settings → Notifications → "Allow exact alarms"). Without it reminders still work but may be a few minutes late.

## 7. How GitHub Actions builds the APK

`.github/workflows/build-apk.yml` runs on every push to `main`/`master`, on pull requests, and manually. It checks out the code, sets up JDK 17, the Android SDK and Gradle 8.10.2 (with caching), runs the unit tests, builds the debug APK and uploads it as an artifact.

`release.yml` runs manually or on tags like `v1.0.0` and builds a release APK and an AAB. Add the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` to get a signed build; without them the release APK is unsigned.

## 8. Where to find the generated APK

GitHub repo → **Actions** tab → latest *Build APK* run → **Artifacts** section → `PersonalTodo-debug-apk` (a zip containing `app-debug.apk`). Download it on your phone or computer, unzip, install.

## 9. How data is stored

All data lives in a private Room/SQLite database (`todo.db`) in the app's internal storage, plus a small SharedPreferences file for settings. Android's cloud auto-backup is switched off (`allowBackup=false`), so nothing leaves the device. Dates are stored as epoch-day numbers and times as minutes after midnight, so reminders follow the phone's current timezone.
Tables: `tasks`, `subtasks` (foreign key, cascade delete), `categories` (deleting one leaves its tasks uncategorised). Export/Import uses JSON (version 1). Importing matches tasks by a stable `uid`; if a task already exists the copy with the newer `updatedAt` wins, so importing the same file twice never duplicates anything.

## 10. Privacy and data retention

- No analytics, ads, tracking SDKs, accounts or cloud sync. The app does not request the INTERNET permission.
- Permissions used: notifications, boot-completed (to restore reminders) and exact alarms (optional).
- **Retention:** completed tasks are kept 30 days and then deleted automatically when the app starts. Active/upcoming/overdue tasks are never deleted by age. Change the default in one place: `AppConfig.DEFAULT_RETENTION_DAYS` in `domain/Models.kt`, or at runtime in Settings → Tasks.

## Behaviour notes

- A recurring task creates its next occurrence **when you complete it** (overdue ones catch up to today). Monthly repeats clamp to the month end (31 Jan → 28 Feb).
- A task has one reminder, offset 0 min to 1 day before the due time. Tasks with a date but no time remind at 9:00.
- Undoing a delete restores the task and its subtasks; once the snackbar disappears the task is gone for good.
