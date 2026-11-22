package com.sfsgdwer.vergilsdt.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Experimental weighted SDT skinning renderer.
 *
 * The original Epic Fight mesh supplies actual vertices, triangle indices,
 * per-vertex joint IDs, and skinning weights. We use those data unmodified.
 *
 * The source asset does not embed inverse-bind matrices or the original
 * Epic Fight armature hierarchy. Joint pivots and hierarchy are therefore
 * inferred from this SDT mesh for now. Not a bit-identical original renderer.
 */
final class SdtWeightedRenderer {
    private static final ResourceLocation MODEL =
        ResourceLocation.fromNamespaceAndPath("vergilsdt",
            "sdt_data/animmodels/entity/sin_devil_armor.json");
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("vergilsdt",
            "sdt_data/textures/mesh/sin_devil/sin_devil.png");

    // IDs verified against the original vcounts/vindices/weights and animation JSON.
    private static final String[] BONE_NAMES = {
        "Root", "Thigh_R", "Leg_R", "Knee_R", "Thigh_L", "Leg_L", "Knee_L",
        "Torso", "Chest", "Head", "Shoulder_R", "Arm_R", "Hand_R",
        "Tool_R", "Elbow_R", "Shoulder_L", "Arm_L", "Hand_L", "Tool_L", "Elbow_L"
    };
    private static final int[] PARENTS = {
        -1, 0, 1, 2, 0, 4, 5, 0, 7, 8, 8, 10, 11, 12, 11, 8, 15, 16, 17, 16
    };
    private static ResourceManager loadedFrom;
    private static Model model;
    private static boolean modelAttempted;

    private SdtWeightedRenderer() {}

    static boolean render(RenderPlayerEvent.Pre event, String clip, float seconds) {
        Model mesh = getModel();
        if (mesh == null) return false;

        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        try {
            float partial = event.getPartialTick();
            float yaw = Mth.rotLerp(partial,
                event.getEntity().yBodyRotO, event.getEntity().yBodyRot);
            stack.mulPose(Axis.YP.rotationDegrees(180F - yaw));

            float height = Math.max(0.1F, mesh.maxY - mesh.minY);
            float scale = Mth.clamp(2.35F / height, 0.5F, 1.1F);
            stack.scale(scale, scale, scale);
            stack.translate(-mesh.centerX(), -mesh.minY, -mesh.centerZ());
            if (event.getEntity().isCrouching()) {
                stack.translate(0D, -0.12D, 0D);
            }

            float speed = (float)Math.sqrt(event.getEntity()
                .getDeltaMovement().horizontalDistanceSqr());
            float stride = clip == null && !event.getEntity().isPassenger()
                ? Math.min(1F, speed * (event.getEntity().isSprinting() ? 7F : 11F))
                : 0F;
            float phase = (event.getEntity().tickCount + partial) *
                (event.getEntity().isSprinting() ? 0.95F : 0.70F);
            float breath = Mth.sin((event.getEntity().tickCount + partial) * 0.10F);
            if (clip == null) {
                stack.translate(0D, breath * 0.010F +
                    Math.abs(Mth.sin(phase)) * 0.018F * stride, 0D);
            }

            Matrix4f[] skin = makeSkinTransforms(mesh, clip, seconds, phase, stride,
                breath, event.getEntity().isFallFlying(), event.getEntity().isSwimming());

            // Skin positions once per frame, then reuse them by face index.
            Vector3f[] transformed = new Vector3f[mesh.positions.length];
            for (int i = 0; i < transformed.length; i++) {
                Vector3f bind = mesh.positions[i];
                Vector3f sum = new Vector3f();
                float weightSum = 0F;
                for (Influence influence : mesh.influences[i]) {
                    if (influence.weight <= 0F) continue;
                    Vector3f deformed = new Vector3f(bind);
                    skin[influence.bone].transformPosition(deformed);
                    sum.fma(influence.weight, deformed);
                    weightSum += influence.weight;
                }
                if (weightSum < 0.00001F) {
                    sum.set(bind);
                } else if (Math.abs(weightSum - 1F) > 0.0005F) {
                    sum.div(weightSum);
                }
                transformed[i] = sum;
            }

            VertexConsumer consumer = event.getMultiBufferSource()
                .getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
            PoseStack.Pose pose = stack.last();

            for (int i = 0; i < mesh.corners.length; i += 3) {
                Corner a = mesh.corners[i];
                Corner b = mesh.corners[i + 1];
                Corner c = mesh.corners[i + 2];
                Vector3f v0 = transformed[a.position];
                Vector3f v1 = transformed[b.position];
                Vector3f v2 = transformed[c.position];

                Vector3f normal = new Vector3f(v1).sub(v0)
                    .cross(new Vector3f(v2).sub(v0));
                if (normal.lengthSquared() <= 0.0000001F) {
                    normal.set(0F, 1F, 0F);
                } else {
                    normal.normalize();
                }

                submit(consumer, pose, v0, mesh.uv[a.uv], normal, event.getPackedLight());
                submit(consumer, pose, v1, mesh.uv[b.uv], normal, event.getPackedLight());
                submit(consumer, pose, v2, mesh.uv[c.uv], normal, event.getPackedLight());
                // Minecraft entity render type expects QUADS, not bare triangles.
                submit(consumer, pose, v2, mesh.uv[c.uv], normal, event.getPackedLight());
            }
        } finally {
            stack.popPose();
        }
        return true;
    }

