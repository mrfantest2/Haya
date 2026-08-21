# Poker Vision v0.3.0 Multi-Card Scanner Design

## Status

Approved design for implementation planning.

## Goal

Replace Poker Vision's OCR-only card scanner with a guided multi-card computer-vision scanner that can see the user's two hole cards and up to five board cards in one camera view, while also replacing the current manual rank-first dialog with a faster suit-first, value-second picker shared by dashboard entry and scanner correction.

## Release

- Version name: `0.3.0`
- Version code: `14`
- QA package: `com.fantest.pokervision.qa`
- Production package remains: `com.fantest.pokervision`
- QA builds continue to use the existing stable QA signer.
- Production/Play builds must not use the public QA signer.
- Minimum Android SDK remains 24.
- Target/compile SDK remains 36.
- Java remains 17.
- Existing support for 1-10 total players remains unchanged: hero plus 1-9 opponents.

## Current Problem

The camera opens and previews correctly, but physical cards are rarely recognized. The current pipeline sends the whole frame through ML Kit Text Recognition and accepts a card only when OCR text can be parsed as a rank/suit sequence such as `AS`, `10H`, `Q♣`, or the reverse order. Real cards use stylized rank fonts and suit glyphs, and OCR often returns no useful rank/suit string.

The defect is therefore primarily recognition architecture rather than camera startup or permission handling.

## Product Experience

### Guided one-view table scan

The scanner displays two visible zones over the live CameraX preview:

1. **BOARD** zone in the upper portion of the scan area, with five conceptual positions.
2. **YOUR CARDS** zone in the lower portion, with two conceptual positions.

Users can place both hole cards and the currently visible community cards in the same camera view. The board can contain 0, 3, 4, or 5 cards. The scanner must also tolerate partial/manual workflows with 1-5 board cards because users may be correcting or reconstructing a hand.

Cards within each zone are assigned left-to-right after geometric normalization. A detected card is never silently committed to the poker table. Stable detections first appear in a review state.

### Detection presentation

Every physical card candidate receives an overlay box and a recognition state:

- **Green:** stable high-confidence card.
- **Amber:** plausible card requiring user review/correction.
- **Neutral/gray:** rectangle detected but rank/suit is not reliable yet.
- **Red:** duplicate or structurally invalid assignment.

The review panel shows the proposed table state, for example:

`Your Cards: A♠ K♠`

`Board: Q♠ J♥ 7♦`

Actions:

- **Confirm All** commits all valid reviewed cards to the table and returns to the dashboard.
- **Correct** is performed by tapping any detected card and opening the shared suit-first picker for that assignment.
- **Rescan** clears only the pending scan review state and keeps the camera open.
- **Back** closes the scanner, discards unconfirmed scan state, and stops the camera immediately.

### Incomplete and existing table state

The scanner targets the currently selected table slots but operates as a table-level scan. Existing confirmed cards remain visible as occupied slots and are protected by duplicate validation.

When a scan contains fewer than seven cards, only detected positions are proposed. Existing confirmed slots not represented by the scan are not automatically erased.

If a proposed card is already used in another confirmed slot or another proposal, the proposal is marked invalid and cannot be confirmed until corrected or rescanned.

## Manual Card Picker

The current rank-first/suit-second/Add flow is replaced by a two-step picker.

### Step 1: suit

Display four large options:

- `♠ Spades`
- `♥ Hearts`
- `♦ Diamonds`
- `♣ Clubs`

Hearts and diamonds use the existing red treatment; spades and clubs use the existing dark treatment.

### Step 2: value

After selecting a suit, display:

`A  K  Q  J  10  9  8  7  6  5  4  3  2`

The current slot's card, when editing, remains selectable. Values whose exact suit/value combination is used elsewhere are gray and disabled.

Selecting a value immediately saves/replaces the card. There is no additional Add/Save/Done confirmation for a valid choice. Dashboard entry then advances to the next empty slot using the existing table-state behavior.

A compact back action returns from value selection to suit selection without closing the picker. Remove remains available when editing an occupied slot.

### Shared correction component

The same picker implementation is used for:

- tapping a hole-card slot;
- tapping a board-card slot;
- the Manual button;
- correcting one pending scanner detection.

Scanner correction changes only pending review state until **Confirm All** is pressed.

## Computer-Vision Architecture

### Dependency

Use the official OpenCV Android Maven AAR:

`org.opencv:opencv:4.14.0`

OpenCV is used for geometry, contours, perspective transforms, thresholding, and template/image comparison. Existing CameraX and ML Kit Text Recognition dependencies remain in place.

### Pipeline

For each eligible CameraX analysis frame:

1. Convert the luminance/RGB camera frame into an OpenCV matrix without blocking the UI thread.
2. Downscale the frame for card-rectangle discovery.
3. Apply grayscale normalization and adaptive/edge thresholding.
4. Find external contours.
5. Approximate quadrilaterals and reject candidates outside playing-card geometry/area constraints.
6. Map candidate coordinates back to preview coordinates.
7. Perspective-warp each card to a normalized portrait card image.
8. Correct orientation so the most likely indexed corner is presented consistently.
9. Crop the rank/suit index region from the normalized card.
10. Recognize rank and suit independently.
11. Combine image recognition with the existing OCR parser as a secondary hint, not as the primary decision source.
12. Feed observations into multi-frame voting.
13. Map stable detections into board/hole zones and left-to-right slot order.
14. Publish immutable pending-review state to the UI.

