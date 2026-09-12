package cn.ayaka.omnibattery.client;

import cn.ayaka.omnibattery.OmniBatteryBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 电池生效范围显示（客户端，由电池设置 GUI 里的开关控制，默认关闭）。
 * 开启后常驻显示：定期扫描玩家周围已加载区块中所有已放置的电池机器，
 * 每台（终极/全维度除外）以机器为中心绘制其生效范围线框，无需准星对准。
 */
public final class RangeOverlay {
    private static final int SCAN_CHUNK_RADIUS = 12;   // 扫描半径（区块）
    private static final int REFRESH_INTERVAL = 10;    // 每 10 tick 重扫一次

    private static boolean visible;
    private static int refreshTimer;
    private static final List<Box> BOXES = new ArrayList<>();

    private record Box(int cx, int cy, int cz, int r) {}

    private RangeOverlay() {}

    public static boolean isVisible() {
        return visible;
    }

    public static void toggle() {
        visible = !visible;
        if (!visible) BOXES.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(visible
                    ? "message.omnibattery.range.show" : "message.omnibattery.range.hide"), true);
        }
    }

    public static void render(RenderLevelStageEvent e) {
        if (!visible) return;
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (++refreshTimer >= REFRESH_INTERVAL) {
            refreshTimer = 0;
            scan(mc);
        }
        if (BOXES.isEmpty()) return;

        PoseStack ps = e.getPoseStack();
        Camera cam = mc.gameRenderer.getMainCamera();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        ps.pushPose();
        ps.translate(-cam.getPosition().x, -cam.getPosition().y, -cam.getPosition().z);
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        for (Box b : BOXES) {
            LevelRenderer.renderLineBox(ps, vc,
                    b.cx() - b.r(), b.cy() - b.r(), b.cz() - b.r(),
                    b.cx() + b.r() + 1.0D, b.cy() + b.r() + 1.0D, b.cz() + b.r() + 1.0D,
                    0.3f, 1.0f, 0.45f, 0.9f);
        }
        ps.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void scan(Minecraft mc) {
        BOXES.clear();
        Level level = mc.level;
        if (mc.player == null) return;
        int ccx = mc.player.blockPosition().getX() >> 4;
        int ccz = mc.player.blockPosition().getZ() >> 4;
        for (int dx = -SCAN_CHUNK_RADIUS; dx <= SCAN_CHUNK_RADIUS; dx++) {
            for (int dz = -SCAN_CHUNK_RADIUS; dz <= SCAN_CHUNK_RADIUS; dz++) {
                LevelChunk chunk = getLoadedChunk(level, ccx + dx, ccz + dz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof OmniBatteryBlockEntity bat)) continue;
                    if (bat.getTier().isUltimate()) continue;
                    int r = bat.getRange();
                    if (r <= 0) continue;
                    BlockPos p = be.getBlockPos();
                    BOXES.add(new Box(p.getX(), p.getY(), p.getZ(), r));
                }
            }
        }
    }

    private static LevelChunk getLoadedChunk(Level level, int chunkX, int chunkZ) {
        if (level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel) {
            var chunk = clientLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
            return chunk instanceof LevelChunk lc ? lc : null;
        }
        return null;
    }
}