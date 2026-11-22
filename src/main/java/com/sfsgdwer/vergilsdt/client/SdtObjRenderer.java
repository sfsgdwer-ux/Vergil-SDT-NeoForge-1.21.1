package com.sfsgdwer.vergilsdt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * First-pass in-world renderer for the original Blockbench SDT OBJ mesh.
 * This is NOT the original Epic Fight skeletal renderer: the mesh is static.
 * The player's original renderer is cancelled only after a mesh loads.
 */
final class SdtObjRenderer {
    private static final ResourceLocation MODEL =
        ResourceLocation.fromNamespaceAndPath("invincible_dmc", "models/obj/sdt.obj");
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("invincible_dmc", "textures/models/obj/sdt.png");

    private static ResourceManager cachedManager;
    private static List<Quad> cachedQuads;

    private SdtObjRenderer() {}

    static boolean render(RenderPlayerEvent.Pre event) {
        List<Quad> quads = getQuads();
        if (quads.isEmpty()) {
            return false;
        }

        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        try {
            float yaw = Mth.rotLerp(event.getPartialTick(),
                event.getEntity().yBodyRotO, event.getEntity().yBodyRot);
            stack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));

            if (event.getEntity().isCrouching()) {
                stack.translate(0.0D, -0.12D, 0.0D);
            }

            VertexConsumer vertices = event.getMultiBufferSource().getBuffer(
                RenderType.entityCutoutNoCull(TEXTURE)
            );
            PoseStack.Pose pose = stack.last();
            for (Quad face : quads) {
                for (Vertex vert : face.verts) {
                    vertices.addVertex(pose.pose(), vert.x, vert.y, vert.z)
                        .setColor(255, 255, 255, 255)
                        .setUv(vert.u, vert.v)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(event.getPackedLight())
                        .setNormal(pose, face.nx, face.ny, face.nz);
                }
            }
        } finally {
            stack.popPose();
        }
        return true;
    }

    private static List<Quad> getQuads() {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        if (manager != cachedManager || cachedQuads == null) {
            cachedManager = manager;
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(manager.open(MODEL), StandardCharsets.UTF_8)
            )) {
                cachedQuads = parseObj(reader);
            } catch (IOException | RuntimeException ex) {
                System.err.println("[Vergil SDT] Unable to load cosmetic OBJ: " + ex);
                cachedQuads = List.of();
            }
        }
        return cachedQuads;
    }

    private static List<Quad> parseObj(BufferedReader reader) throws IOException {
        List<float[]> positions = new ArrayList<>();
        List<float[]> uv = new ArrayList<>();
        List<Quad> quads = new ArrayList<>();
        String line;

        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            String[] parts = trimmed.split("\\s+");
            if (parts.length == 0) continue;
            try {
                switch (parts[0]) {
                    case "v" -> {
                        if (parts.length >= 4) {
                            positions.add(new float[] {
                                Float.parseFloat(parts[1]),
                                Float.parseFloat(parts[2]),
                                Float.parseFloat(parts[3])
                            });
                        }
                    }
                    case "vt" -> {
                        if (parts.length >= 3) {
                            uv.add(new float[] {
                                Float.parseFloat(parts[1]),
                                1F - Float.parseFloat(parts[2])
                            });
                        }
                    }
                    case "f" -> {
                        if (parts.length < 4) break;
                        Vertex[] polygon = new Vertex[parts.length - 1];
                        for (int i = 1; i < parts.length; i++) {
                            String[] ids = parts[i].split("/", -1);
                            int vi = resolveIndex(ids[0], positions.size());
                            float[] xyz = positions.get(vi);
                            float[] uvCoords = new float[] {0F, 0F};
                            if (ids.length > 1 && !ids[1].isBlank()) {
                                uvCoords = uv.get(resolveIndex(ids[1], uv.size()));
                            }
                            polygon[i - 1] = new Vertex(
                                xyz[0], xyz[1], xyz[2], uvCoords[0], uvCoords[1]
                            );
                        }

                        if (polygon.length == 4) {
                            quads.add(makeQuad(polygon[0], polygon[1], polygon[2], polygon[3]));
                        } else {
                            // RenderType.entityCutoutNoCull is QUADS, so duplicate the last
                            // vertex of a triangle to get a degenerate fourth vertex.
                            for (int i = 1; i < polygon.length - 1; i++) {
                                quads.add(makeQuad(polygon[0], polygon[i], polygon[i + 1],
                                    polygon[i + 1]));
                            }
                        }
                    }
                    default -> {
                    }
                }
            } catch (NumberFormatException | IndexOutOfBoundsException malformed) {
                // Skip a malformed polygon/vertex without crashing the render thread.
            }
        }
        return quads;
    }

    private static int resolveIndex(String token, int count) {
        int index = Integer.parseInt(token);
        return index > 0 ? index - 1 : count + index;
    }

    private static Quad makeQuad(Vertex a, Vertex b, Vertex c, Vertex d) {
        float ux = b.x - a.x, uy = b.y - a.y, uz = b.z - a.z;
        float vx = c.x - a.x, vy = c.y - a.y, vz = c.z - a.z;
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 0.0001F) {
            nx /= length;
            ny /= length;
            nz /= length;
        } else {
            nx = 0F; ny = 1F; nz = 0F;
        }
        return new Quad(new Vertex[] {a, b, c, d}, nx, ny, nz);
    }

    private record Vertex(float x, float y, float z, float u, float v) {}

    private record Quad(Vertex[] verts, float nx, float ny, float nz) {}
}
