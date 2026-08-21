# Poker Vision v0.3.0 Multi-Card Scanner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Poker Vision v0.3.0 with a guided one-view multi-card scanner for 2 hole cards + up to 5 board cards, plus a shared suit-first/value-second manual picker, while preserving stable QA signing and 10-total-player equity support.

**Architecture:** Keep CameraX for capture and ML Kit OCR as a secondary hint, add OpenCV 4.14.0 for card geometry/perspective/template recognition, and keep scan proposals separate from `PokerTableState` until atomic confirmation. Pure Java domain logic (picker state, zone mapping, voting, duplicate validation, batch table updates) stays JVM-testable; OpenCV-dependent recognition gets Android instrumentation fixture tests.

**Tech Stack:** Java 17, Android SDK 36 / minSdk 24, CameraX 1.4.2, ML Kit Text Recognition 16.0.1, OpenCV Android Maven AAR 4.14.0, JUnit 4.13.2, AndroidX Test, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-22-poker-vision-v0.3.0-multicard-scanner-design.md`

## Global Constraints

- Version name is exactly `0.3.0`; version code is exactly `14`.
- QA package remains `com.fantest.pokervision.qa`; production package remains `com.fantest.pokervision`.
- QA APK must use the existing stable QA signer; production must not use the public QA signer.
- minSdk remains 24; targetSdk/compileSdk remain 36; Java remains 17.
- Existing 1-10 total player support remains: hero plus 1-9 opponents.
- Use `org.opencv:opencv:4.14.0`.
- Keep CameraX and ML Kit; OCR is secondary and OCR-only confidence is capped at `0.49`.
- Card-discovery long edge: max `960 px`; recognition cadence: max `4 frames/second`.
- Normalized card: `350x500`; index crop: `x=0..105`, `y=0..190`.
- Card ratio: `0.58..0.78`; area: `1.5%..30%` of active scanner area.
- Duplicate contour IoU: `>=0.70`; track IoU: `>=0.35`; fallback center distance: `<=0.12` of candidate diagonal.
- Track expiry: `1200 ms`; high confidence: `>=0.78`; amber: `>=0.55 && <0.78`; below `0.55` neutral.
- Stable recognition requires the same exact rank+suit in at least `4 of the latest 5` usable observations.
- Board zone normalized rectangle: `x=0.05..0.95`, `y=0.08..0.48`; hole-card zone: `x=0.18..0.82`, `y=0.58..0.92`.
- No silent scan commit; scan results enter review state and require **Confirm All**.
- Manual picker is suit first, then rank/value; choosing a valid value saves immediately.
- No Jetpack Compose migration, cloud recognition, ads, monetization, chip/bet recognition, face-down-card recognition, or overlapping-card support in v0.3.0.

---

## File Structure

### Existing files to modify

- `poker-trainer-ci/app/build.gradle` — release version, OpenCV and Android test dependencies.
- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/VisionActivityV025.java` — scanner/picker orchestration and UI only; recognition logic moves into focused classes.
- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/PokerTableState.java` — atomic slot batch application.
- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/CardRecognizer.java` — expose OCR hints for normalized index crops without deciding the card alone.
- `poker-trainer-ci/app/src/test/java/com/fantest/pokervision/PokerTableStateTest.java` — atomic batch regression tests.
- `poker-trainer-ci/app/src/test/java/com/fantest/pokervision/PokerMathTest.java` — retain 9-opponent regression.
- `.github/workflows/poker-vision-v0.2.0-build.yml` — v0.3.0 source/unit/build/signing/fixture pipeline.
- `poker-trainer-ci/tools/verify_final_source.py` — v0.3.0 source contract.

### New pure-Java files

- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/VisionConstants.java` — all fixed geometry/confidence/timing constants.
- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/CardPickerModel.java` — suit-first/value-second picker state.
- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/ScanModels.java` — immutable normalized geometry, observation, tracked detection, proposal/status models.
- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/TableZoneMapper.java` — zone classification/capacity/left-to-right assignment.
- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/RecognitionVoteTracker.java` — 4-of-5 tracking and expiry.
- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/ScanReviewState.java` — pending proposals, correction, duplicate validation and commit preparation.

### New OpenCV/runtime files

- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/CardDetector.java` — contour/quadrilateral discovery.
- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/CardNormalizer.java` — perspective warp/orientation/index crop.
- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/TemplateStore.java` — load/cache rank/suit template assets.
- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/SymbolPrediction.java` — immutable prediction/score value.
- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/RankRecognizer.java` — 13-class image/template rank recognition.
- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/SuitRecognizer.java` — 4-class image/template suit recognition.
- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/CardRecognitionFusion.java` — image + OCR confidence rules.
- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/VisionPipeline.java` — per-frame detector → normalizer → recognizers → vote/zone state orchestration.
- `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/ScannerOverlayView.java` — draw fixed zones, card boxes and status labels.

