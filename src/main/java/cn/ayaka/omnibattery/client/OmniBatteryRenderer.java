package cn.ayaka.omnibattery.client;

import cn.ayaka.omnibattery.OmniBatteryBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * 万能电池方块实体渲染器：在电池正面显示电量百分比。
 *
 * 显示规则：
 *   0%          -> 黑色 "0%"
 *   1..25%      -> 红色
 *   26..50%     -> 黄色
 *   51..100%    -> 蓝色
 *   充电中     -> 覆盖为绿色 + 前置闪电符号 "⚡"
 *
 * 充电中的判定：客户端本地缓存每个坐标的上一次能量值 + 时间戳；
 * 若最近 1 秒内 energy 上升过（>0.001% 变化），认为在充电。
 * 不涉及网络包，纯客户端。
 */
public class OmniBatteryRenderer implements BlockEntityRenderer<OmniBatteryBlockEntity> {

    /** 每个方块坐标的充电状态缓存。 */
    private static final Map<BlockPos, ChargeSample> LAST = new HashMap<>();
    /** 认为"充电中"的能量涨幅静止判定窗口（毫秒）。 */
    private static final long CHARGE_WINDOW_MS = 1_000L;

    public OmniBatteryRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(OmniBatteryBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        long energy = be.getEnergy();
        long capacity = be.getTier().capacity();
        double ratio = capacity <= 0 ? 0.0 : Mth.clamp(energy / (double) capacity, 0.0, 1.0);
        int percent = (int) Math.round(ratio * 100.0);

        // 充电检测（客户端缓存）
        BlockPos pos = be.getBlockPos();
        long now = System.currentTimeMillis();
        boolean charging = false;
        ChargeSample prev = LAST.get(pos);
        if (prev != null) {
            if (energy > prev.energy && (now - prev.lastRiseAt) < CHARGE_WINDOW_MS) {
                charging = true;
            }
            if (energy > prev.energy) {
                prev.lastRiseAt = now;
                charging = true;
            }
            prev.energy = energy;
        } else {
            LAST.put(pos, new ChargeSample(energy, now));
        }

        int color = charging ? 0xFF35DD55 : colorFor(percent);
        String text = charging ? "\u26A1 " + percent + "%" : percent + "%";

        // 在方块四个侧面各绘制一次（面向玩家的那面才看得见，其它面被遮挡）
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            drawTextOnFace(pose, buffer, font, text, color, dir, packedLight);
        }
    }

    /** 数字颜色（非充电中）：0=黑，1-25=红，26-50=黄，51-100=蓝。 */
    private static int colorFor(int percent) {
        if (percent <= 0) return 0xFF141416;
        if (percent <= 25) return 0xFFE84040;
        if (percent <= 50) return 0xFFF0C830;
        return 0xFF4090FF;
    }

    private void drawTextOnFace(PoseStack pose, MultiBufferSource buffer, Font font,
                                String text, int color, Direction dir, int packedLight) {
        pose.pushPose();
        // 中心到方块正中心
        pose.translate(0.5, 0.5, 0.5);
        switch (dir) {
            case NORTH -> pose.mulPose(new org.joml.Quaternionf().rotateY((float) Math.PI));
            case SOUTH -> {}
            case WEST -> pose.mulPose(new org.joml.Quaternionf().rotateY((float) (Math.PI / 2)));
            case EAST -> pose.mulPose(new org.joml.Quaternionf().rotateY((float) (-Math.PI / 2)));
            default -> {}
        }
        // body 沿水平方向占 x=2..14/16，即距中心 ±(14-8)/16 = ±0.375。
        // 把字贴到 body 表面外侧一点点，避免 Z-fight。
        pose.translate(0.0, 0.0, 0.375 + 0.001);
        float scale = 1.0f / 96.0f;
        pose.scale(scale, -scale, scale);
        int w = font.width(text);
        Matrix4f mat = pose.last().pose();
        font.drawInBatch(text, -w / 2.0f, -4, color, false, mat, buffer,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /** 只在实体在附近渲染距离内才画字。 */
    @Override
    public int getViewDistance() {
        return 32;
    }

    private static final class ChargeSample {
        long energy;
        long lastRiseAt;

        ChargeSample(long energy, long lastRiseAt) {
            this.energy = energy;
            this.lastRiseAt = lastRiseAt;
        }
    }
}
