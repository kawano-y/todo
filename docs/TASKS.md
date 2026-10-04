# タスク分割（サブエージェント並列実装用）

前提: 全タスクは [CLAUDE.md](../CLAUDE.md) の仕様・共有コントラクト・並列作業ルールに従う。

## 依存関係

```
Wave 0（直列）   T0 プロジェクト土台 + コントラクト + 空の画面
                   │
Wave 1（並列）   ├─ T1 データ層 (Room)
                 ├─ T2 画像ストレージ
                 ├─ T3 タスク一覧画面
                 ├─ T4 完了一覧画面
                 └─ T5 作成・編集画面
                   │
Wave 2（直列）   T6 統合・実機確認
```

T1〜T5 は互いに依存しない。UI タスク (T3〜T5) は T0 で用意する Fake 実装で開発・テストする。
各サブエージェントは `isolation: "worktree"` で起動し、完了後にブランチをマージする（担当ファイルが重ならないので競合しない想定）。

---

## T0: プロジェクト土台（Wave 0 / 直列・最初に実施）

**目的**: 後続タスクが並列で作業できるよう、ビルドが通る骨組みと共有コントラクトを用意する。

**担当ファイル**
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `.gitignore`
- `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`
- `app/src/main/res/**`（アイコン, `values/strings.xml` のアプリ名, テーマ）
- `TodoApplication.kt`, `MainActivity.kt`, `di/AppContainer.kt`, `di/InMemoryImplementations.kt`（暫定実装。T6 で削除可）
- `domain/**`（コントラクトを CLAUDE.md の通りに作成）
- `ui/theme/**`, `ui/navigation/TodoNavHost.kt`
- `ui/components/GroupTabRow.kt`（PRIVATE/WORK 切替タブ）, `ui/components/TaskThumbnail.kt`（Coil で imagePath 表示）
- 空の画面: `ui/tasklist/TaskListScreen.kt`, `ui/completed/CompletedScreen.kt`, `ui/edit/TaskEditScreen.kt`（シグネチャのみ・「TODO」表示）
- `app/src/test/java/com/kawano/todo/fake/FakeTaskRepository.kt`, `FakeImageStorage.kt`（MutableStateFlow ベース）, `FakeRepositoriesTest.kt`
- `app/src/test/java/com/kawano/todo/testutil/MainDispatcherRule.kt`（ViewModel テスト用）
- `AppContainer` は暫定で Fake 実装 or `TODO()` を返す in-memory 実装をバインド

**依存ライブラリ（すべてここで登録）**: Compose BOM, material3, material-icons-extended, activity-compose, navigation-compose, lifecycle-viewmodel-compose, lifecycle-runtime-compose, room-runtime/ktx/compiler(KSP), coil-compose, androidx.exifinterface, kotlinx-coroutines-test, junit, turbine, androidx.test (ext-junit, runner), room-testing, compose-ui-test, mockk（ユニットテストで `android.net.Uri` を作るため: `mockk<Uri>()`）

**完了条件**
- `./gradlew assembleDebug` と `./gradlew testDebugUnitTest` が成功
- アプリ起動で下部ナビ（タスク/完了）が表示され、画面遷移できる
- 初回コミットを `main` に作成（以降の worktree の起点）

---

## T1: データ層 Room（Wave 1）

**担当ファイル**: `data/local/TaskEntity.kt`, `TaskDao.kt`, `TodoDatabase.kt`, `Converters.kt`, `data/repository/TaskRepositoryImpl.kt`, `app/src/androidTest/.../data/TaskDaoTest.kt`, `app/src/test/.../data/TaskRepositoryImplTest.kt`（必要なら）

**内容**
- `TaskEntity`（テーブル `tasks`、`group` は String で保存）と `Task` との相互変換
- DAO: グループ別の未完了一覧（createdAt DESC）、完了一覧（completedAt DESC）を Flow で返す。get/upsert/setCompleted/delete
- `setCompleted(id, true)` で `completedAt = System.currentTimeMillis()`、false で null
- `TodoDatabase` version 1, `exportSchema = true`（`app/schemas/`）
- DAO の instrumented テスト（in-memory DB）

**完了条件**: `TaskRepository` を完全実装し、DAO テストが通る。`AppContainer` への組み込みは T6 で行う（担当外）。

---

## T2: 画像ストレージ（Wave 1）

**担当ファイル**: `data/image/ImageStorageImpl.kt`, `app/src/test/.../data/ImageStorageImplTest.kt`（Robolectric を使わない範囲で純粋ロジック部分をテスト）, `app/src/androidTest/.../data/ImageStorageImplTest.kt`

**内容**
- `save(uri)`: `ContentResolver` から読み込み → `BitmapFactory` の `inSampleSize` で長辺 2048px 以下に縮小 → `ExifInterface` で回転補正 → `filesDir/images/<UUID>.jpg` に JPEG(品質 85) 保存 → 絶対パスを返す
- `delete(path)`: `filesDir/images/` 配下のファイルのみ削除（パス検証）
- すべて `Dispatchers.IO` で実行
- 縮小サイズ計算は純粋関数に切り出してユニットテスト