### New tests/assets/tools

- `poker-trainer-ci/app/src/test/java/com/fantest/pokervision/CardPickerModelTest.java`
- `poker-trainer-ci/app/src/test/java/com/fantest/pokervision/TableZoneMapperTest.java`
- `poker-trainer-ci/app/src/test/java/com/fantest/pokervision/RecognitionVoteTrackerTest.java`
- `poker-trainer-ci/app/src/test/java/com/fantest/pokervision/ScanReviewStateTest.java`
- `poker-trainer-ci/app/src/androidTest/java/com/fantest/pokervision/VisionFixtureTest.java`
- `poker-trainer-ci/app/src/main/assets/card_templates/` — generated rank/suit template PNGs plus attribution.
- `poker-trainer-ci/app/src/androidTest/assets/vision_fixtures/` — generated conventional-card fixtures for rotation/perspective/brightness tests.
- `poker-trainer-ci/tools/generate_card_templates.py` — deterministic repository-owned template/fixture generator.
- `poker-trainer-ci/app/src/main/assets/card_templates/ATTRIBUTION.md` — generation/font licensing note.

---

### Task 1: Lock v0.3.0 release/dependency contract

**Files:**
- Modify: `poker-trainer-ci/app/build.gradle`
- Modify: `poker-trainer-ci/tools/verify_final_source.py`
- Modify: `.github/workflows/poker-vision-v0.2.0-build.yml`

**Interfaces:**
- Consumes: existing stable QA signer configuration.
- Produces: build configuration exposing OpenCV 4.14.0 and Android test dependencies, with source-contract assertions for v0.3.0.

- [ ] **Step 1: Extend source-contract checks so the old build fails**

Add checks equivalent to:

```python
checks.update({
    'versionCode 14': 'versionCode 14' in gradle,
    'versionName 0.3.0': "versionName '0.3.0'" in gradle,
    'OpenCV 4.14.0': "implementation 'org.opencv:opencv:4.14.0'" in gradle,
    'vision pipeline class': Path('app/src/main/java/com/fantest/pokervision/VisionPipeline.java').exists(),
    'suit-first picker class': Path('app/src/main/java/com/fantest/pokervision/CardPickerModel.java').exists(),
})
```

- [ ] **Step 2: Run the contract against the current branch and verify RED**

Run from `poker-trainer-ci`:

```bash
python3 tools/verify_final_source.py
```

Expected: FAIL because v0.3.0 version/OpenCV/new classes are not present.

- [ ] **Step 3: Update Gradle release metadata/dependencies**

Set:

```gradle
versionCode 14
versionName '0.3.0'
implementation 'org.opencv:opencv:4.14.0'
androidTestImplementation 'androidx.test.ext:junit:1.2.1'
androidTestImplementation 'androidx.test:runner:1.6.2'
```

Preserve the existing QA signing block and `.qa` suffix exactly.

- [ ] **Step 4: Update workflow identity, triggers and artifact names**

Rename the job/workflow to v0.3.0, target the implementation branch used during execution, and reserve an instrumentation fixture-test job before the final artifact job. Do not remove certificate/package verification.

- [ ] **Step 5: Run existing JVM tests and a debug compile**

```bash
gradle --no-daemon testDebugUnitTest assembleDebug --stacktrace
```

Expected: PASS before adding new source classes except the intentionally failing source contract.

- [ ] **Step 6: Commit**

```bash
git add poker-trainer-ci/app/build.gradle poker-trainer-ci/tools/verify_final_source.py .github/workflows/poker-vision-v0.2.0-build.yml
git commit -m "build: prepare Poker Vision v0.3.0 vision stack"
```

---

### Task 2: Add centralized vision constants and suit-first picker model

**Files:**
- Create: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/VisionConstants.java`
- Create: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/CardPickerModel.java`
- Create: `poker-trainer-ci/app/src/test/java/com/fantest/pokervision/CardPickerModelTest.java`

**Interfaces:**
- Produces: `VisionConstants` static constants; `CardPickerModel(Set<PokerMath.Card> blockedCards, PokerMath.Card currentCard)`; `chooseSuit(int)`, `backToSuit()`, `isRankEnabled(int)`, `chooseRank(int)`.

- [ ] **Step 1: Write failing picker tests**

