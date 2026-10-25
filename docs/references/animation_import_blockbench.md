# Authoring and Importing Animation Clips

How a clip is written, how a Blockbench export becomes one, and how it reaches playback. Read
[animation_system.md](animation_system.md) first for the concepts this guide uses: rigs and channels, ownership,
suppression, and the slot order.

Transcribing an export is rote; the failure modes are not. Read **Pitfalls** before you start — every one of them has
already bitten a previous conversion.

---

## What a clip is

A clip is a [KeyframeAnimation](../../src/main/java/dev/breezes/settlements/domain/animation/KeyframeAnimation.java)
built with `KeyframeAnimation.fromTracks()`, which returns a
[TrackAnimationBuilder](../../src/main/java/dev/breezes/settlements/domain/animation/TrackAnimationBuilder.java). It
holds:

- one [AnimationTrack](../../src/main/java/dev/breezes/settlements/domain/animation/AnimationTrack.java) per animated
  channel, each a list of keyframes at unique ticks with the easing toward the next key;
- its timing: duration, loop mode, and blend-in and blend-out;
- its composition declarations: what it owns, what it suppresses, and which arms it claims.

Clips are authoring data and are never sampled as written. Each is baked for the rig it animates, and baking rejects a
clip that keys a channel its rig lacks, keys one channel twice, or owns a mask the rig cannot resolve, so a malformed
clip fails when its rig's clips are baked rather than drawing wrong.

### Values are relative to rest

Every keyed value is relative to the bone's rest transform, which the model's baked layer defines. A rotation or a
translation is a delta that adds onto the rest pose, and a scale is a factor that multiplies the rest scale. A clip
therefore keys only motion away from rest: the neutral rotation and translation are zero, the neutral scale is one, and
a channel left unkeyed stays where the slots below put it.

### Rotations add as Euler angles

