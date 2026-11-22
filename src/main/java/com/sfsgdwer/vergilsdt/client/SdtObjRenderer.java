package com.sfsgdwer.vergilsdt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Quaternionf;

/**
 * Animated OBJ preview, not the complete original Epic Fight skin-weighted renderer.
 * Named Blockbench OBJ groups move with partial original keyframes and walking poses.
 */
final class SdtObjRenderer {
    private static final ResourceLocation OBJ =
        ResourceLocation.fromNamespaceAndPath("invincible_dmc", "models/obj/sdt.obj");
    private static final ResourceLocation SKIN =
        ResourceLocation.fromNamespaceAndPath("invincible_dmc", "textures/models/obj/sdt.png");

    private static ResourceManager cachedManager;
    private static Model cachedModel;

    private SdtObjRenderer() {}

    static boolean render(RenderPlayerEvent.Pre event, String clip, float seconds) {
        Model model = model();
        if (model == null || model.parts.isEmpty()) return false;
        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        try {
            float partial = event.getPartialTick();
            float yaw = Mth.rotLerp(partial, event.getEntity().yBodyRotO,
                event.getEntity().yBodyRot);
            stack.mulPose(Axis.YP.rotationDegrees(180F - yaw));

            // Fit bounding height into approximately 2.35 blocks.
            float scale = Mth.clamp(2.35F / Math.max(0.1F, model.maxY - model.minY),
                0.40F, 1.10F);
            stack.scale(scale, scale, scale);
            stack.translate(-model.centerX, -model.minY, -model.centerZ);
            if (event.getEntity().isCrouching()) stack.translate(0D, -0.10D, 0D);

            SdtAnimationClip animation = clip == null ? null : SdtAnimationClip.load(clip);
            if (animation != null) {
                SdtAnimationClip.Pose root = animation.sample("Root", seconds);
                applyJointRotation(stack, model.centerX, model.minY + 0.85F,
                    model.centerZ, root.rotation());
                stack.translate(root.dx(), root.dy(), root.dz());
            }

            float speed = (float) Math.sqrt(
                event.getEntity().getDeltaMovement().horizontalDistanceSqr());
            float stride = animation == null && !event.getEntity().isPassenger()
                ? Math.min(1F, speed * (event.getEntity().isSprinting() ? 7F : 11F))
                : 0F;
            float phase = (event.getEntity().tickCount + partial) *
                (event.getEntity().isSprinting() ? 0.95F : 0.70F);
            float breathing = Mth.sin((event.getEntity().tickCount + partial) * 0.10F);
            if (animation == null) {
                stack.translate(0D, breathing * 0.012F +
                    Math.abs(Mth.sin(phase)) * 0.025F * stride, 0D);
            }

            VertexConsumer sink = event.getMultiBufferSource()
                .getBuffer(RenderType.entityCutoutNoCull(SKIN));
            for (Part part : model.parts) {
                stack.pushPose();
                try {
                    if (animation != null) {
                        String bone = matchBone(part.name);
                        if (bone != null) {
                            SdtAnimationClip.Pose pose = animation.sample(bone, seconds);
                            applyJointRotation(stack, part.pivotX, part.pivotY,
                                part.pivotZ, pose.rotation());
                            stack.translate(pose.dx(), pose.dy(), pose.dz());
                        }
                    } else {
                        animatePart(stack, part, phase, stride, breathing);
                    }

                    PoseStack.Pose pose = stack.last();
                    for (Face f : part.faces) {
                        for (Point v : f.points) {
                            sink.addVertex(pose.pose(), v.x, v.y, v.z)
                                .setColor(255, 255, 255, 255)
                                .setUv(v.u, v.v)
                                .setOverlay(OverlayTexture.NO_OVERLAY)
                                .setLight(event.getPackedLight())
                                .setNormal(pose, f.nx, f.ny, f.nz);
                        }
                    }
                } finally {
                    stack.popPose();
                }
            }
        } finally {
            stack.popPose();
        }
        return true;
    }

    private static void animatePart(PoseStack stack, Part part,
                                    float phase, float stride, float breathing) {
        float limbSwing = Mth.sin(phase) * stride * 33F;
        float degrees = switch (part.name) {
            case "RightArm", "RightForeArm", "RightHand" -> -limbSwing * 0.75F;
            case "LeftArm", "LeftForeArm", "LeftHand" -> limbSwing * 0.75F;
            case "RightLeg", "RightLowerLeg", "RightFoot" -> limbSwing;
            case "LeftLeg", "LeftLowerLeg", "LeftFoot" -> -limbSwing;
            case "Head", "AllHead" -> breathing * 1.3F;
            case "UpBody", "UpperBody" -> breathing * 0.8F;
            default -> 0F;
        };
        if (Math.abs(degrees) > 0.001F) {
            stack.translate(part.pivotX, part.pivotY, part.pivotZ);
            stack.mulPose(Axis.XP.rotationDegrees(degrees));
            stack.translate(-part.pivotX, -part.pivotY, -part.pivotZ);
        }
    }