```java
@Test public void startsOnSuitStep() {
    CardPickerModel m = new CardPickerModel(Collections.emptySet(), null);
    assertEquals(CardPickerModel.STEP_SUIT, m.step());
}

@Test public void choosingSuitEnablesValueStep() {
    CardPickerModel m = new CardPickerModel(Collections.emptySet(), null);
    m.chooseSuit(1);
    assertEquals(CardPickerModel.STEP_RANK, m.step());
    assertEquals(1, m.selectedSuit());
}

@Test public void blockedExactCardDisablesOnlyThatRankForChosenSuit() {
    Set<PokerMath.Card> blocked = new HashSet<>();
    blocked.add(new PokerMath.Card(12, 1));
    CardPickerModel m = new CardPickerModel(blocked, null);
    m.chooseSuit(1);
    assertFalse(m.isRankEnabled(12));
    assertTrue(m.isRankEnabled(11));
}

@Test public void choosingRankReturnsCompletedCardImmediately() {
    CardPickerModel m = new CardPickerModel(Collections.emptySet(), null);
    m.chooseSuit(2);
    assertEquals(new PokerMath.Card(14, 2), m.chooseRank(14));
}
```

- [ ] **Step 2: Run test and verify RED**

```bash
gradle --no-daemon testDebugUnitTest --tests com.fantest.pokervision.CardPickerModelTest
```

Expected: compilation failure because `CardPickerModel` does not exist.

- [ ] **Step 3: Implement constants and picker model**

`VisionConstants` contains the exact spec values, including:

```java
static final int DISCOVERY_LONG_EDGE = 960;
static final long RECOGNITION_INTERVAL_MS = 250L;
static final int CARD_WIDTH = 350, CARD_HEIGHT = 500;
static final int INDEX_WIDTH = 105, INDEX_HEIGHT = 190;
static final double CARD_RATIO_MIN = 0.58, CARD_RATIO_MAX = 0.78;
static final double CARD_AREA_MIN = 0.015, CARD_AREA_MAX = 0.30;
static final double DUPLICATE_IOU = 0.70, TRACK_IOU = 0.35;
static final long TRACK_EXPIRY_MS = 1200L;
static final double HIGH_CONFIDENCE = 0.78, AMBER_CONFIDENCE = 0.55, OCR_ONLY_CAP = 0.49;
static final int STABLE_VOTES = 4, VOTE_WINDOW = 5;
```

`CardPickerModel.chooseRank()` throws `IllegalStateException` if no suit was selected and `IllegalArgumentException` if the exact card is blocked. The `currentCard` is removed from the effective blocked set so editing a slot can keep its existing card.

- [ ] **Step 4: Run picker tests GREEN**

```bash
gradle --no-daemon testDebugUnitTest --tests com.fantest.pokervision.CardPickerModelTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add poker-trainer-ci/app/src/main/java/com/fantest/pokervision/VisionConstants.java poker-trainer-ci/app/src/main/java/com/fantest/pokervision/CardPickerModel.java poker-trainer-ci/app/src/test/java/com/fantest/pokervision/CardPickerModelTest.java
git commit -m "feat: add suit-first card picker model"
```

---

### Task 3: Add atomic table batch updates and scan review state

**Files:**
- Modify: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/PokerTableState.java`
- Modify: `poker-trainer-ci/app/src/test/java/com/fantest/pokervision/PokerTableStateTest.java`
- Create: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/ScanModels.java`
- Create: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/ScanReviewState.java`
- Create: `poker-trainer-ci/app/src/test/java/com/fantest/pokervision/ScanReviewStateTest.java`

**Interfaces:**
- Produces: `PokerTableState.SlotUpdate(int area, int index, PokerMath.Card card)` and `boolean applyBatch(List<SlotUpdate>)`.
- Produces: `ScanReviewState.replaceProposals(List<ScanModels.Proposal>)`, `correct(int area,int index,Card)`, `validateAgainst(PokerTableState)`, `prepareCommit(PokerTableState)`.

- [ ] **Step 1: Write failing atomic batch tests**

```java
@Test public void invalidBatchDoesNotPartiallyMutateTable() {
    PokerTableState t = new PokerTableState();
    t.selectHole(0); assertTrue(t.setSelected(new PokerMath.Card(14, 0)));
    List<PokerTableState.SlotUpdate> updates = Arrays.asList(
        new PokerTableState.SlotUpdate(PokerTableState.AREA_BOARD, 0, new PokerMath.Card(13, 1)),
        new PokerTableState.SlotUpdate(PokerTableState.AREA_BOARD, 1, new PokerMath.Card(14, 0))
    );
    assertFalse(t.applyBatch(updates));
    assertNull(t.boardAt(0));
    assertNull(t.boardAt(1));
}
```

- [ ] **Step 2: Write failing review-state tests**

Cover confirmed-table duplicates, within-proposal duplicates, manual correction becoming confirmable, and `prepareCommit()` producing all-or-nothing `SlotUpdate`s.

- [ ] **Step 3: Run tests RED**

```bash
gradle --no-daemon testDebugUnitTest --tests com.fantest.pokervision.PokerTableStateTest --tests com.fantest.pokervision.ScanReviewStateTest
```

Expected: FAIL on missing batch/review APIs.

- [ ] **Step 4: Implement atomic copy-validate-commit in `PokerTableState`**

Implementation rule: copy `hole[]` and `board[]`, apply every update to copies while checking bounds/null/duplicates, and write copies back only after all updates validate. Do not change selected slot on failure.

- [ ] **Step 5: Implement scan proposal/status models and review state**

Use immutable proposal status values `NEUTRAL`, `AMBER`, `STABLE`, `INVALID`, `MANUAL`. A proposal contains area/index/card/confidence/quad/manual flag. `prepareCommit()` throws or returns an explicit invalid result when any proposal is unconfirmable; do not silently drop invalid proposals.

- [ ] **Step 6: Run tests GREEN**

```bash
gradle --no-daemon testDebugUnitTest --tests com.fantest.pokervision.PokerTableStateTest --tests com.fantest.pokervision.ScanReviewStateTest
```

- [ ] **Step 7: Commit**

```bash
git add poker-trainer-ci/app/src/main/java/com/fantest/pokervision/PokerTableState.java poker-trainer-ci/app/src/main/java/com/fantest/pokervision/ScanModels.java poker-trainer-ci/app/src/main/java/com/fantest/pokervision/ScanReviewState.java poker-trainer-ci/app/src/test/java/com/fantest/pokervision/PokerTableStateTest.java poker-trainer-ci/app/src/test/java/com/fantest/pokervision/ScanReviewStateTest.java
git commit -m "feat: add atomic scanner review state"
```

---

### Task 4: Implement deterministic zone mapping

**Files:**
- Create: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/TableZoneMapper.java`
- Create: `poker-trainer-ci/app/src/test/java/com/fantest/pokervision/TableZoneMapperTest.java`

