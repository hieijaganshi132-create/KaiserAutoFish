package br.kaiser.autofish;

import br.kaiser.autofish.mixin.FishingBobberEntityAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class KaiserAutoFishClient implements ClientModInitializer {
    private static KeyBinding toggleKey;
    private static boolean enabled = false;
    private static boolean reelRequested = false;
    private static int waitTicks = 0;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.kaiserautofish.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "category.kaiserautofish"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(KaiserAutoFishClient::tick);

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (enabled && client.player != null) {
                drawContext.drawText(client.textRenderer,
                        "Kaiser AutoFish: ON (F6 para desligar)",
                        8, 8, 0x55FF55, true);
            }
        });
    }

    private static void tick(MinecraftClient client) {
        while (toggleKey.wasPressed()) {
            enabled = !enabled;
            reelRequested = false;
            waitTicks = 10;
            if (client.player != null) {
                client.player.sendMessage(Text.literal(
                        enabled ? "§a[Kaiser AutoFish] Ativado." : "§c[Kaiser AutoFish] Desativado."
                ), true);
            }
        }

        if (!enabled || client.player == null || client.world == null || client.interactionManager == null) {
            return;
        }

        Hand rodHand = findRodHand(client);
        if (rodHand == null) return;

        ItemStack rod = client.player.getStackInHand(rodHand);
        if (rod.isDamageable() && rod.getDamage() >= rod.getMaxDamage() - 1) {
            enabled = false;
            client.player.sendMessage(Text.literal("§c[Kaiser AutoFish] Vara quase quebrando. Sistema desligado."), true);
            return;
        }

        FishingBobberEntity bobber = findOwnedBobber(client);
        if (bobber != null && ((FishingBobberEntityAccessor) bobber).kaiserAutoFish$isCaughtFish()) {
            if (!reelRequested) {
                client.interactionManager.interactItem(client.player, rodHand);
                reelRequested = true;
                waitTicks = 12;
            }
            return;
        }

        if (bobber == null) {
            if (waitTicks > 0) {
                waitTicks--;
                return;
            }
            client.interactionManager.interactItem(client.player, rodHand);
            reelRequested = false;
            waitTicks = 20;
        }
    }

    private static Hand findRodHand(MinecraftClient client) {
        if (client.player.getMainHandStack().isOf(Items.FISHING_ROD)) return Hand.MAIN_HAND;
        if (client.player.getOffHandStack().isOf(Items.FISHING_ROD)) return Hand.OFF_HAND;
        return null;
    }

    private static FishingBobberEntity findOwnedBobber(MinecraftClient client) {
        return client.world.getEntitiesByClass(
                FishingBobberEntity.class,
                client.player.getBoundingBox().expand(128.0),
                bobber -> bobber.getPlayerOwner() == client.player
        ).stream().findFirst().orElse(null);
    }
}