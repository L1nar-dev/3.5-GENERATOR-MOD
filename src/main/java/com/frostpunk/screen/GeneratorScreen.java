package com.frostpunk.screen;

import com.frostpunk.FrostpunkMod;
import com.frostpunk.network.ModPackets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class GeneratorScreen extends HandledScreen<GeneratorScreenHandler> {

    private static final Identifier TEXTURE = Identifier.of(FrostpunkMod.MOD_ID, "textures/gui/generator.png");
    private static final Identifier GENERATOR_IMG = Identifier.of(FrostpunkMod.MOD_ID, "textures/gui/generator_image.png");

    // GUI size
    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 256;

    // Heat zone colors
    private static final int[] HEAT_COLORS = {
        0, // index 0 unused
        0xFFCC2200, // 1 - comfortable - red
        0xFFCC8800, // 2 - acceptable - yellow
        0xFF2288CC, // 3 - cool - light blue
        0xFF0044CC, // 4 - cold - blue
        0xFF001888, // 5 - very cold - dark blue
        0xFF440088  // 6 - freezing - purple
    };

    private static final String[] HEAT_ZONE_NAMES = {
        "", "КОМФОРТНО", "ПРИЕМЛЕМО", "ПРОХЛАДНО", "ХОЛОДНО", "ОЧ.ХОЛОДНО", "МОРОЗНО"
    };

    public GeneratorScreen(GeneratorScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = GUI_WIDTH;
        this.backgroundHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.titleX = 999; // hide default title
        this.playerInventoryTitleX = 999;

        int x = (this.width - GUI_WIDTH) / 2;
        int y = (this.height - GUI_HEIGHT) / 2;

        // Power level buttons
        for (int i = 1; i <= 4; i++) {
            final int level = i;
            String label = switch (i) {
                case 1 -> "I  MIN";
                case 2 -> "II STD";
                case 3 -> "III HIGH";
                case 4 -> "IV MAX";
                default -> "";
            };
            this.addDrawableChild(ButtonWidget.builder(Text.literal(label), btn -> {
                ModPackets.sendSetPower(level);
            }).dimensions(x + 140 + ((i - 1) % 2) * 52, y + 60 + ((i - 1) / 2) * 22, 50, 20).build());
        }

        // Boost button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("⚡ OVERDRIVE"), btn -> {
            ModPackets.sendToggleBoost();
        }).dimensions(x + 140, y + 106, 104, 18).build());

        // Radius buttons
        for (int i = 1; i <= 4; i++) {
            final int level = i;
            this.addDrawableChild(ButtonWidget.builder(Text.literal("R" + i), btn -> {
                ModPackets.sendSetRadius(level);
            }).dimensions(x + 140 + (i - 1) * 26, y + 175, 24, 14).build());
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - GUI_WIDTH) / 2;
        int y = (this.height - GUI_HEIGHT) / 2;

        // Dark background
        context.fill(x, y, x + GUI_WIDTH, y + GUI_HEIGHT, 0xFF1A1410);
        // Border
        context.fill(x, y, x + GUI_WIDTH, y + 2, 0xFF5A4020);
        context.fill(x, y + GUI_HEIGHT - 2, x + GUI_WIDTH, y + GUI_HEIGHT, 0xFF2A1A08);
        context.fill(x, y, x + 2, y + GUI_HEIGHT, 0xFF5A4020);
        context.fill(x + GUI_WIDTH - 2, y, x + GUI_WIDTH, y + GUI_HEIGHT, 0xFF2A1A08);

        // Title bar
        context.fill(x, y, x + GUI_WIDTH, y + 18, 0xFF2A1E10);
        context.fill(x, y + 18, x + GUI_WIDTH, y + 20, 0xFF5A4020);
        context.drawText(this.textRenderer, "⚙ GENERATOR CONTROL PANEL", x + 8, y + 5, 0xFFD4A855, false);

        // Status dot (blinking)
        long time = System.currentTimeMillis();
        boolean blink = (time % 2000) > 1000;
        context.fill(x + GUI_WIDTH - 14, y + 7, x + GUI_WIDTH - 8, y + 13,
            blink ? 0xFFFF4400 : 0xFF441100);

        // Left panel - generator image background
        context.fill(x + 2, y + 20, x + 132, y + GUI_HEIGHT - 2, 0xFF0F0C08);

        // Try to draw generator image
        try {
            context.drawTexture(GENERATOR_IMG, x + 2, y + 20, 0, 0, 130, GUI_HEIGHT - 22, 130, GUI_HEIGHT - 22);
        } catch (Exception e) {
            // Draw placeholder if image not found
            context.drawText(this.textRenderer, "⚙", x + 55, y + 100, 0x335A4030, false);
            context.drawText(this.textRenderer, "GENERATOR", x + 30, y + 130, 0x225A4030, false);
        }

        // Gradient overlay on image
        for (int i = 0; i < 30; i++) {
            int alpha = (int)(255 * (i / 30.0f));
            context.fill(x + 102 + i, y + 20, x + 103 + i, y + GUI_HEIGHT - 2,
                (alpha << 24) | 0x1A1410);
        }

        // Status badge on image
        context.fill(x + 4, y + GUI_HEIGHT - 30, x + 130, y + GUI_HEIGHT - 4, 0xCC0A0804);
        context.fill(x + 4, y + GUI_HEIGHT - 30, x + 130, y + GUI_HEIGHT - 28, 0xFF5A4020);
        String statusText = handler.isBroken() ? "§cBROKEN" : handler.isRunning() ? "§aACTIVE · LVL " + handler.getPowerLevel() : "§7OFFLINE";
        context.drawText(this.textRenderer, "● " + statusText.replace("§", ""), x + 8, y + GUI_HEIGHT - 24, 0xFFD4A855, false);

        // === RIGHT PANEL ===
        int rx = x + 136;

        // Outside temperature
        drawSectionLabel(context, "OUTSIDE TEMPERATURE", rx, y + 22);
        context.fill(rx, y + 30, rx + 118, y + 50, 0xFF0F0C08);
        context.fill(rx, y + 30, rx + 118, y + 31, 0xFF2A1E10);
        context.fill(rx, y + 30, rx + 119, y + 50, 0xFF2A1E10);

        int tempColor = getTempColor();
        String tempStr = (handler.getOutsideTemp() >= 0 ? "+" : "") + handler.getOutsideTemp() + "°C";
        context.drawText(this.textRenderer, tempStr, rx + 4, y + 36, tempColor, false);
        String zoneStr = HEAT_ZONE_NAMES[handler.getHeatZone()];
        context.drawText(this.textRenderer, "◈ " + zoneStr, rx + 60, y + 36, tempColor, false);

        // Power level label
        drawSectionLabel(context, "POWER LEVEL", rx, y + 52);

        // Boost button state
        boolean boostUnlocked = handler.isBoostUnlocked();
        // (button text will show locked/unlocked state)

        // Heat zones section
        drawSectionLabel(context, "HEAT ZONE", rx, y + 128);
        drawHeatMeter(context, rx, y + 137);

        // Overload bar
        drawSectionLabel(context, "OVERDRIVE OVERLOAD", rx, y + 163);
        drawOverloadBar(context, rx, y + 171, handler.getOverloadPercent());

        // Coal supply
        drawSectionLabel(context, "COAL SUPPLY", rx, y + 183);
        // Coal slot background
        context.fill(rx, y + 191, rx + 20, y + 211, 0xFF0F0C08);
        context.fill(rx, y + 191, rx + 20, y + 192, 0xFF6A5030);
        context.fill(rx, y + 191, rx + 1, y + 211, 0xFF6A5030);
        context.fill(rx + 19, y + 191, rx + 20, y + 211, 0xFF2A1A08);
        context.fill(rx, y + 210, rx + 20, y + 211, 0xFF2A1A08);

        // Coal info
        int coal = handler.getCoalCount();
        int mins = handler.getMinutesOfCoalLeft();
        context.drawText(this.textRenderer, coal + "/64", rx + 24, y + 193, 0xFFD4A855, false);
        context.fill(rx + 24, y + 202, rx + 118, y + 208, 0xFF0F0C08);
        int barWidth = (int)((coal / 64.0f) * 94);
        if (barWidth > 0) {
            context.fill(rx + 24, y + 202, rx + 24 + barWidth, y + 208, 0xFFFF8800);
        }
        context.drawText(this.textRenderer, "~" + mins + " min left", rx + 24, y + 210, 0xFF5A4030, false);

        // Radius section
        drawSectionLabel(context, "HEAT RADIUS", rx, y + 217);
        drawRadiusPips(context, rx, y + 225, handler.getRadiusLevel());

        // Bottom info row
        context.fill(rx, y + GUI_HEIGHT - 22, rx + 118, y + GUI_HEIGHT - 2, 0xFF0F0C08);
        context.fill(rx, y + GUI_HEIGHT - 23, rx + 118, y + GUI_HEIGHT - 22, 0xFF2A1E10);
        String powerStr = "PWR: " + handler.getPowerLevel();
        String overStr = "OVR: " + handler.getOverloadPercent() + "%";
        context.drawText(this.textRenderer, powerStr, rx + 4, y + GUI_HEIGHT - 16, 0xFFD4A855, false);
        context.drawText(this.textRenderer, overStr, rx + 60, y + GUI_HEIGHT - 16, 0xFFFF6644, false);
    }

    private void drawSectionLabel(DrawContext context, String label, int x, int y) {
        context.drawText(this.textRenderer, label, x, y, 0xFF5A4030, false);
        context.fill(x, y + 8, x + 118, y + 9, 0xFF2A1E10);
    }

    private void drawHeatMeter(DrawContext context, int x, int y) {
        int currentZone = handler.getHeatZone();
        int[] heights = {14, 12, 10, 8, 6, 4};
        int[] colors = {0xFFCC2200, 0xFFCC8800, 0xFF2288CC, 0xFF0044CC, 0xFF001888, 0xFF440088};

        for (int i = 0; i < 6; i++) {
            int bx = x + i * 20;
            int bh = heights[i];
            int color = colors[i];
            boolean active = (i + 1) == currentZone;
            int finalColor = active ? color : (color & 0x00FFFFFF | 0x44000000);
            context.fill(bx, y + (14 - bh), bx + 18, y + 14, finalColor);
            if (active) {
                context.fill(bx, y + (14 - bh), bx + 18, y + (15 - bh), 0xFFFFFFFF);
            }
        }
    }

    private void drawOverloadBar(DrawContext context, int x, int y) {
        int pct = handler.getOverloadPercent();
        context.fill(x, y, x + 118, y + 8, 0xFF0F0C08);
        context.fill(x, y, x + 118, y + 1, 0xFF2A1E10);

        int fillWidth = (int)((pct / 100.0f) * 116);
        if (fillWidth > 0) {
            int color = pct > 75 ? 0xFFFF0000 : pct > 50 ? 0xFFFF4400 : 0xFFFF8800;
            context.fill(x + 1, y + 1, x + 1 + fillWidth, y + 7, color);
        }

        // Danger zone
        context.fill(x + 95, y, x + 96, y + 8, 0x88FF0000);

        context.drawText(this.textRenderer, pct + "%", x + 100, y, 0xFFFF6644, false);
    }

    private void drawRadiusPips(DrawContext context, int x, int y) {
        int[] radii = {30, 45, 65, 100};
        int currentRadius = handler.getRadiusLevel();
        for (int i = 0; i < 4; i++) {
            int px = x + i * 30;
            boolean active = (i + 1) <= currentRadius;
            context.fill(px, y, px + 28, y + 6,
                active ? 0xFFD4A855 : 0xFF0F0C08);
            context.fill(px, y, px + 28, y + 1, active ? 0xFFFFCC44 : 0xFF2A1E10);
        }
        context.drawText(this.textRenderer, "LVL " + currentRadius + " · " +
            (currentRadius > 0 && currentRadius <= 4 ? new int[]{30, 45, 65, 100}[currentRadius - 1] : 0) + " blocks",
            x, y + 8, 0xFF7A6040, false);
    }

    private int getTempColor() {
        return switch (handler.getHeatZone()) {
            case 1 -> 0xFFFF4422;
            case 2 -> 0xFFFFCC22;
            case 3 -> 0xFF44AAFF;
            case 4 -> 0xFF4488FF;
            case 5 -> 0xFF2255CC;
            case 6 -> 0xFFAA44FF;
            default -> 0xFFFFFFFF;
        };
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Suppress default title rendering
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