**Interfaces:**
- Consumes: `List<ScanModels.TrackedDetection>`.
- Produces: `TableZoneMapper.Result` containing ordered `hole`, ordered `board`, `unassigned`, and `capacityError`.

- [ ] **Step 1: Write failing tests for exact zone boundaries/order/capacity**

Include:

```java
@Test public void boardCardsSortLeftToRight() { /* x=.70,.20,.45 -> .20,.45,.70 */ }
@Test public void holeZoneCapsAtTwo() { /* 3 centers in hole zone -> capacityError */ }
@Test public void outsideBothZonesIsUnassigned() { /* center .5,.53 */ }
```

Boundary policy: inclusive edges belong to the zone (`>= min && <= max`).

- [ ] **Step 2: Run RED**

```bash
gradle --no-daemon testDebugUnitTest --tests com.fantest.pokervision.TableZoneMapperTest
```

- [ ] **Step 3: Implement mapper using only `VisionConstants` rectangles**

Do not embed duplicate coordinate literals in the mapper. Map center points, sort by `centerX`, cap hole at 2 and board at 5, and set capacity error instead of truncating/guessing.

- [ ] **Step 4: Run GREEN and commit**

```bash
gradle --no-daemon testDebugUnitTest --tests com.fantest.pokervision.TableZoneMapperTest
git add poker-trainer-ci/app/src/main/java/com/fantest/pokervision/TableZoneMapper.java poker-trainer-ci/app/src/test/java/com/fantest/pokervision/TableZoneMapperTest.java
git commit -m "feat: map detected cards into table zones"
```

---

### Task 5: Implement multi-frame recognition voting

