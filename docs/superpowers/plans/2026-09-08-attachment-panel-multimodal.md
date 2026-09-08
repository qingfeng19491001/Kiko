# DeepSeek Attachment Panel And Multimodal Attachments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the current attachment panel with a keyboard-replacement interaction and deliver real Android/iOS/HarmonyOS attachment selection, persistence, and Alibaba multimodal requests.

**Architecture:** Keep UI and orchestration in `commonMain`. Add a Kuikly `AttachmentModule` contract with platform adapters that copy selected data into app-private storage. Persist attachment metadata alongside chat messages; build image content directly for `qwen3.8-flash`, and upload documents to `qwen-long` before mixed requests.

**Tech Stack:** Kotlin Multiplatform, Kuikly traditional DSL, Kuikly Module APIs, Android Activity Result/SAF, iOS UIKit pickers, HarmonyOS native picker APIs, Alibaba OpenAI-compatible Chat API.

---

### Task 1: Public attachment model and deterministic rules

**Files:**
- Create: `shared/src/commonMain/kotlin/com/kuikly/stockchat/domain/attachment/Attachment.kt`
- Create: `shared/src/commonMain/kotlin/com/kuikly/stockchat/domain/attachment/AttachmentRules.kt`
- Test: `shared/src/commonTest/kotlin/com/kuikly/stockchat/domain/attachment/AttachmentRulesTest.kt`

- [ ] Write failing tests for text-or-attachment send eligibility, default prompts, attachment ordering, and removal by id.
- [ ] Run `./gradlew :shared:allTests` and verify the new tests fail because the model/rules do not exist.
- [ ] Implement `AttachmentKind`, `AttachmentSource`, `AttachmentStatus`, immutable `Attachment`, and pure `AttachmentRules` functions.
- [ ] Re-run the focused common tests, then all shared tests.
- [ ] Commit `feat: add attachment domain model`.

### Task 2: Persist attachments in chat messages

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/kuikly/stockchat/domain/chat/ChatMessage.kt`
- Modify: `shared/src/commonMain/kotlin/com/kuikly/stockchat/data/chat/ChatCodec.kt`
- Modify: `shared/src/commonMain/kotlin/com/kuikly/stockchat/ui/chat/ChatUiModels.kt`
- Modify: `shared/src/commonMain/kotlin/com/kuikly/stockchat/ui/chat/ChatViewModel.kt`
- Test: `shared/src/commonTest/kotlin/com/kuikly/stockchat/data/chat/ChatCodecAttachmentTest.kt`

- [ ] Add a failing round-trip test for a user message containing image and document attachments, including failed/expired status.
- [ ] Run the focused test and confirm failure before implementation.
- [ ] Add attachment lists to domain/UI message conversion and encode/decode every metadata field with backward-compatible empty defaults.
- [ ] Change `ChatViewModel.send` to accept attachments, clear pending state only after creating the user message, and allow attachment-only sends.
- [ ] Make retry reuse the last user message's attachments and block when any referenced local file is missing.
- [ ] Run codec and ViewModel tests; commit `feat: persist chat attachments`.

### Task 3: Kuikly AttachmentModule contract

**Files:**
- Create: `shared/src/commonMain/kotlin/com/kuikly/stockchat/data/attachment/AttachmentModule.kt`
- Modify: `shared/src/commonMain/kotlin/com/kuikly/stockchat/app/StockChatPage.kt`
- Test: `shared/src/commonTest/kotlin/com/kuikly/stockchat/data/attachment/AttachmentModulePayloadTest.kt`

- [ ] Write failing tests for decoding native callback payloads and distinguishing user cancellation from picker failure.
- [ ] Implement module methods `openCamera`, `openPhotoLibrary`, `openFilePicker`, callback payload decoding, and `createExternalModules()` registration.
- [ ] Keep callbacks asynchronous and return JSON strings/primitive-safe payloads per Kuikly Module rules; never block the Kuikly thread while reading files.
- [ ] Run focused tests and commit `feat: add cross-platform attachment module contract`.

### Task 4: Android native picker adapter

**Files:**
- Create: `androidApp/src/main/java/com/kuikly/stockchat/android/attachment/KRAttachmentModule.kt`
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Modify: `androidApp/src/main/java/com/kuikly/stockchat/android/KuiklyRenderActivity.kt`
- Test: `androidApp/src/test/java/com/kuikly/stockchat/android/attachment/AttachmentMimeTest.kt`

- [ ] Add failing MIME mapping tests for camera images, gallery images, and arbitrary documents.
- [ ] Implement Activity Result launchers for `TakePicture`, `GetMultipleContents`, and `OpenMultipleDocuments`; request camera/read permissions only when needed.
- [ ] Copy content resolver streams to app-private attachment storage, create image thumbnails, and return stable metadata; return a cancellation result without an error banner.
- [ ] Run Android unit tests and `./gradlew :androidApp:assembleDebug`.
- [ ] Commit `feat: add Android attachment pickers`.

### Task 5: iOS native picker adapter

**Files:**
- Create: `iosApp/KuiklyStockChat/KRAttachmentModule.h`
- Create: `iosApp/KuiklyStockChat/KRAttachmentModule.m`
- Modify: `iosApp/KuiklyStockChat/KuiklyRenderViewController.m`
- Modify: `iosApp/KuiklyStockChat/Info.plist`

- [ ] Implement camera, `PHPickerViewController`, and `UIDocumentPickerViewController` flows with delegate callbacks.
- [ ] Copy selected security-scoped/document-provider data into the app container and generate image thumbnails before invoking the Kuikly callback.
- [ ] Add camera/photo usage descriptions and ensure cancellation does not mutate pending attachments.
- [ ] Run the iOS build through the repository Podfile and verify picker callback wiring.
- [ ] Commit `feat: add iOS attachment pickers`.

### Task 6: HarmonyOS native picker adapter

**Files:**
- Create/modify: HarmonyOS host Module registration files discovered under the OHOS app/adapter sources
- Test: HarmonyOS module payload tests where the host test harness permits

- [ ] Locate the existing Kuikly HarmonyOS Module registration point and write payload tests first.
- [ ] Implement camera, photo, and document picker calls; copy selected data to app-private storage and return the same JSON payload shape as Android/iOS.
- [ ] Verify `./gradlew -c settings.ohos.gradle.kts :shared:linkDebugSharedOhosArm64` and host compilation.
- [ ] Commit `feat: add HarmonyOS attachment pickers`.

### Task 7: Multimodal Alibaba request builder

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/kuikly/stockchat/data/network/HttpClient.kt`
- Modify: `shared/src/commonMain/kotlin/com/kuikly/stockchat/data/ai/RemoteAiEngine.kt`
- Modify: `shared/src/commonMain/kotlin/com/kuikly/stockchat/data/ai/AiConfig.kt`
- Create: `shared/src/commonMain/kotlin/com/kuikly/stockchat/data/ai/AttachmentPromptBuilder.kt`
- Test: `shared/src/commonTest/kotlin/com/kuikly/stockchat/data/ai/AttachmentPromptBuilderTest.kt`

