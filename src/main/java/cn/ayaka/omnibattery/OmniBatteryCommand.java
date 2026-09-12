package cn.ayaka.omnibattery;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * /omnibattery 命令（移植自 1.20.1 原版）：
 *   /omnibattery rate <1-5>
 *   /omnibattery range <数字|all>
 *   /omnibattery mode <both|charge|absorb|off>
 * 作用于主手（或副手）的万能电池物品。
 */
public final class OmniBatteryCommand {
    private OmniBatteryCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("omnibattery")
                .then(Commands.literal("mode")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("both");
                                    builder.suggest("charge");
                                    builder.suggest("absorb");
                                    builder.suggest("off");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> setMode(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
                .then(Commands.literal("range")
                        .then(Commands.argument("value", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("16");
                                    builder.suggest("32");
                                    builder.suggest("64");
                                    builder.suggest("128");
                                    builder.suggest("256");
                                    builder.suggest("512");
                                    builder.suggest("all");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> setRange(ctx.getSource(), StringArgumentType.getString(ctx, "value")))))
                .then(Commands.literal("rate")
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                .executes(ctx -> setRate(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "level")))))
                .executes(ctx -> info(ctx.getSource())));
    }

    private static int info(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("\u4e07\u80fd\u7535\u6c60\uff1a\u624b\u6301\u7535\u6c60\u540e\u7528 /omnibattery rate 1-5\u3001/omnibattery range <\u6570\u5b57|all>\u3001/omnibattery mode <both|charge|absorb|off>")
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static ItemStack heldBattery(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof OmniBatteryItem)) {
            stack = player.getOffhandItem();
        }
        if (!(stack.getItem() instanceof OmniBatteryItem)) {
            source.sendFailure(Component.literal("\u8bf7\u5148\u628a\u4e07\u80fd\u7535\u6c60\u62ff\u5728\u624b\u4e0a\u3002"));
            return ItemStack.EMPTY;
        }
        return stack;
    }

    private static int setMode(CommandSourceStack source, String raw) throws CommandSyntaxException {
        ItemStack stack = heldBattery(source);
        if (stack.isEmpty()) return 0;
        BatteryMode mode;
        switch (raw.toLowerCase()) {
            case "both", "all", "\u53cc\u5411", "\u5438\u53d6\u4f9b\u80fd" -> mode = BatteryMode.BOTH;
            case "charge", "send", "\u4f9b\u80fd" -> mode = BatteryMode.CHARGE_ONLY;
            case "absorb", "input", "\u5438\u53d6" -> mode = BatteryMode.ABSORB_ONLY;
            case "off", "\u5173\u95ed" -> mode = BatteryMode.OFF;
            default -> mode = null;
        }
        if (mode == null) {
            source.sendFailure(Component.literal("\u6a21\u5f0f\u53ea\u80fd\u662f both / charge / absorb / off"));
            return 0;
        }
        BatteryData.setMode(stack, mode);
        source.sendSuccess(() -> Component.translatable("message.omnibattery.mode", mode.display())
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return 1;
    }

    private static int setRange(CommandSourceStack source, String raw) throws CommandSyntaxException {
        ItemStack stack = heldBattery(source);
        if (stack.isEmpty()) return 0;
        BatteryTier tier = ((OmniBatteryItem) stack.getItem()).getTier();
        int range;
        if (raw.equalsIgnoreCase("all") || raw.equals("\u5168\u7ef4\u5ea6") || raw.equals("\u5168\u90e8")) {
            if (!tier.isUltimate()) {
                source.sendFailure(Component.literal("\u53ea\u6709\u7ec8\u6781\u4e07\u80fd\u7535\u6c60\u53ef\u4ee5\u8bbe\u7f6e\u4e3a all\u3002"));
                return 0;
            }
            range = -1;
        } else {
            try {
                range = Integer.parseInt(raw);
            } catch (NumberFormatException ex) {
                source.sendFailure(Component.literal("\u8303\u56f4\u8bf7\u8f93\u5165\u6570\u5b57\u6216 all\u3002"));
                return 0;
            }
        }
        BatteryData.setRange(stack, tier, range);
        final int r = range;
        source.sendSuccess(() -> Component.translatable("message.omnibattery.range",
                        r < 0 ? "\u5f53\u524d\u7ef4\u5ea6\u5168\u90e8\u5df2\u52a0\u8f7d\u533a\u5757" : r + " \u683c")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int setRate(CommandSourceStack source, int level) throws CommandSyntaxException {
        ItemStack stack = heldBattery(source);
        if (stack.isEmpty()) return 0;
        BatteryData.setRateIndex(stack, level - 1);
        BatteryTier tier = ((OmniBatteryItem) stack.getItem()).getTier();
        int rate = tier.rate(level - 1);
        source.sendSuccess(() -> Component.translatable("message.omnibattery.rate", String.valueOf(rate))
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}