**Files:**
- Create: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/RecognitionVoteTracker.java`
- Create: `poker-trainer-ci/app/src/test/java/com/fantest/pokervision/RecognitionVoteTrackerTest.java`

**Interfaces:**
- Consumes: timestamped `ScanModels.Observation` values with normalized quad/card/confidence.
- Produces: `List<ScanModels.TrackedDetection> update(long nowMs, List<Observation> observations)`.

- [ ] **Step 1: Write RED tests for 4-of-5, bad-frame resistance, track association and expiry**

Required cases:

```java
@Test public void fourOfFiveHighConfidenceVotesBecomeStable() { }
@Test public void oneConflictingFrameDoesNotReplaceStableCard() { }
@Test public void newIdentityNeedsFreshFourOfFiveBeforeReplacingStableCard() { }
@Test public void unmatchedTrackExpiresAfter1200ms() { }
```

Use normalized quads with controlled IoU to test association; add a center-distance association case below IoU threshold.

- [ ] **Step 2: Run RED**

```bash
gradle --no-daemon testDebugUnitTest --tests com.fantest.pokervision.RecognitionVoteTrackerTest
```

- [ ] **Step 3: Implement bounded per-track history**

Keep at most 5 usable identity observations per track. High confidence plus 4/5 creates `STABLE`; `0.55..0.7799` is `AMBER`; lower is `NEUTRAL`. Do not count null/unrecognized observations as identity votes, but they may refresh geometric tracking if the rectangle persists.

- [ ] **Step 4: Run GREEN and commit**

```bash
gradle --no-daemon testDebugUnitTest --tests com.fantest.pokervision.RecognitionVoteTrackerTest
git add poker-trainer-ci/app/src/main/java/com/fantest/pokervision/RecognitionVoteTracker.java poker-trainer-ci/app/src/test/java/com/fantest/pokervision/RecognitionVoteTrackerTest.java
git commit -m "feat: stabilize card recognition across frames"
```

---

### Task 6: Generate owned templates/fixtures and implement OpenCV geometry

**Files:**
- Create: `poker-trainer-ci/tools/generate_card_templates.py`
- Create: `poker-trainer-ci/app/src/main/assets/card_templates/ATTRIBUTION.md`
- Create: generated PNGs under `poker-trainer-ci/app/src/main/assets/card_templates/`
- Create: generated PNG fixtures under `poker-trainer-ci/app/src/androidTest/assets/vision_fixtures/`
- Create: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/CardDetector.java`
- Create: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/CardNormalizer.java`
- Create: `poker-trainer-ci/app/src/androidTest/java/com/fantest/pokervision/VisionFixtureTest.java`

**Interfaces:**
- `CardDetector.detect(Mat frame)` -> `List<ScanModels.PixelQuad>`.
- `CardNormalizer.normalize(Mat frame, PixelQuad quad)` -> `CardNormalizer.Result(card350x500, index105x190, sourceQuad)`.

- [ ] **Step 1: Add the deterministic template/fixture generator**

The generator creates two rank families (serif/sans), four suit shapes, full synthetic cards, and transformed fixtures at 0°, ±12° rotation, perspective skew, and 70%/130% brightness. Use an open-licensed font available in the development environment; commit only generated PNGs plus attribution/license provenance, not font binaries.

- [ ] **Step 2: Write failing Android fixture tests for rectangle discovery/normalization**

Tests load known fixture PNGs via instrumentation assets and assert:

```java
assertEquals(expectedCardCount, detector.detect(frame).size());
assertEquals(350, normalized.card.cols());
assertEquals(500, normalized.card.rows());
assertEquals(105, normalized.index.cols());
assertEquals(190, normalized.index.rows());
```

Also verify 0.58..0.78 ratio and 1.5%..30% area filtering reject false rectangles.

- [ ] **Step 3: Run instrumentation RED on emulator**

```bash
gradle --no-daemon assembleDebug assembleDebugAndroidTest
# emulator already booted by CI/local harness
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.fantest.pokervision.qa.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: FAIL because detector/normalizer do not exist.

- [ ] **Step 4: Implement `CardDetector`**

Pipeline: resize long edge <=960 → grayscale → blur/CLAHE or equalization → adaptive threshold/Canny → external contours → `approxPolyDP` → convex four-corner check → area/ratio filtering → sort corners consistently → IoU duplicate suppression at 0.70. Return coordinates scaled to original analysis-frame pixels.

- [ ] **Step 5: Implement `CardNormalizer`**

Warp candidate to 350x500 portrait. Evaluate top-left index and the bottom-right equivalent after 180° rotation; choose the orientation with the larger foreground/index signal. Return exactly 105x190 index crop.

- [ ] **Step 6: Run fixture geometry tests GREEN**

Use the same instrumentation command as Step 3. Expected: PASS for generated geometry fixtures.

- [ ] **Step 7: Commit**

```bash
git add poker-trainer-ci/tools/generate_card_templates.py poker-trainer-ci/app/src/main/assets/card_templates poker-trainer-ci/app/src/androidTest/assets/vision_fixtures poker-trainer-ci/app/src/main/java/com/fantest/pokervision/CardDetector.java poker-trainer-ci/app/src/main/java/com/fantest/pokervision/CardNormalizer.java poker-trainer-ci/app/src/androidTest/java/com/fantest/pokervision/VisionFixtureTest.java
git commit -m "feat: detect and normalize physical cards with OpenCV"
```

---

### Task 7: Implement rank/suit recognition and OCR fusion

**Files:**
- Create: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/TemplateStore.java`
- Create: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/SymbolPrediction.java`
- Create: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/RankRecognizer.java`
- Create: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/SuitRecognizer.java`
- Create: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/CardRecognitionFusion.java`
- Modify: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/CardRecognizer.java`
- Modify: `poker-trainer-ci/app/src/androidTest/java/com/fantest/pokervision/VisionFixtureTest.java`

