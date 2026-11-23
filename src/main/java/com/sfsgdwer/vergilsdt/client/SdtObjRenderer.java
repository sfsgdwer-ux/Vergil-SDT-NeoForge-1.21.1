package com.sfsgdwer.vergilsdt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Experimental SDT OBJ renderer with a shared hierarchy of pivots.
 * It is NOT the original Epic Fight weighted/skinned mesh renderer.
 */
final class SdtObjRenderer {
    private static final ResourceLocation OBJ =
        ResourceLocation.fromNamespaceAndPath("vergilsdt", "sdt_data/models/obj/sdt.obj");
    private static final ResourceLocation SKIN =
        ResourceLocation.fromNamespaceAndPath("vergilsdt", "sdt_data/textures/models/obj/sdt.png");

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

            float scale = Mth.clamp(2.35F / Math.max(0.1F, model.maxY - model.minY),
                0.40F, 1.10F);
            stack.scale(scale, scale, scale);
            stack.translate(-model.centerX, -model.minY, -model.centerZ);

            if (event.getEntity().isCrouching()) stack.translate(0D, -0.10D, 0D);

            SdtAnimationClip original = clip == null ? null : SdtAnimationClip.load(clip);
            // Use Minecraft's interpolated walk cycle, not an arbitrary tick
            // clock. This keeps each leg/arm swing aligned with actual steps
            // and naturally fades back to rest when movement stops.
            float speed = (float) Math.sqrt(event.getEntity()
                .getDeltaMovement().horizontalDistanceSqr());
            boolean sprinting = event.getEntity().isSprinting();
            boolean crouching = event.getEntity().isCrouching();
            boolean gliding = event.getEntity().isFallFlying();
            boolean swimming = event.getEntity().isSwimming();
            boolean airborne = !event.getEntity().onGround() && !gliding && !swimming;
            float walkSpeed = event.getEntity().walkAnimation.speed(partial);
            float stride = original != null || event.getEntity().isPassenger()
                ? 0F : Mth.clamp(Math.max(walkSpeed * 2F,
                    speed * (sprinting ? 7F : 11F)), 0F, 1F);
            float phase = event.getEntity().walkAnimation.position(partial) * 0.6662F;
            float breathing = Mth.sin((event.getEntity().tickCount + partial) * 0.11F);
            // Bone poses must be sampled once per frame, not once per OBJ part:
            // there are hundreds of pieces and all pieces on a bone must share
            // the exact same animation.
            Map<String, SdtAnimationClip.Pose> clipPoses = new HashMap<>();
            Map<String, LimbAngles> limbPoses = new HashMap<>();

            if (original == null) {
                stack.translate(0D, breathing * 0.009F +
                    Math.abs(Mth.sin(phase)) * 0.022F * stride, 0D);
            }

            VertexConsumer sink = event.getMultiBufferSource()
                .getBuffer(RenderType.entityCutoutNoCull(SKIN));