    private static void submit(VertexConsumer consumer, PoseStack.Pose pose,
                               Vector3f xyz, float[] uv, Vector3f normal, int light) {
        consumer.addVertex(pose.pose(), xyz.x, xyz.y, xyz.z)
            .setColor(255, 255, 255, 255)
            .setUv(uv[0], uv[1])
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, normal.x, normal.y, normal.z);
    }

    private static Matrix4f[] makeSkinTransforms(Model mesh, String clipName,
                                                  float seconds, float phase,
                                                  float stride, float breath,
                                                  boolean gliding, boolean swimming) {
        SdtAnimationClip clip = clipName == null ? null : SdtAnimationClip.load(clipName);
        Matrix4f[] world = new Matrix4f[BONE_NAMES.length];
        for (int i = 0; i < BONE_NAMES.length; i++) {
            Vector3f p = mesh.bindPivots[i];
            Quaternionf rotation;
            float dx = 0F, dy = 0F, dz = 0F;
            if (clip == null) {
                rotation = proceduralRotation(BONE_NAMES[i], phase, stride, breath,
                    gliding, swimming);
            } else {
                SdtAnimationClip.Pose pose = clip.sample(BONE_NAMES[i], seconds);
                rotation = pose.rotation();
                dx = pose.dx();
                dy = pose.dy();
                dz = pose.dz();
            }

            Matrix4f local = new Matrix4f()
                .translate(p.x + dx, p.y + dy, p.z + dz)
                .rotate(rotation)
                .translate(-p.x, -p.y, -p.z);
            int parent = PARENTS[i];
            world[i] = parent < 0
                ? local
                : new Matrix4f(world[parent]).mul(local);
        }
        return world;
    }

    private static Quaternionf proceduralRotation(String name, float phase,
                                                   float stride, float breath,
                                                   boolean gliding, boolean swimming) {
        float swing = Mth.sin(phase) * stride * 30F;
        float degrees;
        if (gliding || swimming) {
            degrees = switch (name) {
                case "Arm_R", "Arm_L" -> -35F;
                case "Thigh_R", "Thigh_L" -> 10F;
                case "Chest" -> -8F;
                default -> 0F;
            };
        } else {
            degrees = switch (name) {
                case "Arm_R" -> -swing * 0.70F;
                case "Arm_L" -> swing * 0.70F;
                case "Thigh_R" -> swing;
                case "Thigh_L" -> -swing;
                case "Leg_R" -> Math.max(0F, swing) * 0.18F;
                case "Leg_L" -> Math.max(0F, -swing) * 0.18F;
                case "Elbow_R", "Elbow_L" -> Math.max(0F, -swing) * 0.14F;
                case "Head" -> breath * 0.6F;
                case "Chest" -> breath * 0.55F;
                default -> 0F;
            };
        }
        return new Quaternionf().rotationX(degrees * ((float)Math.PI / 180F));
    }

    private static Model getModel() {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        if (resources != loadedFrom) {
            loadedFrom = resources;
            model = null;
            modelAttempted = false;
        }
        if (modelAttempted) return model;
        modelAttempted = true;
        try (InputStreamReader reader = new InputStreamReader(
            resources.open(MODEL), StandardCharsets.UTF_8)) {
            model = decode(JsonParser.parseReader(reader).getAsJsonObject());
            System.out.println("[Vergil SDT] Weighted mesh ready: " +
                model.positions.length + " vertices and " +
                (model.corners.length / 3) + " triangles");
        } catch (IOException | RuntimeException ex) {
            System.err.println("[Vergil SDT] Weighted mesh load failed: " + ex);
        }
        return model;
    }

    private static Model decode(JsonObject root) {
        JsonObject v = root.getAsJsonObject("vertices");
        float[] rawPositions = floats(v.getAsJsonObject("positions"));
        float[] rawUvs = floats(v.getAsJsonObject("uvs"));
        int[] counts = ints(v.getAsJsonObject("vcounts"));
        float[] weights = floats(v.getAsJsonObject("weights"));
        int[] pairs = ints(v.getAsJsonObject("vindices"));
        int[] packed = ints(v.getAsJsonObject("parts").getAsJsonObject("noGroups"));

        if (rawPositions.length % 3 != 0 || rawUvs.length % 2 != 0
            || packed.length % 9 != 0 || pairs.length % 2 != 0
            || counts.length != rawPositions.length / 3) {
            throw new IllegalArgumentException("Malformed original SDT weighted mesh arrays");
        }

        Vector3f[] positions = new Vector3f[counts.length];
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY,
            minZ = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY,
            maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < positions.length; i++) {
            // Source Epic Fight exported mesh is Z-up.
            // Remap (x, depthY, heightZ) to Minecraft (x, heightZ, -depthY).
            float x = rawPositions[3*i], y = rawPositions[3*i+2],
                z = -rawPositions[3*i+1];
            positions[i] = new Vector3f(x,y,z);
            minX = Math.min(minX,x); maxX = Math.max(maxX,x);
            minY = Math.min(minY,y); maxY = Math.max(maxY,y);
            minZ = Math.min(minZ,z); maxZ = Math.max(maxZ,z);
        }

        float[][] uv = new float[rawUvs.length/2][2];
        for (int i = 0; i < uv.length; i++) {
            uv[i][0] = rawUvs[2*i];
            uv[i][1] = 1F - rawUvs[2*i+1];
        }

        Influence[][] influences = new Influence[positions.length][];
        int pairAt = 0;
        for (int i=0; i<positions.length; i++) {
            int size = counts[i];
            if (size < 1 || size > 8 || pairAt + size*2 > pairs.length) {
                throw new IllegalArgumentException("Unexpected SDT weight count for vertex " + i);
            }
            Influence[] vertexWeights = new Influence[size];
            for (int j=0; j<size; j++) {
                int bone = pairs[pairAt++];
                int weightIndex = pairs[pairAt++];
                if (bone < 0 || bone >= BONE_NAMES.length || weightIndex < 0
                    || weightIndex >= weights.length)
                    throw new IllegalArgumentException("Invalid SDT influence index");
                vertexWeights[j] = new Influence(bone, weights[weightIndex]);
            }
            influences[i] = vertexWeights;
        }
        if (pairAt != pairs.length)
            throw new IllegalArgumentException("Unused SDT vertex influence data");

        Corner[] corners = new Corner[packed.length/3];
        for (int i=0; i<corners.length; i++) {
            int pos = packed[3*i], tex = packed[3*i+1];
            if (pos<0 || pos>=positions.length || tex<0 || tex>=uv.length)
                throw new IllegalArgumentException("SDT face index out of range");
            corners[i] = new Corner(pos,tex);
        }

        Vector3f[] pivots = approximateBindPivots(positions,influences,
            minX,minY,minZ,maxX,maxY,maxZ);
        return new Model(positions,uv,influences,corners,pivots,
            minX,minY,minZ,maxX,maxY,maxZ);
    }

    private static float[] floats(JsonObject property) {
        JsonArray values = property.getAsJsonArray("array");
        float[] result = new float[values.size()];
        for (int i=0; i<result.length; i++) result[i] = values.get(i).getAsFloat();
        return result;
    }

    private static int[] ints(JsonObject property) {
        JsonArray values = property.getAsJsonArray("array");
        int[] result = new int[values.size()];
        for (int i=0; i<result.length; i++) result[i] = values.get(i).getAsInt();
        return result;
    }

    private static Vector3f[] approximateBindPivots(Vector3f[] positions,
        Influence[][] influences,float minX,float minY,float minZ,
        float maxX,float maxY,float maxZ) {

        Vector3f[] pivot = new Vector3f[BONE_NAMES.length];
        Vector3f[] centroids = new Vector3f[BONE_NAMES.length];
        float[] mass = new float[BONE_NAMES.length];
        for (int i=0; i<centroids.length; i++) centroids[i] = new Vector3f();
        for (int i=0; i<positions.length; i++) {
            for (Influence influence : influences[i]) {
                if (influence.weight <= 0F) continue;
                centroids[influence.bone].fma(influence.weight,positions[i]);
                mass[influence.bone] += influence.weight;
            }
        }
        for (int i=0; i<pivot.length; i++) {
            if (mass[i]>0.001F) centroids[i].div(mass[i]);
        }

        // Estimated locations derived from actual weight-dominant limb bounds.
        // These are placeholders for original Epic Fight inverse bind transforms.
        float midZ=(minZ+maxZ)*0.5F;
        float torsoY = minY+(maxY-minY)*0.42F;
        pivot[0]=new Vector3f(0F,torsoY,midZ);
        pivot[1]=pivotEstimate(centroids,mass,1,0.73F, 0F);
        pivot[2]=pivotEstimate(centroids,mass,2,0.40F, 0F);
        pivot[3]=new Vector3f(pivot[2]).setComponent(1,minY+0.16F);
        pivot[4]=pivotEstimate(centroids,mass,4,0.73F, 0F);
        pivot[5]=pivotEstimate(centroids,mass,5,0.40F, 0F);
        pivot[6]=new Vector3f(pivot[5]).setComponent(1,minY+0.16F);
        pivot[7]=new Vector3f(0F,torsoY,midZ);
        pivot[8]=new Vector3f(0F,minY+(maxY-minY)*0.56F,midZ);
        pivot[9]=pivotEstimate(centroids,mass,9,minY+(maxY-minY)*0.69F,0F);
        pivot[10]=pivotEstimate(centroids,mass,11,minY+(maxY-minY)*0.63F,0F);
        pivot[11]=new Vector3f(pivot[10]);
        pivot[12]=pivotEstimate(centroids,mass,12,minY+(maxY-minY)*0.48F,0F);
        pivot[13]=new Vector3f(pivot[12]);
        pivot[14]=new Vector3f(pivot[11]).lerp(pivot[12],0.55F);
        pivot[15]=pivotEstimate(centroids,mass,16,minY+(maxY-minY)*0.63F,0F);
        pivot[16]=new Vector3f(pivot[15]);
        pivot[17]=pivotEstimate(centroids,mass,17,minY+(maxY-minY)*0.48F,0F);
        pivot[18]=new Vector3f(pivot[17]);
        pivot[19]=new Vector3f(pivot[16]).lerp(pivot[17],0.55F);
        return pivot;
    }

    private static Vector3f pivotEstimate(Vector3f[] avg,float[] mass,int index,
                                           float height,float fallbackDepth) {
        Vector3f p = mass[index]>0.001F ? new Vector3f(avg[index]) :
            new Vector3f(0F,height,fallbackDepth);
        p.y = height;
        return p;
    }

    private record Model(Vector3f[] positions, float[][] uv,
                         Influence[][] influences, Corner[] corners,
                         Vector3f[] bindPivots, float minX,float minY,float minZ,
                         float maxX,float maxY,float maxZ) {
        float centerX(){return (minX+maxX)*0.5F;}
        float centerZ(){return (minZ+maxZ)*0.5F;}
    }
    private record Corner(int position,int uv){}
    private record Influence(int bone,float weight){}
}
