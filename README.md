# Vergil SDT – NeoForge 1.21.1

## Version 0.3.0 — experimental shared-rig OBJ preview

- Press **V** to transform and press **V** again to return to the original player appearance.
- Uses the original user-supplied SDT OBJ mesh and texture in third-person rendering.
- Reads original transition keyframes from `yamato_sin_devil_trigger.json` and `yamato_sin_devil_trigger_back.json` to approximate transformation and reversal.
- Improves animation: torso, head, arms, forearms, hands, thighs, legs and feet share a **parent-child joint hierarchy**, rather than each part rotating independently.
- Separates repeatedly named OBJ objects instead of accidentally merging all objects called `cube` into one huge rigid body. Assigns unnamed cubes to nearby body-region pivots as a heuristic.
- Basic procedural walk, sprint, idle breathing, swimming and elytra-glide poses.
- **Purely cosmetic**: HP, hitbox, speed, attacks and abilities remain unchanged.

### Critical limitations
- This is **not the source mod's weighted Epic Fight mesh renderer**. It uses segmented OBJ pieces with approximate parent joints and original transition quaternion *deltas*.
- The original SDT mesh JSON has weighted skinning which is not used here. The model may still clip or rotate incorrectly because precise bind-pose transforms and full Epic Fight armature are not yet ported.
- Full original walk/idle/run animation JSON files are not in the current uploaded SDT_Assets.zip. Procedural approximations are used instead.
- Local player's third-person view only; not network synchronized, no first-person replacement.
- Not tested visually in Minecraft. A successful GitHub Actions build demonstrates compilation, not in-game correctness. Rendering with Epic Fight or Avalon needs validation.

### Installing
[Open GitHub Actions](../../actions), find the latest successful **Build NeoForge JAR** and download **Vergil-SDT-Prototype-JAR**. Unzip the artifact and place only the contained JAR in the Minecraft 1.21.1 NeoForge mods folder. Remove older Vergil SDT JARs.

### Source assets
The workflow imports models from the repository's `SDT_Assets.zip`. Confirm rights to redistribute mod/game-derived assets before making this repository public.