**Interfaces:**
- `SymbolPrediction<T>` -> `value`, `imageScore`.
- `RankRecognizer.recognize(Mat indexCrop)` -> `SymbolPrediction<Integer>`.
- `SuitRecognizer.recognize(Mat indexCrop)` -> `SymbolPrediction<Integer>`.
- `CardRecognizer.extractHints(Text)` -> rank/suit hint values, not final card confidence.
- `CardRecognitionFusion.fuse(rankPrediction, suitPrediction, ocrHints)` -> `ScanModels.CardIdentity` with exact card/confidence.

- [ ] **Step 1: Extend fixture tests RED for all 13 ranks and 4 suits**

For each committed canonical fixture assert the expected rank and suit, and include transformed brightness/rotation/perspective samples. Add explicit fusion tests:

```java
assertEquals(0.49, fusion.ocrOnlyConfidence(), 0.0001);
assertEquals(0.90, fusion.adjust(0.80, true, false), 0.0001);
assertEquals(0.65, fusion.adjust(0.80, false, true), 0.0001);
```

- [ ] **Step 2: Run instrumentation RED**

Expected: missing recognizer/fusion classes.

- [ ] **Step 3: Implement `TemplateStore`**

Load PNG templates once from assets into binary normalized Mats. Cache per process and release Mats only when application process ends; do not reload every frame.

- [ ] **Step 4: Implement rank/suit image scoring**

Threshold the index crop, isolate connected components for rank and suit regions, normalize each component to template dimensions, and compute a weighted score from normalized cross-correlation/template similarity plus contour shape similarity. Return the highest-scoring class; do not apply confidence thresholds inside the recognizer.

- [ ] **Step 5: Refactor OCR into hints and implement fusion**

Image score is the base. OCR agreement `+0.10` capped at 1.00; explicit disagreement `-0.15` floored at 0.00. If the image side is absent, cap the OCR-only side at 0.49. Overall card confidence is `min(rankConfidence, suitConfidence)`.

- [ ] **Step 6: Run all vision fixture tests GREEN**

Expected: all canonical generated ranks/suits recognized under the committed transform set.

- [ ] **Step 7: Commit**

```bash
git add poker-trainer-ci/app/src/main/java/com/fantest/pokervision/TemplateStore.java poker-trainer-ci/app/src/main/java/com/fantest/pokervision/SymbolPrediction.java poker-trainer-ci/app/src/main/java/com/fantest/pokervision/RankRecognizer.java poker-trainer-ci/app/src/main/java/com/fantest/pokervision/SuitRecognizer.java poker-trainer-ci/app/src/main/java/com/fantest/pokervision/CardRecognitionFusion.java poker-trainer-ci/app/src/main/java/com/fantest/pokervision/CardRecognizer.java poker-trainer-ci/app/src/androidTest/java/com/fantest/pokervision/VisionFixtureTest.java
git commit -m "feat: recognize card ranks and suits from normalized corners"
```

---

### Task 8: Build the reusable suit-first picker UI

**Files:**
- Modify: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/VisionActivityV025.java`

**Interfaces:**
- Consumes: `CardPickerModel`.
- Produces: one picker method accepting `currentCard`, `blockedCards`, `onChosen(Card)`, `onRemove` callbacks, reused by dashboard and scanner correction.

- [ ] **Step 1: Replace rank-first dialog with two-step UI behind one helper**

Use method shape:

```java
private void showCardPicker(
        PokerMath.Card currentCard,
        java.util.Set<PokerMath.Card> blocked,
        java.util.function.Consumer<PokerMath.Card> onChosen,
        Runnable onRemove) { ... }
```

Suit step displays four large suit buttons. Rank step displays `A K Q J 10 9 8 7 6 5 4 3 2`; blocked exact cards are disabled/gray. Add a compact Back-to-suits control and Remove only when `currentCard != null`.

- [ ] **Step 2: Wire dashboard slot taps and Manual button**

Slot tap uses table cards as blocked set excluding the current slot. Valid rank selection invokes `placeSelected(card, true)` and dismisses immediately—no Add/Done button.

- [ ] **Step 3: Verify manually by source/UI contract and compile**

Add source-contract tokens for suit-first copy and absence of old `Choose rank, then suit.` copy. Run:

```bash
gradle --no-daemon testDebugUnitTest assembleDebug --stacktrace
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add poker-trainer-ci/app/src/main/java/com/fantest/pokervision/VisionActivityV025.java poker-trainer-ci/tools/verify_final_source.py
git commit -m "feat: simplify manual card entry to suit then value"
```

---

### Task 9: Integrate the vision pipeline and scanner review UI

**Files:**
- Create: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/VisionPipeline.java`
- Create: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/ScannerOverlayView.java`
- Modify: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/VisionActivityV025.java`

