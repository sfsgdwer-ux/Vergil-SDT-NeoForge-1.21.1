# Vergil SDT – NeoForge 1.21.1

**Status: cosmetic prototype. The complete Devil May Cry Sin Devil Trigger transformation is NOT implemented.**

## Already implemented
- Key **V** toggles a local cosmetic state and cyan soul-fire particles.
- Thai and English keybind/status text.
- No attack buffs, HP change, or gameplay modification.
- A GitHub Actions build using Minecraft 1.21.1, NeoForge 21.1.265 and Java 21.

## How to import the source model resources
1. On GitHub, choose **Add file → Upload files**.
2. Upload **SDT_Assets.zip** to the **root of this repository**. Keep the ZIP name exactly `SDT_Assets.zip` (alternatively `SDT_Assets(1).zip`).
3. Commit the change to `main`.
4. The [Actions](../../actions) workflow automatically finds the ZIP, extracts SDT OBJ, textures and JSON animations, and bundles them into the new JAR.
5. Download **Vergil-SDT-Prototype-JAR** from the successful workflow run, extract the artifact ZIP, and use the inner JAR in your NeoForge mods folder.

**Important:** This step only **includes the model resources in the JAR**; it **does not render the original Vergil SDT model**. An actual player renderer, matching animation armature and Epic Fight integration must be ported from Forge 1.20.1 to NeoForge 1.21.1.

Do **not** upload `SDT_Code.zip` to the public repository: Forge 1.20.1 compiled classes cannot be used directly as NeoForge 1.21.1 classes. Compiled classes are for examining original behavior, not for blindly including in the JAR.

## Licensing / privacy
This repo is public. Only upload extracted original mod assets if you have permission to redistribute them publicly, including any DMC-derived assets. If permission covers only personal use, change the repository to **Private** before uploading the ZIP.

## Features yet to be ported
- SDT player mesh replacement and textures
- Original transform/untransform, idle, walk/run, and fly animations
- Epic Fight custom mesh/armature synchronization
- Other-player visibility/networking
- Original SDT effects