**完了条件**: `ImageStorage` を完全実装しテストが通る。

---

## T3: タスク一覧画面（Wave 1）

**担当ファイル**: `ui/tasklist/TaskListScreen.kt`, `ui/tasklist/TaskListViewModel.kt`, `ui/tasklist/TaskListUiState.kt`, `res/values/strings_tasklist.xml`, `app/src/test/.../ui/tasklist/TaskListViewModelTest.kt`

**内容**
- `GroupTabRow` でプライベート/仕事を切替（選択状態は ViewModel が保持）
- 未完了タスクを `LazyColumn` で表示: チェックボックス, タイトル, メモ 1 行, `TaskThumbnail`
- チェック → `setCompleted(id, true)` → 一覧から消える → Snackbar「完了しました / 元に戻す」で `setCompleted(id, false)`
- FAB → `onAddTask(currentGroup)`、行タップ → `onOpenTask(id)`
- 空状態表示（「タスクはありません」）
- ViewModel テスト（Fake + Turbine）: グループ切替、完了、元に戻す

---

## T4: 完了一覧画面（Wave 1）

**担当ファイル**: `ui/completed/CompletedScreen.kt`, `ui/completed/CompletedViewModel.kt`, `ui/completed/CompletedUiState.kt`, `res/values/strings_completed.xml`, `app/src/test/.../ui/completed/CompletedViewModelTest.kt`

**内容**
- `GroupTabRow` でグループ切替、完了タスクを completedAt 降順で表示（完了日時表示、取り消し線、サムネイル）
- 行のメニュー or スワイプで「未完了に戻す」「削除」（削除は確認ダイアログ。画像があれば `ImageStorage.delete` も呼ぶ）
- 行タップ → `onOpenTask(id)`
- 空状態表示
- ViewModel テスト: 戻す・削除（画像削除が呼ばれること）

---

## T5: タスク作成・編集画面（Wave 1）

**担当ファイル**: `ui/edit/TaskEditScreen.kt`, `ui/edit/TaskEditViewModel.kt`, `ui/edit/TaskEditUiState.kt`, `res/values/strings_edit.xml`, `app/src/test/.../ui/edit/TaskEditViewModelTest.kt`

**内容**
- `SavedStateHandle` から `taskId`（null=新規）と `group` を取得。既存タスクならロード
- 入力: グループ（SegmentedButton）, タイトル（必須・空なら保存不可）, メモ（複数行）
- 画像: 「画像を添付」ボタン → `PickVisualMedia(ImageOnly)` → 選択 URI をプレビュー → 「削除」「差し替え」
- 保存時: 新しい画像 URI があれば `ImageStorage.save` → 旧画像があれば `delete` → `upsert`。画像を外した場合も旧画像を `delete`
- 新規作成時の `createdAt` はここで設定
- 完了済みタスクを開いた場合も編集可能（グループ・完了状態はそのまま）
- 保存中はボタン無効化、完了後 `onDone()`
- ViewModel テスト: 新規保存、編集保存、画像差し替えで旧画像削除、タイトル空で保存不可

---

## T6: 統合・実機確認（Wave 2 / T1〜T5 マージ後）

**担当ファイル**: `di/AppContainer.kt`, `di/InMemoryImplementations.kt`（不要になれば削除）, `ui/navigation/TodoNavHost.kt`, 必要に応じて全体

**内容**
- 各 worktree ブランチを `main` にマージ
- `AppContainer` を `TaskRepositoryImpl` / `ImageStorageImpl` に差し替え
- `strings_*.xml` の重複キー・命名を整理
- `./gradlew assembleDebug testDebugUnitTest` 成功、可能なら `connectedDebugAndroidTest`
- Pixel 6a に `installDebug` して手動確認チェックリスト:
  - [ ] プライベート/仕事でタスクが分かれて表示される
  - [ ] 完了でタスク一覧から消え、完了タブに出る。元に戻せる
  - [ ] 画像付きタスクを作成でき、サムネイル・プレビューが表示される
  - [ ] 画像の差し替え・削除後に古いファイルが残らない
  - [ ] アプリ再起動後もデータが保持される
  - [ ] ダークモード・画面回転で表示が崩れない

---

## サブエージェントへの指示テンプレート

```
あなたは Todo Android アプリの <Tn: タスク名> の担当です。
まず CLAUDE.md と docs/TASKS.md の <Tn> セクションを読んでください。
- 「担当ファイル」以外は作成・編集しないこと
- 共有コントラクトは変更しないこと。変更が必要なら最終報告に「要変更点」として書くこと
- 完了条件のテスト/ビルドを実行し、結果を報告すること
- 作業内容をコミットすること（メッセージ例: "feat(<tn>): ..."）
最終報告: 作成/変更ファイル一覧、テスト結果、要変更点、残課題
```