**Interfaces:**
- `VisionPipeline.process(ImageProxy, long nowMs, Callback)` publishes immutable tracked detections and zone mapping at <=4 Hz.
- `ScannerOverlayView.setDetections(List<TrackedDetection>, TableZoneMapper.Result)` redraws boxes/zones only.
- Activity owns `ScanReviewState`, Confirm All/Rescan/Correct actions, CameraX lifecycle, and localization.

- [ ] **Step 1: Create `VisionPipeline` with single-frame backpressure**

Convert the CameraX frame into OpenCV Mat, call detector/normalizer/recognizers, issue cropped ML Kit OCR requests as secondary hints, fuse results, update the vote tracker and zone mapper, then callback to UI. Ensure every `ImageProxy` closes exactly once on success/failure.

- [ ] **Step 2: Add overlay view with exact zone geometry/status colors**

Draw BOARD and YOUR CARDS guides plus candidate quadrilaterals. Status colors: green stable, amber medium, neutral gray, red invalid. Draw rank/suit label and rounded integer confidence where identity exists.

- [ ] **Step 3: Replace current horizontal OCR-candidate scanner UI with review state**

The bottom review panel must show:

- proposed `Your Cards` list;
- proposed `Board` list;
- `Confirm All`;
- `Rescan`;
- correction by tapping a proposed card;
- Manual fallback.

No scan proposal mutates `PokerTableState` before Confirm All.

- [ ] **Step 4: Wire correction through the shared picker**

Blocked set = all confirmed table cards not targeted by this proposal + all other pending proposal cards. Corrected proposal status becomes `MANUAL`; correction stays pending until Confirm All.

- [ ] **Step 5: Wire atomic confirmation**

`Confirm All` calls `review.prepareCommit(table)` then `table.applyBatch(updates)`. On success, clear review, refresh cards, select first empty slot if any, close scanner and stop camera. On failure, keep scanner/review visible and mark conflicts red.

- [ ] **Step 6: Implement Rescan/Back semantics**

Rescan clears `RecognitionVoteTracker` and `ScanReviewState` only. Back discards pending review, unbinds CameraX, hides scanner, and clears overlay.

- [ ] **Step 7: Compile and run JVM + fixture tests**

```bash
gradle --no-daemon testDebugUnitTest assembleDebug assembleDebugAndroidTest --stacktrace
```

