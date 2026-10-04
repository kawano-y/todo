# CLAUDE.md

Pixel 6a 向けの個人用 Todo Android アプリ。

## 仕様

- タスクは **プライベート (PRIVATE)** と **仕事 (WORK)** の 2 グループに分かれる
- タスクを完了するとタスク一覧から消える
- 完了したタスクは別タブ「完了」で参照できる（未完了に戻す・削除も可能）
- 1 タスクにつき画像を **最大 1 枚** 紐づけられる
- 作成フロー: FAB → グループ選択 → タスク内容（タイトル必須・メモ任意）入力 → 画像があれば添付 → 保存

### 画面構成

| 画面 | 内容 |
|---|---|
| タスク一覧（下部タブ「タスク」） | 上部タブでプライベート/仕事を切替。チェックで完了 → 一覧から消え、Snackbar で「元に戻す」。FAB で新規作成。行タップで編集 |
| 完了一覧（下部タブ「完了」） | 上部タブでプライベート/仕事を切替。完了日時の新しい順。行タップで詳細（編集画面を読み取り表示）。未完了に戻す / 削除 |
| タスク作成・編集 | グループ選択、タイトル、メモ、画像（Photo Picker で選択・プレビュー・削除）、保存 |

画像付きタスクは一覧でサムネイルを表示する。

## 技術スタック

- 対象端末: Pixel 6a（Android 12L 以降）→ `minSdk 31` / `targetSdk 35` / `compileSdk 35`
- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- Room（KSP）, Kotlin Coroutines / Flow
- 画像: Photo Picker (`ActivityResultContracts.PickVisualMedia`) — ストレージ権限不要。表示は Coil
- DI: Hilt は使わず `AppContainer` による手動 DI
- ビルド: Gradle Kotlin DSL + Version Catalog (`gradle/libs.versions.toml`)、Java 21、Gradle 8.5 以上、AGP 8.x
- パッケージ名: `com.kawano.todo`

## アーキテクチャ

```
ui (Compose Screen + ViewModel)  →  domain (model / interface)  ←  data (Room / ImageStorage 実装)
```

- UI 層は `domain` の interface のみに依存し、`data` の実装クラスを直接参照しない
- ViewModel は `StateFlow<UiState>` を公開し、Screen は `collectAsStateWithLifecycle()` で購読
- ViewModel 生成は `AppContainer` 経由の `viewModelFactory { initializer { ... } }`
- 画像は `content://` URI をそのまま保存せず、`filesDir/images/` にコピー（長辺 2048px に縮小・EXIF 回転補正、JPEG）して **絶対パス** を DB に保存する
- タスク削除時・画像差し替え時は古い画像ファイルも削除する

### ディレクトリ構成

```
app/src/main/java/com/kawano/todo/
├── TodoApplication.kt          # AppContainer を保持
├── MainActivity.kt
├── di/AppContainer.kt
├── domain/
│   ├── model/Task.kt           # Task, TaskGroup
│   ├── repository/TaskRepository.kt
│   └── image/ImageStorage.kt
├── data/
│   ├── local/                  # TaskEntity, TaskDao, TodoDatabase, Converters
│   ├── repository/TaskRepositoryImpl.kt
│   └── image/ImageStorageImpl.kt
└── ui/
    ├── theme/
    ├── navigation/TodoNavHost.kt   # 下部ナビ + ルーティング
    ├── components/                 # GroupTabRow, TaskThumbnail など共通部品
    ├── tasklist/                   # TaskListScreen, TaskListViewModel
    ├── completed/                  # CompletedScreen, CompletedViewModel
    └── edit/                       # TaskEditScreen, TaskEditViewModel
app/src/test/.../fake/FakeTaskRepository.kt, FakeImageStorage.kt   # UI テスト用フェイク
```

## 共有コントラクト（変更禁止・変更する場合は全タスクに影響するので要相談）

```kotlin
// domain/model/Task.kt
enum class TaskGroup { PRIVATE, WORK }

data class Task(
    val id: Long = 0,
    val title: String,
    val note: String = "",
    val group: TaskGroup,
    val imagePath: String? = null,   // filesDir 内の絶対パス
    val isCompleted: Boolean = false,
    val createdAt: Long,             // epoch millis
    val completedAt: Long? = null,
)

// domain/repository/TaskRepository.kt
interface TaskRepository {
    fun observeActiveTasks(group: TaskGroup): Flow<List<Task>>     // createdAt 降順
    fun observeCompletedTasks(group: TaskGroup): Flow<List<Task>>  // completedAt 降順
    suspend fun getTask(id: Long): Task?
    suspend fun upsert(task: Task): Long
    suspend fun setCompleted(id: Long, completed: Boolean)        // completedAt も更新
    suspend fun delete(id: Long)                                  // 画像ファイル削除は呼び出し側 (ViewModel) の責務
}

// domain/image/ImageStorage.kt
interface ImageStorage {
    suspend fun save(uri: Uri): String   // コピー後の絶対パスを返す
    suspend fun delete(path: String)
}
```

画面の Composable シグネチャ（`TodoNavHost` から呼ばれる）:

```kotlin
@Composable fun TaskListScreen(onAddTask: (TaskGroup) -> Unit, onOpenTask: (Long) -> Unit)
@Composable fun CompletedScreen(onOpenTask: (Long) -> Unit)
@Composable fun TaskEditScreen(onDone: () -> Unit)   // taskId / 初期 group は SavedStateHandle から取得
```

ルート: `tasks`, `completed`, `edit?taskId={taskId}&group={group}`（taskId 未指定 = 新規）
- edit の引数（`SavedStateHandle` のキー。定数は `ui/navigation/TodoNavHost.kt` の `EditArgs`）: `taskId` は `Long`・未指定（新規）は `-1L`（`EditArgs.NEW_TASK_ID`）、`group` は `String?`（`TaskGroup.name`、新規作成時の初期グループ。既存編集時は null）

## 規約

- UI 文言は日本語。文字列は `res/values/strings.xml` に置く（ハードコード禁止）
- 各 Screen には `@Preview` を付け、ViewModel を受け取らない stateless な `XxxContent(state, onEvent...)` を分離する
- I/O は `Dispatchers.IO`（Room の suspend 関数はそのままで可）
- テスト: ViewModel は `app/src/test` で Fake を使ったユニットテスト、DAO は `app/src/androidTest` で in-memory Room
- 自分の担当外のファイルは編集しない（下記「並列作業ルール」）

## ビルド・実行

```bash
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
./gradlew installDebug           # Pixel 6a を USB デバッグで接続しておく
```

- Android SDK: `%LOCALAPPDATA%\Android\Sdk`（platforms 34/35, build-tools 34 導入済み）。`local.properties` の `sdk.dir` で指定（git 管理外）
- JDK 21 を使用（環境変数 `JAVA_HOME` で指定済みの前提）。コンパイルターゲット（`jvmToolchain` / `JavaVersion`）も 21 に設定する

## 並列作業ルール

タスク分割と各サブエージェントの担当範囲は [docs/TASKS.md](docs/TASKS.md) を参照。

- 各タスクは「担当ファイル」に列挙されたファイルのみ作成・編集する
- 共有コントラクトや担当外ファイルの変更が必要になった場合は、実装せずに最終報告に「要変更点」として記載する
- `strings.xml` は競合しやすいので、各タスクは `res/values/strings_<task>.xml`（例: `strings_tasklist.xml`）に分けて追加する
- 依存ライブラリは T0 で `libs.versions.toml` に全て登録済みにしておき、後続タスクでは追加しない
