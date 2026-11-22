# Vergil SDT – NeoForge 1.21.1

## Version 0.3.1 — Resource namespace compatibility fix

**Issue diagnosed using the player's latest.log (v0.2.0):** Epic Fight tried loading original namespace
`invincible_dmc:biped/yamato/living/yamato_sin_devil_trigger` and logged
`Datapack animation reading failed: No constructor information has provided`.
The standalone mod included the original animation JSON at a path that Epic Fight scans, without
registering Epic Fight animation constructor metadata.

**Fix:** The build now bundles the user-supplied SDT OBJ/texture/animation resources under the
internal path `assets/vergilsdt/sdt_data/`. Source code loads these new locations directly.
This isolates the resources from Epic Fight's original animation datapack scanner.
A GitHub Actions validation step fails the build if legacy `assets/invincible_dmc/` paths
accidentally appear in the JAR.

### Current features
- Key **V** toggles the local third-person player's SDT cosmetic transformation.
- Original SDT OBJ mesh and texture with an experimental parent-joint hierarchy.
- Approximate transformation/reversion via original JSON keyframes.
- Basic procedural movement, breath, swim and glide poses.
- No changes to HP, damage, hitbox, equipment, mana, or movement speed.

### Limitations
- The model is still a segmented OBJ preview, not full Epic Fight skin-weighted/armature animation.
- Other player clients will not see the transformation; there is no networking.
- Epic Fight renderer conflicts may still exist. This fix specifically addresses resource name collision.
- **GitHub Actions compilation is not an in-game test.** If the game still reports an error,
  capture a new latest.log with v0.3.1 installed and after pressing V.
- The previous log also shows Player Animator gecko_legacy/emotecraft decode errors and an
  Infinity Gauntlet testaw.json syntax error. Those are separate errors and cannot be
  conclusively attributed to Vergil SDT without isolation.

### Install / test
Download the latest successful GitHub Actions artifact, extract its JAR, and replace all old
`Vergil-SDT-NeoForge-1.21.1-*.jar` files in Minecraft 1.21.1 NeoForge's mods directory.
Verify Minecraft's Mod List displays **Vergil SDT Cosmetic 0.3.1**.
Try F5 and V and attach a new latest.log if a problem persists.

### Rights
The user provided original assets and stated permission for porting. Confirm permission also
allows redistribution in this public repository.
