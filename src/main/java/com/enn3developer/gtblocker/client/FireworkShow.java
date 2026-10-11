package com.enn3developer.gtblocker.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.particle.EntityFireworkSparkFX;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.MathHelper;

import org.jetbrains.annotations.Nullable;

import com.enn3developer.gtblocker.Config;
import com.enn3developer.gtblocker.data.Spot;

/**
 * Client-side fireworks for a reveal; no entities, nothing for the server to sync. With a party spot nearby, every
 * client plays the same show there, since the layout comes from the seed in the reveal event. Everyone else gets a
 * smaller show of their own around them: rockets if they can see the sky, low bursts if they're indoors.
 */
public final class FireworkShow {

    private static final double SPOT_RANGE = 160;
    private static final int[] PALETTE = { 0xFFD700, 0xFF4040, 0x40C0FF, 0x60FF60, 0xFF60FF, 0xFFFFFF, 0xFF9020,
        0x9060FF };

    private static final List<Rocket> PENDING = new ArrayList<>();
    private static final List<Rocket> FLYING = new ArrayList<>();
    private static int tick;

    private FireworkShow() {}

    public static void start(@Nullable Spot spot, long seed) {
        stop();
        Minecraft mc = Minecraft.getMinecraft();
        EntityClientPlayerMP player = mc.thePlayer;
        if (!Config.fireworks || mc.theWorld == null || player == null) {
            return;
        }
        Random random = new Random(seed);
        if (spot != null && spot.dim() == player.dimension
            && player.getDistanceSq(spot.x() + 0.5, spot.y(), spot.z() + 0.5) < SPOT_RANGE * SPOT_RANGE) {
            plan(random, spot.x() + 0.5, spot.y() + 1, spot.z() + 0.5, 16, 6, 7.0, 14, 24);
            return;
        }
        int x = MathHelper.floor_double(player.posX);
        int y = MathHelper.floor_double(player.boundingBox.minY);
        int z = MathHelper.floor_double(player.posZ);
        if (mc.theWorld.canBlockSeeTheSky(x, y, z)) {
            plan(random, player.posX, player.boundingBox.minY, player.posZ, 7, 9, 9.0, 12, 18);
        } else {
            // Indoors: small pops around head height, rockets would just hit the ceiling.
            plan(random, player.posX, player.posY, player.posZ, 5, 10, 4.0, 1, 2);
        }
    }

    public static void stop() {
        PENDING.clear();
        FLYING.clear();
        tick = 0;
    }

    private static void plan(Random random, double cx, double cy, double cz, int count, int interval, double radius,
        int minHeight, int maxHeight) {
        for (int i = 0; i < count; i++) {
            boolean finale = i == count - 1;
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = radius * (0.3 + 0.7 * random.nextDouble());
            double x = cx + Math.cos(angle) * distance;
            double z = cz + Math.sin(angle) * distance;
            double apex = cy + minHeight + random.nextInt(maxHeight - minHeight + 1) + (finale ? 3 : 0);
            int launch = i * interval + random.nextInt(Math.max(1, interval / 2)) + (finale ? interval : 0);
            PENDING.add(new Rocket(launch, x, cy, z, apex, charge(random, finale, maxHeight > 2)));
        }
    }

    /** Client tick: launches due rockets, moves the flying ones and bursts them at their apex. */
    static void tick() {
        if (PENDING.isEmpty() && FLYING.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.theWorld;
        if (world == null) {
            stop();
            return;
        }
        for (Iterator<Rocket> it = PENDING.iterator(); it.hasNext();) {
            Rocket rocket = it.next();
            if (rocket.launchTick <= tick) {
                it.remove();
                if (rocket.apex - rocket.y > 3) {
                    world.playSound(rocket.x, rocket.y, rocket.z, "fireworks.launch", 3.0f, 1.0f, false);
                }
                FLYING.add(rocket);
            }
        }
        for (Iterator<Rocket> it = FLYING.iterator(); it.hasNext();) {
            Rocket rocket = it.next();
            rocket.y += rocket.speed;
            rocket.speed = Math.min(1.6, rocket.speed * 1.08 + 0.04);
            if (rocket.y >= rocket.apex) {
                it.remove();
                world.makeFireworks(rocket.x, rocket.apex, rocket.z, 0, 0, 0, rocket.charge);
                continue;
            }
            EntityFireworkSparkFX spark = new EntityFireworkSparkFX(
                world,
                rocket.x,
                rocket.y - 0.3,
                rocket.z,
                world.rand.nextGaussian() * 0.05,
                -rocket.speed * 0.5,
                world.rand.nextGaussian() * 0.05,
                mc.effectRenderer);
            // Added directly: World.spawnParticle drops particles more than 16 blocks from the camera.
            mc.effectRenderer.addEffect(spark);
        }
        tick++;
    }

    /** The "Fireworks" compound vanilla rockets carry: one or more explosions. */
    private static NBTTagCompound charge(Random random, boolean finale, boolean outdoors) {
        NBTTagList explosions = new NBTTagList();
        int bursts = finale ? 3 : 1;
        for (int i = 0; i < bursts; i++) {
            NBTTagCompound explosion = new NBTTagCompound();
            int type;
            if (!outdoors) {
                type = 0;
            } else if (finale) {
                type = i == 0 ? 1 : random.nextInt(2) == 0 ? 2 : 4;
            } else {
                type = new int[] { 0, 1, 1, 2, 4 }[random.nextInt(5)];
            }
            explosion.setByte("Type", (byte) type);
            explosion.setBoolean("Flicker", finale || random.nextInt(3) == 0);
            explosion.setBoolean("Trail", outdoors && random.nextInt(3) == 0);
            explosion.setIntArray("Colors", colors(random, 1 + random.nextInt(3)));
            explosion.setIntArray("FadeColors", colors(random, random.nextInt(2)));
            explosions.appendTag(explosion);
        }
        NBTTagCompound charge = new NBTTagCompound();
        charge.setTag("Explosions", explosions);
        return charge;
    }

    private static int[] colors(Random random, int count) {
        int[] colors = new int[count];
        for (int i = 0; i < count; i++) {
            colors[i] = PALETTE[random.nextInt(PALETTE.length)];
        }
        return colors;
    }

    private static final class Rocket {

        final int launchTick;
        final double x;
        final double z;
        final double apex;
        final NBTTagCompound charge;
        double y;
        double speed = 0.5;

        Rocket(int launchTick, double x, double y, double z, double apex, NBTTagCompound charge) {
            this.launchTick = launchTick;
            this.x = x;
            this.y = y;
            this.z = z;
            this.apex = apex;
            this.charge = charge;
        }
    }
}