- [ ] Write failing tests for image-only, document-only, and mixed request plans, asserting `image_url` content and `fileid://` document flow.
- [ ] Extend HTTP abstraction with binary/file upload support without changing existing market requests.
- [ ] Implement image Base64 Data URL construction, controlled image metadata, document upload/cleanup, and mixed two-stage orchestration; use server errors for unsupported format/size messaging.
- [ ] Keep text-only request behavior unchanged and parse both normal completion and streamed response shapes required by the new requests.
- [ ] Run focused tests and shared compilation; commit `feat: add Alibaba multimodal request pipeline`.

### Task 8: DeepSeek-style composer UI and state machine

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/kuikly/stockchat/ui/chat/ChatViews.kt`
- Modify: `shared/src/commonMain/kotlin/com/kuikly/stockchat/app/StockChatPage.kt`
- Modify: `shared/src/commonMain/kotlin/com/kuikly/stockchat/ui/components/Icons.kt`
- Test: `shared/src/commonTest/kotlin/com/kuikly/stockchat/ui/chat/AttachmentComposerStateTest.kt`

- [ ] Write failing state tests for keyboard/panel mutual exclusion, plus-to-close behavior, picker cancellation, attachment deletion, and attachment-only send.
- [ ] Remove the content-area attachment backdrop and render the panel in the keyboard replacement slot; animate input/panel as one bottom composer.
- [ ] Add the pending attachment strip above the input, thumbnail/file cards, remove buttons, loading/error/expired states, and dynamic send enabled state.
- [ ] Wire camera/photo/file actions to `AttachmentModule`, preserve pending items through errors, and connect send/retry to `ChatViewModel`.
- [ ] Run common tests and build the Android app; commit `feat: redesign attachment composer`.

### Task 9: Message attachment rendering and cleanup

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/kuikly/stockchat/ui/chat/ChatViews.kt`
- Modify: `shared/src/commonMain/kotlin/com/kuikly/stockchat/ui/chat/HistoryDrawerView.kt`
- Create: `shared/src/commonMain/kotlin/com/kuikly/stockchat/data/attachment/AttachmentStore.kt`
- Test: `shared/src/commonTest/kotlin/com/kuikly/stockchat/data/attachment/AttachmentStoreTest.kt`

- [ ] Test reference counting/cleanup decisions for deleting a conversation and pruning orphaned files.
- [ ] Render persisted attachment cards in user messages and historical conversations without displaying the excluded recent-photo row.
- [ ] Implement cleanup of deleted-conversation files and stale unreferenced files while retaining files referenced by retryable messages.
- [ ] Run focused tests and all platform builds available in the environment.
- [ ] Commit `feat: render and clean up persisted attachments`.

### Task 10: Verification and integration

**Files:**
- Modify: only files needed to address verification failures
- Test: existing common, Android, iOS, and HarmonyOS test/build targets

- [ ] Run `./gradlew :shared:allTests`.
- [ ] Run `./gradlew :androidApp:assembleDebug`.
- [ ] Run the iOS build/test command supported by the local Xcode/Podfile setup.
- [ ] Run `./gradlew -c settings.ohos.gradle.kts :shared:linkDebugSharedOhosArm64`.
- [ ] Review `git diff` for accidental changes, verify API keys and local attachment data are not added, then document any platform build limitations.
