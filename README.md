# Vergil SDT – NeoForge 1.21.1

## 0.2.0 animated 3D preview (experimental)

- V toggles the local third-person character into and out of the user-supplied Vergil Sin Devil Trigger OBJ model.
- Original transformation and return animation JSON are loaded, and their quaternion rotations are applied as approximate relative transforms to selected OBJ bone-named parts.
- The model has basic procedural idle breathing, walking and running motion after transformation.
- Model size and ground alignment are normalized.
- No HP, hitbox, movement speed, damage or skills are changed.

### Caveats
- The original weighted mesh and full Epic Fight skeleton are NOT ported. This version approximates keyframe movements using independent OBJ groups, so parts may clip or stretch and unassigned 'cube' groups remain static.
- Full original idle, walk, run and flying animation JSON are not included in the current asset ZIP, so locomotion is procedural rather than exact.
- It renders only the local player's transformation and does not synchronize it over multiplayer.
- Render compatibility with Epic Fight/Avalon is not validated, and the build has not been tested in-game.
- The GitHub Actions job automatically extracts original model assets from SDT_Assets.zip at the repository root.

Download the latest successful JAR from GitHub Actions. Extract the artifact ZIP and place the JAR inside your Minecraft 1.21.1 NeoForge mods directory.

The original Devil Mine Craft targets Forge 1.20.1. Porting its classes directly to 1.21.1 is not supported. Verify redistribution rights for original assets in this public repository.
