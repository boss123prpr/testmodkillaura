package aura;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.Random;

public class AuraClient implements ClientModInitializer {
    public static boolean enabled = false;
    public static boolean follow = true;
    public static double reach = 3.0;
    public static double loseRange = 16.0;  // focus puszcza typa dopiero powyzej tej odleglosci
    public static double aimRange = 4.1;    // kamera celuje dopiero od tej odleglosci (kratki)
    public static double hitChance = 0.90;
    public static double rotSpeed = 0.45;   // 0..1 - jak szybko kamera dogania cel (na klatke)

    private final Random rng = new Random();
    private boolean shiftWasDown = false;
    private LivingEntity target;
    private double offY = 1.0, offX = 0, offZ = 0;
    private int reroll = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudRenderCallback.EVENT.register((ctx, tc) -> smoothAim()); // co klatke = plynna kamera
    }

    private void tick(MinecraftClient mc) {
        // prawy shift -> GUI
        boolean down = mc.getWindow() != null &&
            GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        if (down && !shiftWasDown && mc.currentScreen == null) mc.setScreen(new AuraScreen());
        shiftWasDown = down;

        if (mc.player == null || mc.world == null || !enabled || mc.currentScreen != null) {
            if (mc.options != null) { mc.options.forwardKey.setPressed(false); }
            target = null;
            return;
        }
        PlayerEntity me = mc.player;

        // FOCUS: trzymaj tego samego typa, dopoki zyje i jest w zasiegu (loseRange)
        if (target != null && (!target.isAlive() || target.isRemoved() || me.distanceTo(target) > loseRange)) {
            target = null;
        }
        if (target == null) {
            target = mc.world.getEntitiesByClass(LivingEntity.class, me.getBoundingBox().expand(8),
                    e -> e != me && e.isAlive() && e instanceof PlayerEntity)
                .stream().min(Comparator.comparingDouble(me::squaredDistanceTo)).orElse(null);
        }

        if (target == null) { mc.options.forwardKey.setPressed(false); return; }

        // co ~8 tickow losuj nowy punkt na ciele, zeby nie bic ciagle w to samo miejsce
        if (--reroll <= 0) {
            reroll = 5 + rng.nextInt(8);
            offY = 0.25 + rng.nextDouble() * (target.getHeight() - 0.45);
            offX = (rng.nextDouble() - 0.5) * target.getWidth() * 0.7;
            offZ = (rng.nextDouble() - 0.5) * target.getWidth() * 0.7;
        }

        double dist = me.distanceTo(target);
        if (follow) {
            mc.options.forwardKey.setPressed(dist > 2.0);
            me.setSprinting(dist > 2.0);
        }

        if (dist <= reach && me.getAttackCooldownProgress(0.5f) >= 0.92f) {
            if (rng.nextDouble() < hitChance) {
                mc.interactionManager.attackEntity(me, target);
            }
            me.swingHand(Hand.MAIN_HAND);
        }
    }

    private void smoothAim() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || target == null || mc.player == null || mc.currentScreen != null) return;
        if (mc.player.distanceTo(target) > aimRange) return;
        Vec3d eye = mc.player.getEyePos();
        Vec3d aim = target.getPos().add(offX, offY, offZ);
        double dx = aim.x - eye.x, dy = aim.y - eye.y, dz = aim.z - eye.z;
        float ty = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float tp = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        float dyaw = MathHelper.wrapDegrees(ty - mc.player.getYaw());
        float dpit = tp - mc.player.getPitch();
        mc.player.setYaw(mc.player.getYaw() + dyaw * (float) rotSpeed);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + dpit * (float) rotSpeed, -90f, 90f));
    }
}
