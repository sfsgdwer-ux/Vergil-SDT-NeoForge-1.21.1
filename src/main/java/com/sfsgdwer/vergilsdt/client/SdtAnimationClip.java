package com.sfsgdwer.vergilsdt.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Quaternionf;

/** Reads original animation JSON; returns relative-to-first-frame bone transforms. */
final class SdtAnimationClip {
    private static ResourceManager cachedManager;
    private static final Map<String, SdtAnimationClip> CACHE = new HashMap<>();
    private final Map<String, Bone> bones;

    private SdtAnimationClip(Map<String, Bone> bones) {
        this.bones = bones;
    }

    static SdtAnimationClip load(String clip) {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        if (manager != cachedManager) {
            cachedManager = manager;
            CACHE.clear();
        }
        if (CACHE.containsKey(clip)) return CACHE.get(clip);

        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(
            "invincible_dmc", "animmodels/animations/biped/yamato/living/yamato_sin_devil_trigger"
            + ("revert".equals(clip) ? "_back" : "") + ".json"
        );
        Map<String, Bone> bones = new HashMap<>();
        try (InputStreamReader reader = new InputStreamReader(manager.open(file), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("animation")) {
                JsonObject boneJson = e.getAsJsonObject();
                String name = boneJson.get("name").getAsString();
                JsonArray times = boneJson.getAsJsonArray("time");
                JsonArray transforms = boneJson.getAsJsonArray("transform");
                int count = Math.min(times.size(), transforms.size());
                if (count == 0) continue;
                float[] t = new float[count];
                Key[] keys = new Key[count];
                for (int i = 0; i < count; i++) {
                    t[i] = times.get(i).getAsFloat();
                    JsonObject transform = transforms.get(i).getAsJsonObject();
                    JsonArray loc = transform.getAsJsonArray("loc");
                    JsonArray rot = transform.getAsJsonArray("rot");
                    if (loc == null || rot == null || loc.size() < 3 || rot.size() < 4)
                        throw new IllegalArgumentException("Invalid SDT bone data for " + name);
                    // Epic Fight resource format: quaternion is [w, x, y, z].
                    keys[i] = new Key(
                        loc.get(0).getAsFloat(), loc.get(1).getAsFloat(), loc.get(2).getAsFloat(),
                        new Quaternionf(rot.get(1).getAsFloat(), rot.get(2).getAsFloat(),
                            rot.get(3).getAsFloat(), rot.get(0).getAsFloat())
                    );
                }
                bones.put(name, new Bone(t, keys));
            }
        } catch (IOException | RuntimeException err) {
            System.err.println("[Vergil SDT] Failed to read original animation " + file + ": " + err);
        }
        SdtAnimationClip loaded = new SdtAnimationClip(bones);
        CACHE.put(clip, loaded);
        return loaded;
    }

    Pose sample(String bone, float time) {
        Bone b = bones.get(bone);
        return b == null ? Pose.IDENTITY : b.sample(time);
    }

    record Pose(float dx, float dy, float dz, Quaternionf rotation) {
        static final Pose IDENTITY = new Pose(0F, 0F, 0F, new Quaternionf());
    }
    private record Key(float x, float y, float z, Quaternionf rotation) {}

    private record Bone(float[] times, Key[] keys) {
        Pose sample(float time) {
            int first = 0;
            while (first + 1 < times.length && time > times[first + 1]) first++;
            int next = Math.min(first + 1, times.length - 1);
            float span = times[next] - times[first];
            float alpha = span <= 0.0001F ? 0F :
                Math.max(0F, Math.min(1F, (time - times[first]) / span));
            Key a = keys[first], b = keys[next], rest = keys[0];
            Quaternionf rotation = new Quaternionf(a.rotation)
                .slerp(b.rotation, alpha)
                .mul(new Quaternionf(rest.rotation).conjugate())
                .normalize();
            return new Pose(
                a.x + (b.x - a.x) * alpha - rest.x,
                a.y + (b.y - a.y) * alpha - rest.y,
                a.z + (b.z - a.z) * alpha - rest.z,
                rotation
            );
        }
    }
}