### Card rectangle constraints

v0.3.0 is optimized for standard poker-size playing cards and similarly proportioned decks. A contour candidate must:

- be a convex four-corner polygon after approximation;
- have a normalized width/height ratio compatible with a portrait or landscape playing card after rotation;
- exceed the configured minimum visible-frame area;
- remain mostly inside one guided zone;
- not be a near-duplicate contour of another candidate.

Cards must be visually separated. Significant overlapping/stacked cards are explicitly out of scope for v0.3.0.

### Rank recognition

Rank classes are:

`A, K, Q, J, 10, 9, 8, 7, 6, 5, 4, 3, 2`

The normalized rank crop is converted to high-contrast binary representations and compared against bundled rank templates using scale-normalized image similarity/contour features. Existing ML Kit OCR contributes a rank hint when it returns a parsable value.

A rank result records:

- predicted rank;
- image/template score;
- optional OCR agreement;
- combined confidence.

### Suit recognition

Suit classes are:

`♠, ♥, ♦, ♣`

Suit recognition uses the normalized suit crop, contour topology/shape descriptors, and bundled suit templates. OCR is only a secondary hint when a suit letter or Unicode suit glyph is returned.

A suit result records:

- predicted suit;
- shape/template score;
- optional OCR agreement;
- combined confidence.

### Template scope

v0.3.0 targets conventional high-contrast poker decks where the rank and suit are printed in an indexed corner. Bundled templates cover standard serif/sans rank forms and the four conventional suit shapes. Highly decorative decks, novelty fonts, borderless art decks, cards without indexed corners, and heavily occluded cards are not guaranteed; manual correction is the designed fallback.

The implementation must keep template assets isolated so additional deck/template packs can be added later without changing table or camera architecture.

## Multi-Frame Voting

Single-frame classifications do not become stable detections.

Each physical candidate is tracked by geometric proximity/overlap across recent frames. A card becomes **stable** only when the same rank+suit result appears in at least 4 of the latest 5 usable observations for that tracked card and the combined confidence is at or above the high-confidence threshold.

Medium-confidence detections may appear in amber review state but are never auto-confirmed.

A single conflicting frame must not replace an already stable result. A stable result can change only after a new candidate wins the same 4-of-5 criterion.

Tracking state expires after the candidate is absent for a short bounded window so removed cards do not persist indefinitely.

## Zone Mapping

`TableZoneMapper` receives normalized preview-space card centers and the scanner's two zone rectangles.

Rules:

- Card center in the upper BOARD zone -> board candidate.
- Card center in the lower YOUR CARDS zone -> hole-card candidate.
- Card center outside both zones -> unassigned and not confirmable.
- Hole candidates are sorted left-to-right and capped at 2.
- Board candidates are sorted left-to-right and capped at 5.
- More than the zone capacity is treated as a scan-layout error; extra cards are not guessed into slots.

The mapper is deterministic and independently unit-testable.

## Pending Review State

Introduce a scan-review model separate from `PokerTableState`.

Each proposed slot records:

- target area and index;
- recognized card when available;
- confidence;
- recognition status;
- source bounding quadrilateral;
- whether the user manually corrected it.

`Confirm All` validates the complete proposal against existing table cards and within-proposal duplicates, then commits valid proposals atomically. If validation fails, no partial commit occurs.

## Component Boundaries

### `CardDetector`

Input: OpenCV frame matrix.

Output: geometrically valid card quadrilaterals.

Responsibility: rectangle discovery only; no rank/suit logic.

### `CardNormalizer`

Input: frame matrix plus card quadrilateral.

Output: normalized, orientation-corrected card image plus indexed-corner crop metadata.

Responsibility: perspective/orientation only.

### `RankRecognizer`

Input: normalized index crop.

Output: rank prediction and confidence.

### `SuitRecognizer`

Input: normalized index crop.

Output: suit prediction and confidence.

### `CardRecognitionFusion`

Input: rank result, suit result, optional existing ML Kit OCR hints.

Output: one card observation and combined confidence.

Responsibility: combine signals; OCR cannot produce a high-confidence result by itself.

### `RecognitionVoteTracker`

Input: timestamped geometric candidates and card observations.

Output: tracked detections with unstable/amber/stable state.

### `TableZoneMapper`

Input: tracked detections plus zone geometry.

Output: deterministic hole/board proposals.

### `ScanReviewState`

Responsibility: pending proposals, correction, rescan, duplicate validation, and atomic commit preparation.

### `CardPicker`

Responsibility: reusable two-step suit -> value selection behavior for table entry and scan correction.

The implementation may remain programmatic Android Views to match the existing application; no Compose migration is part of v0.3.0.

## Camera Lifecycle and Performance

