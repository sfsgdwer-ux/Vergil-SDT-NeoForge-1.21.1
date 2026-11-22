# Vergil SDT – NeoForge 1.21.1

**Status: experimental in-world 3D transformation preview, NOT the full original animation port.**

## In this version
- Press **V** to toggle your local player's visual form.
- Replaces the normal third-person player model with the original SDT **Blockbench OBJ** mesh when it loads successfully.
- Uses the SDT OBJ diffuse texture and optional soul-fire particle effect.
- Retains Minecraft's original HP, attributes, speed and combat.
- Key mapping is registered correctly on the NeoForge MOD event bus.
- A failed/missing OBJ falls back to the vanilla player render.

## Important limitations
- **Static mesh only**. The original transform, untransform, idle, walk, run, and fly skeletal animations are not yet played.
- The OBJ is independent of Epic Fight's armature; the original Sin Devil Trigger armor mesh JSON uses weighted skinning, which needs a renderer/armature port.
- Other clients do not see your transformation; local 3rd-person rendering only.
- Epic Fight/Avalon replacement rendering has **not** been verified in a running game.
- Orientation, model scale, clipping, lighting, transparency and animation still need in-game testing.

## How to build
The GitHub Actions build reads `SDT_Assets.zip` (or `SDT_Assets(1).zip`) from the repository root, extracts the relevant OBJ/model/animation resources, and builds a `.jar` using Java 21 + NeoForge 21.1.265. Open [Actions](../../actions), select the latest successful run, and download `Vergil-SDT-Prototype-JAR`.

Place the extracted `.jar` in `.minecraft/mods` on Minecraft 1.21.1 NeoForge.

## Rights
Redistribute the assets only if their creator's permission extends to the publicly accessible repository and to any third-party game IP. This repository was created from user-supplied files.
