# Vergil SDT – NeoForge 1.21.1

**Status: early prototype, NOT the finished Vergil Sin Devil Trigger transformation.**

This repository builds an independent NeoForge 1.21.1 client-side cosmetic test mod. It is separate from Devil Mine Craft.

## Currently implemented
- Press **V** to toggle cosmetic SDT state ON/OFF.
- A small soul-fire particle effect while active.
- Thai and English keybind/status labels.
- No attack, HP, movement, or gameplay changes.

## Not yet implemented
- The original Vergil SDT player model or skin replacement.
- Original transform/untransform animations, idle/walk/run animations.
- Epic Fight renderer compatibility, synchronization, and original VFX.
- Multiplayer support for seeing another player's transformation.

The original mod targets Forge 1.20.1; copying its compiled classes into a NeoForge 1.21.1 JAR will not port it.

## Download a test JAR
Open [Actions](../../actions), select **Build NeoForge JAR** and the latest successful run, then download the **Vergil-SDT-Prototype-JAR** artifact. Extract the JAR and put it into your `mods` folder. Requires Minecraft **1.21.1**, NeoForge **21.1.265+**, and Java **21**.

## Next porting phase
Inspect and adapt the original SDT mesh/texture/animation JSON and rendering code. Do not assume the current JAR already contains the original transformation. The original assets have not yet been uploaded into this repository.
