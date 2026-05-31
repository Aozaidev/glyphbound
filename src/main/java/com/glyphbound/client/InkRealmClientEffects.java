package com.glyphbound.client;

import com.glyphbound.Glyphbound;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = Glyphbound.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class InkRealmClientEffects {
    private static final ResourceLocation INK_REALM_EFFECTS = ResourceLocation.fromNamespaceAndPath(Glyphbound.MOD_ID, "ink_realm");
    private static final DimensionSpecialEffects EFFECTS = new InkRealmEffects();

    private InkRealmClientEffects() {
    }

    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(INK_REALM_EFFECTS, EFFECTS);
    }

    private static final class InkRealmEffects extends DimensionSpecialEffects {
        private static final ResourceLocation SKY_FRONT = skyboxTexture("front");
        private static final ResourceLocation SKY_BACK = skyboxTexture("back");
        private static final ResourceLocation SKY_LEFT = skyboxTexture("left");
        private static final ResourceLocation SKY_RIGHT = skyboxTexture("right");
        private static final ResourceLocation SKY_UP = skyboxTexture("up");
        private static final ResourceLocation SKY_DOWN = skyboxTexture("down");
        private static final float SKY_SIZE = 110.0F;
        private static final int SKY_COLOR = 0xFFFFFFFF;

        private InkRealmEffects() {
            super(Float.NaN, true, SkyType.NONE, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
            return fogColor.multiply(0.92D, 0.90D, 0.86D);
        }

        @Override
        public boolean isFoggyAt(int x, int z) {
            return false;
        }

        @Override
        public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
            setupFog.run();
            if (isFoggy) {
                return true;
            }

            PoseStack poseStack = new PoseStack();
            poseStack.mulPose(modelViewMatrix);

            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            renderSkyBox(poseStack);

            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            return true;
        }

        private static ResourceLocation skyboxTexture(String face) {
            return ResourceLocation.fromNamespaceAndPath(Glyphbound.MOD_ID, "textures/environment/skybox/" + face + ".png");
        }

        private static void renderSkyBox(PoseStack poseStack) {
            float s = SKY_SIZE;

            renderFace(poseStack, SKY_FRONT,
                -s,  s, -s,
                 s,  s, -s,
                 s, -s, -s,
                -s, -s, -s);
            renderFace(poseStack, SKY_BACK,
                 s,  s,  s,
                -s,  s,  s,
                -s, -s,  s,
                 s, -s,  s);
            renderFace(poseStack, SKY_LEFT,
                -s,  s,  s,
                -s,  s, -s,
                -s, -s, -s,
                -s, -s,  s);
            renderFace(poseStack, SKY_RIGHT,
                 s,  s, -s,
                 s,  s,  s,
                 s, -s,  s,
                 s, -s, -s);
            renderFace(poseStack, SKY_UP,
                -s,  s,  s,
                 s,  s,  s,
                 s,  s, -s,
                -s,  s, -s);
            renderFace(poseStack, SKY_DOWN,
                -s, -s, -s,
                 s, -s, -s,
                 s, -s,  s,
                -s, -s,  s);
        }

        private static void renderFace(PoseStack poseStack, ResourceLocation texture,
                                       float x0, float y0, float z0,
                                       float x1, float y1, float z1,
                                       float x2, float y2, float z2,
                                       float x3, float y3, float z3) {
            RenderSystem.setShaderTexture(0, texture);

            Matrix4f matrix = poseStack.last().pose();
            BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            bufferBuilder.addVertex(matrix, x0, y0, z0).setUv(0.0F, 0.0F).setColor(SKY_COLOR);
            bufferBuilder.addVertex(matrix, x1, y1, z1).setUv(1.0F, 0.0F).setColor(SKY_COLOR);
            bufferBuilder.addVertex(matrix, x2, y2, z2).setUv(1.0F, 1.0F).setColor(SKY_COLOR);
            bufferBuilder.addVertex(matrix, x3, y3, z3).setUv(0.0F, 1.0F).setColor(SKY_COLOR);
            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        }
    }
}