Then emulator instrumentation command from Task 6. Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add poker-trainer-ci/app/src/main/java/com/fantest/pokervision/VisionPipeline.java poker-trainer-ci/app/src/main/java/com/fantest/pokervision/ScannerOverlayView.java poker-trainer-ci/app/src/main/java/com/fantest/pokervision/VisionActivityV025.java
git commit -m "feat: add guided multi-card scan review flow"
```

---

### Task 10: Lifecycle, localization and scanner failure handling

**Files:**
- Modify: `poker-trainer-ci/app/src/main/java/com/fantest/pokervision/VisionActivityV025.java`
- Modify: `poker-trainer-ci/tools/verify_final_source.py`

**Interfaces:**
- Produces: deterministic camera stop on close/`onStop`, bilingual scanner/picker copy, manual fallback for camera/OpenCV failures.

- [ ] **Step 1: Add lifecycle regression behavior**

Override `onStop()` to stop/unbind camera when scanner is active; `onStart()` must not auto-open scanner. `onDestroy()` closes recognizer/executor resources.

- [ ] **Step 2: Add explicit user states in English/Arabic**

Include localized equivalents for:

- `Place cards inside the guides`
- `Too many cards in this area`
- `Scanner initialization error`
- `Tap a card to correct it`
- `Confirm all`
- `Rescan`
- `Choose suit`
- `Choose value`

Keep rank/suit symbols LTR inside Arabic UI.

- [ ] **Step 3: Add OpenCV/camera fallback**

If OpenCV initialization or pipeline construction fails, disable analysis for that scanner session, keep live/manual controls usable when possible, and show Manual entry instead of crashing/black-screening.

- [ ] **Step 4: Run full local verification**

```bash
python3 tools/verify_final_source.py
gradle --no-daemon testDebugUnitTest assembleDebug bundleRelease assembleDebugAndroidTest --stacktrace
```

Expected: all commands PASS.

- [ ] **Step 5: Commit**

```bash
git add poker-trainer-ci/app/src/main/java/com/fantest/pokervision/VisionActivityV025.java poker-trainer-ci/tools/verify_final_source.py
git commit -m "fix: harden scanner lifecycle and bilingual fallback states"
```

---

### Task 11: CI fixture gate, signing verification and release candidate artifact

**Files:**
- Modify: `.github/workflows/poker-vision-v0.2.0-build.yml`
- Modify: `poker-trainer-ci/tools/verify_final_source.py`
- Create: `poker-trainer-ci/docs/v0.3.0-device-acceptance.md`

**Interfaces:**
- Produces: CI artifact `Poker-Vision-v0.3.0-multicard-scanner` containing stable-signed QA APK, unsigned production AAB, and SHA256SUMS.

- [ ] **Step 1: Add dedicated JVM gate**

Run `python3 tools/verify_final_source.py` and `gradle --no-daemon testDebugUnitTest` before Android packaging.

- [ ] **Step 2: Add Android emulator vision-fixture gate**

Use `reactivecircus/android-emulator-runner@v2` with API 35, x86_64, hardware acceleration when available, and execute:

```bash
gradle --no-daemon connectedDebugAndroidTest --stacktrace
```

The final packaging job depends on this fixture gate.

- [ ] **Step 3: Preserve stable QA signing/package verification**

Build `assembleDebug` and verify:

```bash
apkanalyzer manifest application-id app/build/outputs/apk/debug/app-debug.apk
# expected com.fantest.pokervision.qa
apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk
```

Compare certificate SHA-256 with the previously pinned stable QA certificate in the workflow. Do not accept an arbitrary newly generated debug key.

- [ ] **Step 4: Build release AAB and validate archives**

```bash
gradle --no-daemon bundleRelease
unzip -t app/build/outputs/apk/debug/app-debug.apk
unzip -t app/build/outputs/bundle/release/app-release.aab
sha256sum app/build/outputs/apk/debug/app-debug.apk app/build/outputs/bundle/release/app-release.aab > SHA256SUMS.txt
```

Rename copied artifacts to:

- `Poker-Vision-v0.3.0-QA.apk`
- `Poker-Vision-v0.3.0-unsigned.aab`
- `SHA256SUMS.txt`

- [ ] **Step 5: Add physical-device acceptance checklist document**

The checklist must require a conventional indexed deck and verify: camera opens; 2 hole + 3 board visible together; separate rectangles; stable detections; correction; Confirm All; duplicate rejection; Rescan; camera stop; 10-player calculation.

State explicitly: CI fixture success is not proof of real-deck recognition; the physical-device gate must be recorded before calling scanner recognition production-final.

- [ ] **Step 6: Run the GitHub Actions workflow and inspect every step**

Require successful source contract, JVM tests, emulator fixture tests, APK/AAB build, stable signer/package checks, archive validation and artifact upload.

- [ ] **Step 7: Download the CI artifact and independently verify it**

After downloading the artifact ZIP:

```bash
unzip -t Poker-Vision-v0.3.0-multicard-scanner.zip
sha256sum -c SHA256SUMS.txt
```

Also run `apksigner verify --print-certs Poker-Vision-v0.3.0-QA.apk` on the downloaded APK.

- [ ] **Step 8: Commit CI/checklist changes**

```bash
git add .github/workflows/poker-vision-v0.2.0-build.yml poker-trainer-ci/tools/verify_final_source.py poker-trainer-ci/docs/v0.3.0-device-acceptance.md
git commit -m "ci: gate Poker Vision v0.3.0 scanner release"
```

---

### Task 12: Regression review and release handoff

**Files:**
- Review all changed files.
- Update only if verification exposes a defect.

**Interfaces:**
- Produces: review-ready PR and downloadable QA release candidate.

- [ ] **Step 1: Run the complete regression suite from a clean checkout**

```bash
python3 poker-trainer-ci/tools/verify_final_source.py
cd poker-trainer-ci
gradle --no-daemon testDebugUnitTest assembleDebug bundleRelease assembleDebugAndroidTest --stacktrace
```

Then run the instrumentation fixture suite on the emulator.

- [ ] **Step 2: Confirm legacy requirements explicitly**

Verify: tap card opens picker; used cards are unavailable; duplicate protection remains; camera is off outside scanner; bilingual switch works; equity runs for 1-9 opponents; QA package is `.qa`; QA signer fingerprint matches stable lineage.

- [ ] **Step 3: Review the diff for architecture leakage**

Reject the implementation if `VisionActivityV025.java` contains contour/template/voting algorithms instead of orchestration, or if OpenCV types leak into `TableZoneMapper`, `RecognitionVoteTracker`, `ScanReviewState`, or picker tests.

- [ ] **Step 4: Open PR against `poker-vision-v0.2.11-final` or the accepted successor base**

PR title:

`Poker Vision v0.3.0 — guided multi-card scanner`

PR body must summarize scanner architecture, suit-first picker, fixture/device gates, package/signing behavior, and explicit physical-device acceptance status.

- [ ] **Step 5: Handoff the QA APK for real-deck device acceptance**

Provide the stable-signed `Poker-Vision-v0.3.0-QA.apk` and SHA-256. Do not label real-world scan recognition "fixed" until the physical-device checklist passes on an Android camera device with a conventional indexed deck.