A rotation value is a vector of pitch, yaw, and roll in radians about X, Y, and Z, added componentwise onto the angles
beneath it and interpolated componentwise between keys. An exported rotation is relative to the bone's rest angles in
the same way, which is why it transcribes verbatim. Interpolation follows the numbers rather than the shortest turn, so
keys can wind a bone through whole turns, as the ballista's crankshaft does. It is a convention for small additive
motion, not a general rotation model; the limits are under
[Deliberate constraints](animation_system.md#deliberate-constraints).

---

## The source

The input is Blockbench's **"Export → Java"** output: a vanilla `net.minecraft.client.animation.AnimationDefinition`
builder. Example shape:

```
AnimationDefinition.Builder.withLength(1.5F)
    .addAnimation("arm_straight_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
        new Keyframe(0.0F, KeyframeAnimations.degreeVec(0, 0, 0), AnimationChannel.Interpolations.LINEAR),
        new Keyframe(0.4F, KeyframeAnimations.degreeVec(-80, 10, 10), ...), ...))
    .addAnimation("torso", new AnimationChannel(AnimationChannel.Targets.POSITION, ...))
    .build();
```

Two facts that make this tractable:

1. **The clips are authored against our own rigs** (the `.bbmodel` sources under `assets/models/`), so the Blockbench
   bone names match our model parts exactly and the values are already in our coordinate space.
2. **We do not run vanilla `AnimationDefinition` at runtime.** Our clips are sampled in **ticks**, not seconds. You
   transcode the data into `KeyframeAnimation` tracks; you never reference the exported class.

Each `addAnimation(bone, channel)` maps directly to one track, added with `.track(...)`. There is no intermediate pose
representation.

---

## Conversion rules

| Source (vanilla) | Ours | Notes |
|---|---|---|
| timestamp `0.4F` (seconds) | `Math.round(0.4 * 20)` = `8` (ticks) | A keyframe tick is an `int` and **must be unique within a track** (see Pitfalls) |
| `withLength(1.5F)` | `durationTicks(30)` | `Math.round(seconds * 20)` |
| `degreeVec(p, y, r)` | `RotationUtil.degrees(p, y, r)` | **Verbatim**: both convert degrees to radians, applied additively |
| `posVec(x, y, z)` | `new Vec3(x, -y, z)` | **Negate Y.** Vanilla `posVec` returns `(x, -y, z)`; our translation channels add onto the bone as written, with no flip. Forgetting this inverts every translation. |
| all-zero translation keyframe | `Vec3.ZERO` | |
| `scaleVec(x, y, z)` | `new Vector3f(x, y, z)` | **Verbatim**: no Y flip, and **do not subtract 1.** See below. |
| `Interpolations.LINEAR` | `Easing.LINEAR` | |
| `Interpolations.CATMULLROM` | Choose after comparing with the source | No available easing reproduces the spline — see Pitfalls. |

Y-negation worked example — vanilla `posVec(0, -4.5, 2.5)` becomes `new Vec3(0.0, 4.5, 2.5)`.

### Scale is verbatim, despite what `scaleVec` returns

Vanilla `scaleVec(x, y, z)` returns `(x-1, y-1, z-1)` — an *offset* from unit scale, which vanilla then adds onto a base
of `1`. Our scale channels are factors from a `(1, 1, 1)` rest, multiplied onto the bone's rest scale. The two systems
meet at the same final size only if you transcribe **the literal from the export**: `scaleVec(1, 1.5, 1)` →
`new Vector3f(1.0F, 1.5F, 1.0F)`. Subtracting 1 yourself would land the part at half size instead of one-and-a-half.

Consequently the no-op scale keyframe is all **ones**, not all zeros.

### Bone to channel

A `ROTATION` channel maps to the bone's rotation target, keyed with `Vector3f` values from `RotationUtil.degrees`; a
`POSITION` channel to its translation target, keyed with `Vec3`; a `SCALE` channel to its scale target, keyed with
`Vector3f`. The compiler holds each track to its target's value type.

Each target declares its bone by the Blockbench name, so the target for a bone is found by searching for that name:

- villager bones in [AnimationTargets](../../src/main/java/dev/breezes/settlements/domain/animation/AnimationTargets.java),
  where the `torso` bone's targets are named `BODY_*`;
- attachment slots in [SlotTargets](../../src/main/java/dev/breezes/settlements/domain/animation/SlotTargets.java);
- umbrella bones in
  [UmbrellaAnimationTargets](../../src/main/java/dev/breezes/settlements/domain/animation/UmbrellaAnimationTargets.java)
  and ballista bones in
  [BallistaAnimationTargets](../../src/main/java/dev/breezes/settlements/domain/animation/BallistaAnimationTargets.java),
  beside the rig each forms.

If a channel has no target, add one with `AnimationTarget.rotation`, `translation`, or `scale`, naming its bone, and add
it to the rig, as [Add an animation channel](animation_system.md#add-an-animation-channel) describes. A model binds
every rig channel to its part by the bone's name, so the bone must name a part of the baked layer. Do not invent a
target for a channel that is constant-neutral; drop it (see Pitfalls).

---

## Declarations the source does not contain

The export has geometry and timing. Everything below is a deliberate choice, and none of it is inferred from which
channels the clip keys.

### Arm claims

A clip claims the arm geometry it drives, per arm:

- `arms(ArmConfiguration)` claims both arms from the first tick;
- `leftArm(ArmPose)` and `rightArm(ArmPose)` claim one arm, leaving the other to the slots below;
- `armConfigurationAt`, `leftArmAt`, and `rightArmAt` change a claim from a given tick, for a clip that hands off between
  geometries mid-play.

A clip that names no arm claims none. Claiming one arm twice at one tick throws, even with the same pose. The claim must
match the geometry the clip animates: a clip that animates `arms_crossed` needs that side crossed, and one that animates
`arm_straight_*` needs it straight. Get this wrong and the gesture renders on hidden geometry, that is, invisibly. This
is the single most common error.

### Ownership

- `owns(VillagerAnimationRig.HEAD_ROTATION)` when the clip keys head rotation and should take the look. Without it, the
  head keys add onto wherever the villager is looking, which is right for a gait's head bob and wrong for a clip that
  aims the head at its work.
- `owns(VillagerAnimationRig.LEG_ROTATION)` whenever the clip keys leg rotation, so its stance replaces the one below
  instead of stacking on it.

Every villager clip outside the exclusive profile owns only those rotation-only masks, and only the ones it keys, so its
translations stay additive.
[ProductionClipCompositionTest](../../src/test/java/dev/breezes/settlements/domain/animation/ProductionClipCompositionTest.java)
holds every production clip to that. Owning a region mask instead is a deliberate visible change, made clip by clip,
and that test has to admit it. `owns(mask, coverage)` owns partway, for a clip that should only lean the channel toward
its own value.

### Suppression

`suppresses(...)` turns a component off at full strength wherever the clip plays.
[SuppressibleComponent](../../src/main/java/dev/breezes/settlements/domain/animation/SuppressibleComponent.java) says
what each covers. Declare one when the clip's own pose must hold against it:

- `BLINK` when the clip holds the eyelids, since the blink owns them and would close eyes the clip holds wide. Every
  production clip that keys the eyelids declares it, except the blink itself and the gaits, whose raised lids are meant
  to blink shut.
- `BREATH` when the clip's body bob replaces breathing rather than stacking on it, as every gait does.
- `GAZE` and `BROW` when the eyes and brow are the clip's own expression, as ponder's upward glance and raised brow are.
  Without `GAZE`, a clip's pupil keys add onto the gaze, which moves both pupils the same way toward the villager's
  look and carries the look while the clip holds the head. Suppress it when the pupils hold a direction of their own,
  such as a line being read or food in the hands; leave it on when they only change the pupils' size or sit off rest
  as an expression, so the eyes stay on the look target of the work.

### Exclusive playback

`playExclusively()` owns every model-part channel and suppresses every component. Declare it only for a clip that
carries the whole body and its own breathing and lids, as sleep does. An explicit `owns(...)` beside it sets the
coverage of the channels it names in place of the full coverage.

### Timing

- **`loopMode`** — `ONCE` or `LOOP` is decided by how the behavior *uses* the clip, **not** by the Blockbench
  `.looping()` flag, which is a preview setting and routinely wrong. A one-shot action re-triggered per beat is `ONCE`; a
  sustained state is `LOOP`.
- **`blendInTicks` / `blendOutTicks`** — the slot's weight fades over these, which also fades owned head and leg
  rotation to and from the pose beneath. Use enough to avoid a visible snap; tune against the clip.
- **Timing constants** — expose gameplay-relevant frames and durations as `public static final int` on the factory.
  Existing examples include `FishingAnimations.CAST_IMPACT_TICK` and `HarvestCropAnimations.HARVEST_AT_TICK` /
  `HARVEST_DURATION_TICKS`.

---

## Pitfalls

1. **Arm claim against driven geometry.** (Above.) Cross-check: list the arm bones the clip animates, and confirm they
   are visible under the arms it claims.
2. **Y-negation on translations.** Forgetting flips every translation track upside down.
3. **Tick rounding and collisions.** Convert with `Math.round(seconds * 20)`. Duplicate ticks throw, but there is no
   universal sample to discard. Preserve a key authored exactly on an integer tick over a nearby sub-tick key. For dense
   or ambiguous curves, resample the source curve at integer-tick times. Otherwise retain the sample that best preserves
   the source value and direction at that tick. Never cascade collisions forward; that distorts timing and can push the
   closing key past `durationTicks`. Preserve the effective source values at tick 0 and `durationTicks`, intentional
   holds and gaps, unique ticks, and the clip duration.
4. **Don't drop channels that map.** Only drop a channel whose every keyframe is neutral — all zeros for rotation and
   translation, all **ones** for scale. Everything else has a target; check before assuming otherwise.
5. **A single-keyframe channel is a pose, not a no-op.** Blockbench emits single-key channels to hold a constant
   non-neutral offset for the whole clip — crossed pupils, splayed legs, a squashed eyeball. A track holds its first key
   before it and its last key after it, so a one-key track holds that value throughout. Transcribe them; dropping them
   loses the pose.
6. **Head and leg rotation replace only when the clip owns them.** A clip that keys head rotation without
   `owns(VillagerAnimationRig.HEAD_ROTATION)` adds onto the look, and one that keys leg rotation must own
   `LEG_ROTATION` (see Ownership).
7. **`Easing.CUBIC` is not Catmull-Rom.** It is segment smoothstep with no spline tangents or overshoot. It is not
   categorically closer than `LINEAR`; compare both with the source. Sparse curves may need extra sampled keys.
8. **Blockbench shows both arm rigs; the game shows one.** Animators routinely key `arms_crossed` *and*
   `arm_straight_*` in the same clip because both are visible while authoring. Only the set matching the arm claims
   renders. Before dropping the losing side, check whether it is actually a mid-clip hand-off that wants
   `armConfigurationAt(tick, …)` — `HarvestCropAnimations` and `TillAnimations` both use the crossed channel as a
   return-to-rest tail.
9. **Loop seams must be transform-continuous.** Compare the effective transform before the wrap with tick 0. Euler
   rotations differing by whole turns can render identically while preserving one-way winding; translation and scale
   generally need equal endpoints. Inspect both pose and direction at the seam.
10. **The pupils' rest comes from the eye geometry.** The model builds each pupil from
    [VillagerEye](../../src/main/java/dev/breezes/settlements/domain/animation/VillagerEye.java): smaller than its
    texture square, and resting a little outward of its eye's inner edge. A pupil translation is relative to that rest
    and a pupil scale multiplies that rest size, so rebase an export authored against a pupil resting elsewhere onto
    it, and never key the eyes' constant outward offset into a clip. Every pupil a clip keys must stay inside the
    bounds [PupilContainment](../../src/main/java/dev/breezes/settlements/domain/animation/PupilContainment.java)
    holds it to; the animation tests sweep every production clip's pupils against them. In game the gaze adds onto
    those keys unless the clip suppresses `GAZE`, and the containment bounds the sum: a pupil keyed against the outer
    edge stays there whenever the look points past it, and one carried past the inner or bottom edge squashes against
    it.

Gaits are ordinary clips. The Locomotion slot phases a gait by limb swing, one vanilla limb cycle to one authored cycle,
so the cadence survives speed changes; author one full stride across the clip's duration.

---

## File structure

One factory class per clip or per family, in
`dev.breezes.settlements.domain.animation`. [HarvestCropAnimations.java](../../src/main/java/dev/breezes/settlements/domain/animation/HarvestCropAnimations.java)
is the canonical worked example (multiple arms, body translation, legs, nose). Skeleton:

```java

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FooAnimations {

    public static final int FOO_DURATION_TICKS = 30;
    public static final int FOO_PEAK_TICK = 14; // the action frame, for behavior coordination

    public static KeyframeAnimation foo() {
        return KeyframeAnimation.fromTracks()
                .id(ResourceLocationUtil.mod("animation/<family>/<clip>"))
                .durationTicks(FOO_DURATION_TICKS)
                .loopMode(LoopMode.ONCE)
                .blendInTicks(4).blendOutTicks(4)
                .owns(VillagerAnimationRig.HEAD_ROTATION)
                .arms(ArmConfiguration.BOTH_STRAIGHT)
                .track(AnimationTrack.<Vector3f>builder()
                        .target(AnimationTargets.ARM_STRAIGHT_RIGHT_ROTATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, RotationUtil.degrees(0f, 0f, 0f), Easing.LINEAR),
                                new Keyframe<>(8, RotationUtil.degrees(-80f, 10f, 10f), Easing.LINEAR),
                                new Keyframe<>(30, RotationUtil.degrees(0f, 0f, 0f), Easing.LINEAR)))
                        .build())
                .track(AnimationTrack.<Vector3f>builder()
                        .target(AnimationTargets.HEAD_ROTATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, RotationUtil.degrees(0f, 0f, 0f), Easing.LINEAR),
                                new Keyframe<>(8, RotationUtil.degrees(20f, 0f, 0f), Easing.LINEAR),
                                new Keyframe<>(30, RotationUtil.degrees(0f, 0f, 0f), Easing.LINEAR)))
                        .build())
                .track(AnimationTrack.<Vec3>builder()
                        .target(AnimationTargets.ARM_STRAIGHT_RIGHT_TRANSLATION)
                        .keyframes(List.of(
                                new Keyframe<>(0, Vec3.ZERO, Easing.LINEAR),
                                new Keyframe<>(8, new Vec3(0.0, 1.0, 1.0), Easing.LINEAR),
                                new Keyframe<>(30, Vec3.ZERO, Easing.LINEAR)))
                        .build())
                // ...one .track(...) per usable channel...
                .build();
    }
}
```

Imports needed beyond the same-package types: `RotationUtil`, `ResourceLocationUtil` (`shared.util`), `ArmConfiguration`
(`domain.presentation`), `Vec3` (`net.minecraft.world.phys`), `Vector3f` (`org.joml`), `List` (`java.util`), plus
Lombok.

---

## Wiring (how a clip reaches playback)

A villager clip plays only once it is baked, and the
[BakedAnimationRegistry](../../src/main/java/dev/breezes/settlements/domain/animation/BakedAnimationRegistry.java) bakes,
once per client session, exactly the clips the session's libraries hand out. Registering the clip in
[ClientAnimationModule](../../src/main/java/dev/breezes/settlements/di/modules/client/ClientAnimationModule.java) is
therefore what makes it playable; a clip no library returns is never baked, and nothing can play it.

1. **Archetype** — pick one
   from [AnimationArchetype](../../src/main/java/dev/breezes/settlements/domain/animation/AnimationArchetype.java), or
   append a new value (**at the end** — the ordinal is the network wire format). Reuse before inventing: a new tool's
   swing is still `SWING_HEAVY`.
2. **Category** — usually `ItemCategory.GENERIC`. Use a specific category (e.g. `AXE`) only to specialize a variant for
   that item; the library falls back `exact → (archetype, GENERIC) → (NONE, GENERIC)`.
3. **Register** — add a `Map.entry(AnimationKey.of(archetype, category), FooAnimations.foo())`
   to `ClientAnimationModule.animationLibrary`. A gait is registered by `NavigationType` in its locomotion library, and
   an idle animation in its idle library's list.
4. **New targets** — only if a channel had no home (see Bone to channel).

Registration makes the clip *resolvable*, not *played*. Drive `ONCE` actions with `villager.triggerMotion(archetype)`
and sustained states with `villager.setMotion(archetype)`. One-shots auto-pop after their blend-out; do not restore
`NONE` per phase. Clear sustained motion when its owning state ends, with behavior teardown as a safety net.
See [Add an action animation](animation_system.md#add-an-action-animation).

The umbrella's and the ballista's clips are not registered: each is baked once by the class that plays it, for the rig
it animates.

---

## Verify

- `./gradlew compileJava` (view only the tail — Gradle is noisy).
- Run the animation tests. They discover every production clip from its factory, bake it for the rig holding its
  channels, and check its declarations, so a clip that keys a channel outside its rig, or keys head or leg rotation
  without owning it, fails there before it fails in game.
- Compare the curve and loop seam with the source in Blockbench.
- Verify in game through the real archetype or locomotion path; there is no in-game pose preview.
- Where the clip is wired correctly but a placement or magnitude reads wrong, see
  [Animation and Model Tuning](animation_model_tuning.md) for finding the value without a relaunch per guess.
