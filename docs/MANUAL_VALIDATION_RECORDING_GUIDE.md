# Manual validation recording guide (Phase 3B)

This is exactly how to record and import the first batch of real test clips into the Manual
Validation Harness (**Settings → Developer Tools → Open Validation Harness →**). This is a
local/debug tool — nothing you record or import here ever becomes official club-camera data (see
`com.example.climb.validation`'s trust-boundary doc comments if you want the enforced guarantee,
not just this doc's word for it).

## 1. Set up one fixed phone position

- Mount or prop up **one phone** so it doesn't move for the entire session. A tripod, clamp, or
  even a stack of books against a wall works — the only requirement is it truly does not move,
  tilt, or get bumped between the reference photo and every clip you record after it.
- Use the phone's **back camera** — the harness assumes this and doesn't check it (there's no way
  to check it from a video file alone).
- Pick a **zoom level and orientation** (landscape recommended, matching this app's own personal
  recording convention) and don't change it for the whole session.
- Frame the shot so **2-3 routes** are visible in the same view, with enough of the wall around
  them to see holds clearly.

## 2. Capture the reference photo — do this first, before any climbing

- With the wall **completely clear** (no climber, no chalk bag left on a hold, nothing blocking
  any hold), take **one photo** from the fixed position.
- Do not change the phone's position, zoom, or orientation between this photo and any video you
  record next. If you need to adjust anything, take a **new** reference photo afterward — don't
  reuse an old one against a moved camera.

## 3. Record clips

Aim for **10-15 clips** in the first batch. Mix in as many of these as you can:

- A few clean sends (climber reaches the top hold successfully).
- A few falls (climber comes off partway).
- Different climbers, if more than one person is available — different heights/styles are useful.
- At least one fast, dynamic attempt and one slow, controlled attempt.
- At least one clip with a brief hand occlusion (a hand briefly hidden behind the body, another
  limb, or the climber's own head).
- At least one clip with a brief foot occlusion (foot behind the leg, or briefly off-camera at the
  frame edge).
- At least one clip where the climber's hand or foot passes near a **hold on a neighboring route**
  (reaching past, or briefly resting near, a hold that isn't part of the route being climbed).

You don't need to hit every category in every clip — spread them across the batch. The goal right
now is **contact detection** validation (does the algorithm correctly say "yes, a hand/foot
touched hold N here"), not full route-verification accuracy — don't worry about picking "clean"
attempts only.

## 4. Import into the harness

1. Open **Settings → Developer Tools → Open Validation Harness**.
2. Fill in a `wallOrFixtureId` you'll recognize later (e.g. `gym-visit-2026-08-21`) — every clip
   from this same fixed-camera session should reuse the same value.
3. Leave `cameraGeometryProfileVersion` at its default unless you've been told to change it.
4. Tap **Import Reference Wall Photo** and pick the one clean-wall photo from step 2.
5. **Annotate hold geometry**: tap the reference image to place each hold's contour vertices (3+
   taps per hold, roughly tracing its outline), then **Finish Hold**. Repeat for every hold you
   care about across the 2-3 visible routes. Mark which holds are a route's start/finish using the
   Start/Finish toggles next to each hold in the list.
6. Tap **Import Climbing Video** and pick one clip.
7. Optional but valuable: scrub to the moment you see a real contact happen, and use the **Ground
   Truth** section to record "yes, [limb] touched hold [N] here" — this is what lets the report
   show true/missed/false contact counts instead of just raw detector output with no comparison.
8. Tap **Run Analysis**, then **Save Session** once you're happy with the result — saved sessions
   persist locally and reload later from the list at the bottom of the screen.
9. Repeat steps 6-8 for each additional clip from the same fixed-camera session — you can reuse
   the same reference photo/hold annotations by leaving them as-is (only re-import the video each
   time), or load a previously-saved session from the list and just swap its video.

## 5. If a clip gets rejected

If a clip shows **"Rejected: VALIDATION_GEOMETRY_MISMATCH..."**, the harness has detected that the
video's aspect ratio (or the profile version you entered) doesn't match the reference photo. This
almost always means the phone moved, was rotated, or the zoom/orientation changed between the
reference photo and that clip — re-shoot a fresh reference photo from the camera's actual current
position rather than trying to force the mismatch through.

## What NOT to worry about yet

- Perfect hold annotation precision — rough contours are fine for this validation pass.
- Getting every clip "clean" — falls, occlusions, and messy attempts are exactly what's useful
  right now.
- Route-level accuracy (which specific route a climb belongs to) — that's Phase 4+ work, not part
  of what this harness validates.