    private static String matchBone(String name) {
        return switch (name) {
            case "RightArm" -> "Arm_R";
            case "LeftArm" -> "Arm_L";
            case "RightForeArm" -> "Elbow_R";
            case "LeftForeArm" -> "Elbow_L";
            case "RightHand" -> "Hand_R";
            case "LeftHand" -> "Hand_L";
            case "RightLeg" -> "Thigh_R";
            case "LeftLeg" -> "Thigh_L";
            case "RightLowerLeg" -> "Leg_R";
            case "LeftLowerLeg" -> "Leg_L";
            case "RightFoot" -> "Knee_R";
            case "LeftFoot" -> "Knee_L";
            case "Head", "AllHead" -> "Head";
            case "UpperBody", "UpBody" -> "Chest";
            default -> null;
        };
    }

    private static void applyJointRotation(PoseStack stack, float x, float y,
                                           float z, Quaternionf rotation) {
        stack.translate(x, y, z);
        stack.mulPose(rotation);
        stack.translate(-x, -y, -z);
    }

    private static Model model() {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        if (manager != cachedManager || cachedModel == null) {
            cachedManager = manager;
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(manager.open(OBJ), StandardCharsets.UTF_8))) {
                cachedModel = parse(reader);
            } catch (IOException | RuntimeException ex) {
                System.err.println("[Vergil SDT] Failed to load OBJ: " + ex);
                cachedModel = new Model(List.of(), 0F, 1F, 0F, 0F);
            }
        }
        return cachedModel;
    }

    private static Model parse(BufferedReader reader) throws IOException {
        List<float[]> coords = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        Map<String, PartBuilder> pieces = new LinkedHashMap<>();
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY,
            minZ = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY,
            maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        String current = "unnamed";
        String line;
        while ((line = reader.readLine()) != null) {
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("#")) continue;
            String[] row = s.split("\\s+");
            try {
                switch (row[0]) {
                    case "o", "g" -> {
                        if (row.length > 1) current = row[1];
                    }
                    case "v" -> {
                        if (row.length < 4) break;
                        float x = Float.parseFloat(row[1]);
                        float y = Float.parseFloat(row[2]);
                        float z = Float.parseFloat(row[3]);
                        coords.add(new float[] {x, y, z});
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                    }
                    case "vt" -> {
                        if (row.length < 3) break;
                        uvs.add(new float[] {
                            Float.parseFloat(row[1]),
                            1F - Float.parseFloat(row[2])
                        });
                    }
                    case "f" -> {
                        if (row.length < 4) break;
                        Point[] polygon = new Point[row.length - 1];
                        for (int i = 1; i < row.length; i++) {
                            String[] ids = row[i].split("/", -1);
                            float[] xyz = coords.get(index(ids[0], coords.size()));
                            float[] uv = {0F, 0F};
                            if (ids.length > 1 && !ids[1].isBlank())
                                uv = uvs.get(index(ids[1], uvs.size()));
                            polygon[i - 1] = new Point(xyz[0], xyz[1], xyz[2], uv[0], uv[1]);
                        }
                        PartBuilder builder = pieces.computeIfAbsent(current, PartBuilder::new);
                        if (polygon.length == 4) {
                            builder.add(face(polygon[0], polygon[1], polygon[2], polygon[3]));
                        } else {
                            for (int i = 1; i + 1 < polygon.length; i++)
                                builder.add(face(polygon[0], polygon[i], polygon[i + 1],
                                    polygon[i + 1]));
                        }
                    }
                    default -> {}
                }
            } catch (NumberFormatException | IndexOutOfBoundsException malformed) {
                // Skip malformed elements instead of crashing the render thread.
            }
        }
        if (coords.isEmpty()) return new Model(List.of(), 0F, 1F, 0F, 0F);
        List<Part> finished = pieces.values().stream().map(PartBuilder::finish).toList();
        return new Model(finished, minY, maxY, (minX + maxX) * 0.5F,
            (minZ + maxZ) * 0.5F);
    }

    private static int index(String token, int length) {
        int i = Integer.parseInt(token);
        return i > 0 ? i - 1 : length + i;
    }

    private static Face face(Point a, Point b, Point c, Point d) {
        float ax = b.x - a.x, ay = b.y - a.y, az = b.z - a.z;
        float bx = c.x - a.x, by = c.y - a.y, bz = c.z - a.z;
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 0.0001F) {
            nx /= length; ny /= length; nz /= length;
        } else {
            nx = 0F; ny = 1F; nz = 0F;
        }
        return new Face(new Point[]{a, b, c, d}, nx, ny, nz);
    }

    private static final class PartBuilder {
        final String name;
        final List<Face> faces = new ArrayList<>();
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY,
            minZ = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY,
            maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

        PartBuilder(String name) { this.name = name; }

        void add(Face face) {
            faces.add(face);
            for (Point v : face.points) {
                minX = Math.min(minX, v.x); maxX = Math.max(maxX, v.x);
                minY = Math.min(minY, v.y); maxY = Math.max(maxY, v.y);
                minZ = Math.min(minZ, v.z); maxZ = Math.max(maxZ, v.z);
            }
        }

        Part finish() {
            float pivotY = maxY;
            if (name.equals("Head") || name.equals("AllHead")) pivotY = minY;
            return new Part(name, faces, (minX + maxX) * 0.5F,
                pivotY, (minZ + maxZ) * 0.5F);
        }
    }

    private record Model(List<Part> parts, float minY, float maxY,
                         float centerX, float centerZ) {}
    private record Part(String name, List<Face> faces, float pivotX, float pivotY,
                        float pivotZ) {}
    private record Point(float x, float y, float z, float u, float v) {}
    private record Face(Point[] points, float nx, float ny, float nz) {}
}