- Camera starts only while the scanner overlay/activity state is visible and permission is granted.
- Camera unbinds immediately on Back/close and when the activity stops.
- Analysis remains off the main thread.
- Only one frame can be in the expensive recognition stage at a time.
- Rectangle discovery runs on a reduced-resolution image.
- Detailed normalization/recognition runs only on detected card ROIs.
- Use CameraX `STRATEGY_KEEP_ONLY_LATEST` to prevent frame backlog.
- UI receives throttled immutable recognition-state updates rather than per-pixel/frame work.
- Existing 25,000-simulation poker calculation remains outside the camera pipeline.

## Error Handling

### Camera unavailable

Show a clear camera error and expose Manual entry without crashing or leaving a black overlay.

### Permission denied

Keep the existing permission request behavior. If permission remains denied, scanner state must explain that camera permission is required and offer Manual entry.

### No cards detected

Show `Place cards inside the guides` rather than reporting an OCR failure.

### Rectangle found, recognition uncertain

Show an amber/neutral candidate and allow tap-to-correct.

### Duplicate

Mark conflicting proposals red and disable Confirm All until the conflict is corrected or rescanned.

### Excess cards in a zone

Show `Too many cards in this area` and do not guess assignments.

### OpenCV initialization failure

Disable vision analysis for the scanner session, show a scanner initialization error, and keep Manual entry available.

## Localization

All new user-visible scanner and picker text must have English and Arabic variants using the application's existing runtime language switch behavior.

Arabic scanner zones, statuses, correction actions, and picker instructions must preserve card symbols/ranks in left-to-right visual order while surrounding explanatory text can use RTL direction.

## Testing

### Unit tests

Add deterministic JVM tests where Android/OpenCV runtime is not required for:

- suit-first picker state transitions;
- exact-card availability after selecting a suit;
- immediate value selection result;
- next-empty-slot advancement through existing table state;
- zone classification;
- left-to-right ordering;
- hole zone capacity of 2;
- board zone capacity of 5;
- duplicate proposal detection;
- atomic review commit preparation;
- 4-of-5 recognition voting;
- one bad frame not replacing a stable detection;
- tracking expiry behavior;
- existing 1-9 opponent math coverage.

### Vision fixture tests

Store a small, repository-owned set of synthetic/generated test fixtures representing conventional cards under rotation, scale, perspective, and moderate brightness differences. Fixture tests must validate card rectangle geometry and rank/suit recognition for the supported template family.

Do not use copyrighted third-party card artwork as committed fixtures unless its license explicitly permits repository redistribution.

### Android build validation

GitHub Actions must:

1. run source-contract checks;
2. run JVM unit tests;
3. compile the OpenCV-integrated Android application;
4. build the stable-signed QA APK;
5. build the unsigned production AAB unless a private production signer is supplied by the release workflow;
6. verify QA package ID `com.fantest.pokervision.qa`;
7. verify the QA APK certificate matches the existing stable QA certificate;
8. validate APK/AAB ZIP integrity;
9. upload the v0.3.0 artifacts.

### Device acceptance test

Before calling scanning fixed, test on an Android camera device with a standard indexed poker deck:

- camera opens;
- two hole cards plus three board cards can be visible together;
- separate card rectangles appear;
- stable cards reach review state;
- wrong/uncertain card can be corrected with suit -> value picker;
- Confirm All populates intended table slots;
- duplicate proposal cannot be confirmed;
- Rescan clears pending proposals only;
- closing scanner stops camera;
- dashboard calculation still works through 10 total players.

CI/build success alone is not evidence that real-world visual recognition works; the device acceptance test is a separate release gate.

## Success Criteria

v0.3.0 is acceptable when all of the following are true:

- The current B-type failure mode, where the camera opens but never produces useful card candidates on a conventional indexed poker deck, is replaced by visible multi-card rectangle detection and rank/suit proposals.
- Two hole cards and up to five board cards can be scanned in one guided view when cards are separated and placed in their respective zones.
- Stable recognition requires multi-frame consensus.
- Users review detected cards before table commit.
- Any individual detection can be corrected without leaving the scanner.
- Manual card entry is suit first, then value, with immediate save and automatic next-slot advance.
- Already-used exact cards are unavailable in both manual entry and correction.
- Existing poker equity behavior, bilingual support, camera lifecycle rules, and 10-total-player support continue to work.
- QA APK remains upgrade-compatible with the stable QA signing lineage.

## Explicit Non-Goals for v0.3.0

- Recognizing stacked or heavily overlapping cards.
- Reading face-down cards.
- Identifying poker chips, bet sizes, player positions, or dealer button.
- Inferring opponent hole cards.
- Cloud/server recognition.
- Training a large neural model inside the release pipeline.
- Supporting every novelty/decorative deck style.
- Migrating the application to Jetpack Compose.
- Adding advertisements or monetization as part of this scanner release.

## Future Extension Path

The component boundaries intentionally allow `RankRecognizer` and `SuitRecognizer` to be replaced later by TensorFlow Lite/ONNX-style classifiers trained on broader deck datasets without changing CameraX lifecycle, card detection, zone mapping, review state, or the manual picker.