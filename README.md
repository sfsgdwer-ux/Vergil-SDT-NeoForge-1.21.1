# Vergil SDT – NeoForge 1.21.1

## v0.4.0: Actual weighted mesh deformation (experimental)

This version uses the original user-provided **sin_devil_armor.json weighted mesh**, instead of only animating separate OBJ groups.

### Implemented
- Original **1,591 vertices**, 2,518 triangles and full original per-vertex **vcounts / vindices / weights**.
- Up to 2 original influences per vertex (960 with 1, 631 with 2).
- Bone IDs match 20 original animation joint names; 11 of those IDs are used by weighted mesh vertices.
- Skinning is recomputed client-side every frame using relative quaternions from original transformation/return clips.
- In idle/walk/run/swim/fly states, a procedural bone rig deforms the weighted vertices.
- Automatic fallback to v0.3 segmented OBJ rendering if the weighted mesh fails to load.
- Press **V** to transform, **V** again to return to normal.
- No health, abilities, damage, movement speed, or hitbox changes.
- Resources are isolated under assets/vergilsdt/sdt_data to prevent the original Epic Fight datapack constructor error.

### Important limitations
- **This is weighted skinning but not a faithful port of the original Epic Fight Armature.**
  The original SDT asset file contains vertex weights and joint IDs, but not an independent
  bind-pose/inverse-bind matrix or a complete hierarchy. The rig has estimated joint
  pivots and a first-pass skeleton hierarchy derived from the original mesh.
- Weight blending avoids hard seams, but incorrect inferred pivots may still cause
  distortions and mismatched orientations, especially near wings and accessories.
- Animation is still an approximation; the original Epic Fight runtime might define
  parent-child coordinates or rotations differently.
- Third-person local player only: no multiplayer synchronization, no first-person model.
- Minecraft + Epic Fight game-level rendering **has not been tested**; GitHub Actions
  proves compilation and JAR contents, not correct visual behavior.
- The uploaded latest.log (from v0.2.0) had an Epic Fight constructor-information error.
  This was addressed by namespace isolation in v0.3.1 and retained in v0.4.0.

### Download / install
On [GitHub Actions](../../actions), download latest successful **Vergil-SDT-NeoForge-1.21.1-v0.4.0** artifact,
extract the inner JAR, remove all older Vergil SDT JARs from the Minecraft 1.21.1
NeoForge mods folder, and use the new JAR.

If it looks distorted or does not render, send a screenshot taken with F5 plus
a fresh latest.log so the bind-pose and skinning matrices can be corrected.

### Licensing note
The repository is public and includes user-uploaded game/mod-derived assets.
Confirm redistribution rights cover these assets before publishing.
