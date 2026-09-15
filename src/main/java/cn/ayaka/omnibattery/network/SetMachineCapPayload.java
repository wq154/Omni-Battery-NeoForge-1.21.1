package cn.ayaka.omnibattery.network;

import cn.ayaka.omnibattery.OmniBatteryBlockEntity;
import cn.ayaka.omnibattery.OmniBatteryMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：设置某台机器的"自定义"容量上限（在电池 GUI 的用电配置里输入）。
 */
public record SetMachineCapPayload(int x, int y, int z, long value) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetMachineCapPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(OmniBatteryMod.MOD_ID, "set_machine_cap"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetMachineCapPayload> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {
                buf.writeInt(msg.x); buf.writeInt(msg.y); buf.writeInt(msg.z); buf.writeLong(msg.value);
            }, buf -> new SetMachineCapPayload(buf.readInt(), buf.readInt(), buf.readInt(), buf.readLong()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetMachineCapPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            BlockPos pos = new BlockPos(payload.x(), payload.y(), payload.z());
            // 目标机器可能不在玩家当前维度，遍历所有维度找它
            for (ServerLevel sl : sp.server.getAllLevels()) {
                if (sl.getBlockEntity(pos) instanceof OmniBatteryBlockEntity be) {
                    if (be.setMachineCap(pos, payload.value(), sp)) {
                        sp.displayClientMessage(Component.literal(
                                        "\u81ea\u5b9a\u4e49\u5bb9\u91cf\u5df2\u8bbe\u4e3a " + payload.value() + " FE")
                                .withStyle(net.minecraft.ChatFormatting.GOLD), true);
                    }
                    return;
                }
            }
        });
    }
}