            // Every separate OBJ object receives its own bone association.
            // All parts use the SAME joint pivots and ancestor chain.
            for (Part part : model.parts) {
                stack.pushPose();
                try {
                    for (String bone : model.rig.chain(part.bone)) {
                        Pivot joint = model.rig.pivot(bone);
                        if (original != null) {
                            SdtAnimationClip.Pose pose = clipPoses.computeIfAbsent(
                                bone, key -> original.sample(key, seconds));
                            rotateAround(stack, joint, pose.rotation());
                            if (bone.equals("Root")) {
                                stack.translate(pose.dx(), pose.dy(), pose.dz());
                            }
                        } else {
                            LimbAngles pose = limbPoses.computeIfAbsent(bone,
                                key -> proceduralPose(key, phase, stride, breathing,
                                    sprinting, crouching, airborne, gliding, swimming));
                            if (Math.abs(pose.x) > 0.001F || Math.abs(pose.z) > 0.001F) {
                                stack.translate(joint.x, joint.y, joint.z);
                                if (Math.abs(pose.z) > 0.001F)
                                    stack.mulPose(Axis.ZP.rotationDegrees(pose.z));
                                if (Math.abs(pose.x) > 0.001F)
                                    stack.mulPose(Axis.XP.rotationDegrees(pose.x));
                                stack.translate(-joint.x, -joint.y, -joint.z);
                            }
                        }
                    }

                    PoseStack.Pose pose = stack.last();
                    for (Face face : part.faces) {
                        for (Point v : face.vertices) {
                            sink.addVertex(pose.pose(), v.x, v.y, v.z)
                                .setColor(255, 255, 255, 255)
                                .setUv(v.u, v.v)
                                .setOverlay(OverlayTexture.NO_OVERLAY)
                                .setLight(event.getPackedLight())
                                .setNormal(pose, face.nx, face.ny, face.nz);
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

    /**
     * Small cosmetic animations on the full segmented OBJ rig.
     * X rotates limbs forward/backward; Z gives shoulders a subtle side sway.
     * Both arms and both legs have opposite phases; elbows and knees flex,
     * and idle breathing prevents the model from looking frozen.
     * The geometry keeps the same underlying body and remains cosmetic only.
     */
    private static LimbAngles proceduralPose(String bone, float phase, float stride,
                                             float breath, boolean sprinting,
                                             boolean crouching, boolean airborne,
                                             boolean gliding, boolean swimming) {
        float pulse = Mth.sin(phase);
        float walk = stride * (sprinting ? 52F : crouching ? 21F : 36F);
        float swing = pulse * walk;
        float idle = breath * (1F - stride);
        float rightKnee = Math.max(0F, -pulse) * stride * (sprinting ? 28F : 17F);
        float leftKnee = Math.max(0F, pulse) * stride * (sprinting ? 28F : 17F);
        float elbowFlex = sprinting ? 22F : crouching ? 13F : 7F;

        if (gliding) {
            return switch (bone) {
                case "Arm_R", "Arm_L" -> new LimbAngles(-74F, 0F);
                case "Elbow_R", "Elbow_L" -> new LimbAngles(-9F, 0F);
                case "Thigh_R", "Thigh_L" -> new LimbAngles(13F, 0F);
                case "Leg_R", "Leg_L" -> new LimbAngles(5F, 0F);
                case "Chest" -> new LimbAngles(-10F, 0F);
                default -> LimbAngles.ZERO;
            };
        }

        if (swimming) {
            float paddle = Mth.sin(phase * 1.8F) * 14F;
            return switch (bone) {
                case "Arm_R" -> new LimbAngles(-48F + paddle, -5F);
                case "Arm_L" -> new LimbAngles(-48F - paddle, 5F);
                case "Elbow_R", "Elbow_L" -> new LimbAngles(-15F, 0F);
                case "Thigh_R" -> new LimbAngles(12F + paddle * 0.45F, 0F);
                case "Thigh_L" -> new LimbAngles(12F - paddle * 0.45F, 0F);
                case "Chest" -> new LimbAngles(-7F, 0F);
                default -> LimbAngles.ZERO;
            };
        }

        if (airborne) {
            return switch (bone) {
                case "Arm_R" -> new LimbAngles(-18F, -3F);
                case "Arm_L" -> new LimbAngles(-18F, 3F);
                case "Elbow_R", "Elbow_L" -> new LimbAngles(-12F, 0F);
                case "Thigh_R" -> new LimbAngles(-12F, 0F);
                case "Thigh_L" -> new LimbAngles(8F, 0F);
                case "Leg_R", "Leg_L" -> new LimbAngles(13F, 0F);
                default -> LimbAngles.ZERO;
            };
        }

        return switch (bone) {
            case "Arm_R" -> new LimbAngles(-swing * 0.90F - 2F + idle * 2.4F,
                -3F - 2F * stride);
            case "Arm_L" -> new LimbAngles(swing * 0.90F - 2F - idle * 2.4F,
                3F + 2F * stride);
            case "Elbow_R" -> new LimbAngles(-elbowFlex - rightKnee * 0.60F, 0F);
            case "Elbow_L" -> new LimbAngles(-elbowFlex - leftKnee * 0.60F, 0F);
            case "Hand_R" -> new LimbAngles(breath * 2.2F, 0F);
            case "Hand_L" -> new LimbAngles(-breath * 2.2F, 0F);
            case "Thigh_R" -> new LimbAngles(swing, 0F);
            case "Thigh_L" -> new LimbAngles(-swing, 0F);
            case "Leg_R" -> new LimbAngles(rightKnee + (crouching ? 8F : 0F), 0F);
            case "Leg_L" -> new LimbAngles(leftKnee + (crouching ? 8F : 0F), 0F);
            case "Knee_R", "Knee_L" -> new LimbAngles(-4F * stride, 0F);
            case "Head" -> new LimbAngles(breath * 1.2F, 0F);
            case "Chest" -> new LimbAngles(breath * 1.0F - (sprinting ? 4F : 0F), 0F);
            case "Torso" -> new LimbAngles(crouching ? 8F : 0F,
                Mth.sin(phase * 2F) * 1.5F * stride);
            default -> LimbAngles.ZERO;
        };
    }

    private record LimbAngles(float x, float z) {
        static final LimbAngles ZERO = new LimbAngles(0F, 0F);
    }

    private static void rotateAround(PoseStack stack, Pivot p, Quaternionf rotation) {
        stack.translate(p.x, p.y, p.z);
        stack.mulPose(rotation);
        stack.translate(-p.x, -p.y, -p.z);
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
                cachedModel = Model.EMPTY;
            }
        }
        return cachedModel;
    }

    private static Model parse(BufferedReader reader) throws IOException {
        List<float[]> xyz = new ArrayList<>();
        List<float[]> uv = new ArrayList<>();
        List<PartBuilder> builders = new ArrayList<>();
        PartBuilder current = null;

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY,
            minZ = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY,
            maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

        String line;
        while ((line = reader.readLine()) != null) {
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("#")) continue;
            String[] tokens = s.split("\\s+");
            if (tokens.length == 0) continue;
            try {
                switch (tokens[0]) {
                    case "o" -> {
                        // A new OBJ object, even with the same name, has its own bounds/pivot.
                        current = new PartBuilder(tokens.length > 1 ? tokens[1] : "cube");
                        builders.add(current);
                    }
                    case "g" -> {
                        // An OBJ group can be a label without a new object; do not merge 'cube's.
                        if (current == null) {
                            current = new PartBuilder(tokens.length > 1 ? tokens[1] : "cube");
                            builders.add(current);
                        }
                    }
                    case "v" -> {
                        if (tokens.length < 4) break;
                        float x = Float.parseFloat(tokens[1]);
                        float y = Float.parseFloat(tokens[2]);
                        float z = Float.parseFloat(tokens[3]);
                        xyz.add(new float[]{x, y, z});
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                        minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                    }
                    case "vt" -> {
                        if (tokens.length < 3) break;
                        uv.add(new float[]{
                            Float.parseFloat(tokens[1]),
                            1F - Float.parseFloat(tokens[2])
                        });
                    }
                    case "f" -> {
                        if (tokens.length < 4) break;
                        if (current == null) {
                            current = new PartBuilder("unnamed");
                            builders.add(current);
                        }
                        Point[] points = new Point[tokens.length - 1];
                        for (int i = 1; i < tokens.length; i++) {
                            String[] indices = tokens[i].split("/", -1);
                            float[] p = xyz.get(index(indices[0], xyz.size()));
                            float[] t = {0F, 0F};
                            if (indices.length > 1 && !indices[1].isBlank()) {
                                t = uv.get(index(indices[1], uv.size()));
                            }
                            points[i-1] = new Point(p[0],p[1],p[2],t[0],t[1]);
                        }
                        if (points.length == 4) {
                            current.add(face(points[0],points[1],points[2],points[3]));
                        } else {
                            for (int i=1; i+1<points.length; i++) {
                                // Degenerate quad for the Minecraft quad RenderType.
                                current.add(face(points[0],points[i],points[i+1],points[i+1]));
                            }
                        }
                    }
                    default -> {}
                }
            } catch (NumberFormatException | IndexOutOfBoundsException malformed) {
                // Skip a malformed face; the rest of the SDT model is still renderable.
            }
        }

        if (xyz.isEmpty()) return Model.EMPTY;

        List<Part> parts = new ArrayList<>();
        for (PartBuilder builder : builders) {
            if (!builder.faces.isEmpty()) parts.add(builder.finish());
        }
        Rig rig = new Rig(parts, minX, minY, minZ, maxX, maxY, maxZ);
        List<Part> assigned = new ArrayList<>(parts.size());
        for (Part part : parts) {
            assigned.add(new Part(part.name, rig.chooseBone(part), part.faces,
                part.minX,part.minY,part.minZ,part.maxX,part.maxY,part.maxZ));
        }
        return new Model(List.copyOf(assigned), minY, maxY,
            (minX + maxX) * 0.5F, (minZ + maxZ) * 0.5F, rig);
    }

    private static int index(String token, int count) {
        int i = Integer.parseInt(token);
        return i > 0 ? i-1 : count+i;
    }

    private static Face face(Point a, Point b, Point c, Point d) {
        float ux=b.x-a.x, uy=b.y-a.y, uz=b.z-a.z;
        float vx=c.x-a.x, vy=c.y-a.y, vz=c.z-a.z;
        float nx=uy*vz-uz*vy, ny=uz*vx-ux*vz, nz=ux*vy-uy*vx;
        float size=Mth.sqrt(nx*nx+ny*ny+nz*nz);
        if (size>0.0001F){nx/=size;ny/=size;nz/=size;}
        else{nx=0;ny=1;nz=0;}
        return new Face(new Point[]{a,b,c,d},nx,ny,nz);
    }

    private static final class PartBuilder {
        final String name;
        final List<Face> faces = new ArrayList<>();
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,minZ=Float.POSITIVE_INFINITY;
        float maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
        PartBuilder(String name){this.name=name;}
        void add(Face f){
            faces.add(f);
            for(Point p:f.vertices){
                minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);minZ=Math.min(minZ,p.z);
                maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);maxZ=Math.max(maxZ,p.z);
            }
        }
        Part finish(){return new Part(name,"Root",List.copyOf(faces),
            minX,minY,minZ,maxX,maxY,maxZ);}
    }

    private static final class Rig {
        private final Map<String,Pivot> pivots = new HashMap<>();
        private final Map<String,String> parents = new HashMap<>();

        Rig(List<Part> parts,float minX,float minY,float minZ,float maxX,float maxY,float maxZ){
            float centerX=(minX+maxX)*0.5F, centerZ=(minZ+maxZ)*0.5F;
            float height=maxY-minY;
            Pivot root=new Pivot(centerX,minY+height*0.47F,centerZ);
            pivots.put("Root",root);
            parents.put("Root",null);
            put("Torso","Root",find(parts,"UpBody",false,new Pivot(centerX,minY+height*0.48F,centerZ)));
            put("Chest","Torso",find(parts,"UpperBody",false,new Pivot(centerX,minY+height*0.67F,centerZ)));
            put("Head","Chest",find(parts,"Head",true,new Pivot(centerX,minY+height*0.81F,centerZ)));
            put("Shoulder_R","Chest",find(parts,"RightArm",false,pivots.get("Chest")));
            put("Arm_R","Shoulder_R",pivots.get("Shoulder_R"));
            put("Elbow_R","Arm_R",find(parts,"RightForeArm",false,pivots.get("Shoulder_R")));
            put("Hand_R","Elbow_R",find(parts,"RightHand",false,pivots.get("Elbow_R")));
            put("Shoulder_L","Chest",find(parts,"LeftArm",false,pivots.get("Chest")));
            put("Arm_L","Shoulder_L",pivots.get("Shoulder_L"));
            put("Elbow_L","Arm_L",find(parts,"LeftForeArm",false,pivots.get("Shoulder_L")));
            put("Hand_L","Elbow_L",find(parts,"LeftHand",false,pivots.get("Elbow_L")));
            put("Thigh_R","Root",find(parts,"RightLeg",false,root));
            put("Leg_R","Thigh_R",find(parts,"RightLowerLeg",false,pivots.get("Thigh_R")));
            put("Knee_R","Leg_R",find(parts,"RightFoot",false,pivots.get("Leg_R")));
            put("Thigh_L","Root",find(parts,"LeftLeg",false,root));
            put("Leg_L","Thigh_L",find(parts,"LeftLowerLeg",false,pivots.get("Thigh_L")));
            put("Knee_L","Leg_L",find(parts,"LeftFoot",false,pivots.get("Leg_L")));
        }

        private void put(String name,String parent,Pivot p){
            pivots.put(name,p);
            parents.put(name,parent);
        }

        private static Pivot find(List<Part> parts,String name,boolean bottom,Pivot fallback){
            float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,minZ=Float.POSITIVE_INFINITY;
            float maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
            for(Part p:parts){
                if(!p.name.equals(name))continue;
                minX=Math.min(minX,p.minX);minY=Math.min(minY,p.minY);
                minZ=Math.min(minZ,p.minZ);
                maxX=Math.max(maxX,p.maxX);maxY=Math.max(maxY,p.maxY);
                maxZ=Math.max(maxZ,p.maxZ);
            }
            return minX==Float.POSITIVE_INFINITY?fallback:
                new Pivot((minX+maxX)*0.5F,bottom?minY:maxY,(minZ+maxZ)*0.5F);
        }

        String chooseBone(Part p){
            String assigned=switch(p.name){
                case "Head","AllHead"->"Head";
                case "UpperBody"->"Chest";
                case "UpBody"->"Torso";
                case "RightArm"->"Arm_R";
                case "RightForeArm"->"Elbow_R";
                case "RightHand"->"Hand_R";
                case "LeftArm"->"Arm_L";
                case "LeftForeArm"->"Elbow_L";
                case "LeftHand"->"Hand_L";
                case "RightLeg"->"Thigh_R";
                case "RightLowerLeg"->"Leg_R";
                case "RightFoot"->"Knee_R";
                case "LeftLeg"->"Thigh_L";
                case "LeftLowerLeg"->"Leg_L";
                case "LeftFoot"->"Knee_L";
                default->null;
            };
            if(assigned!=null)return assigned;

            // Original OBJ contains hundreds of separate unnamed cube objects.
            // Associate each with the nearest major body-region pivot.
            float cx=(p.minX+p.maxX)*0.5F;
            float cy=(p.minY+p.maxY)*0.5F;
            float cz=(p.minZ+p.maxZ)*0.5F;
            if(Math.abs(cx)>1.10F)return "Chest"; // wings/side accessories
            String best="Torso";
            float bestDistance=Float.POSITIVE_INFINITY;
            for(String bone:new String[]{"Head","Chest","Torso","Arm_R","Arm_L",
                "Elbow_R","Elbow_L","Hand_R","Hand_L","Thigh_R","Thigh_L",
                "Leg_R","Leg_L","Knee_R","Knee_L"}){
                Pivot pivot=pivots.get(bone);
                if(pivot==null)continue;
                float dx=cx-pivot.x,dy=cy-pivot.y,dz=cz-pivot.z;
                float distance=dx*dx+dy*dy*1.15F+dz*dz;
                if(distance<bestDistance){bestDistance=distance;best=bone;}
            }
            return best;
        }

        Pivot pivot(String bone){return pivots.getOrDefault(bone,pivots.get("Root"));}

        List<String> chain(String bone) {
            List<String> reversed=new ArrayList<>();
            String node=pivots.containsKey(bone)?bone:"Root";
            int cap=0;
            while(node!=null && cap++<20){
                reversed.add(node);
                node=parents.get(node);
            }
            List<String> forward=new ArrayList<>(reversed.size());
            for(int i=reversed.size()-1;i>=0;i--)forward.add(reversed.get(i));
            return forward;
        }
    }

    private record Pivot(float x,float y,float z) {}
    private record Model(List<Part> parts,float minY,float maxY,float centerX,float centerZ,Rig rig) {
        static final Model EMPTY=new Model(List.of(),0,1,0,0,null);
    }
    private record Part(String name,String bone,List<Face> faces,
        float minX,float minY,float minZ,float maxX,float maxY,float maxZ) {}
    private record Point(float x,float y,float z,float u,float v) {}
    private record Face(Point[] vertices,float nx,float ny,float nz) {}
}
