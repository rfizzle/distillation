package com.rfizzle.distillation.client.gui.brewing;

import com.rfizzle.distillation.batch.BatchBrew;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the batch row over a rigged stand's screen ({@code design/SPEC.md} §3): a small panel
 * hugging the top of the vanilla window, three bottle-slot cells in vanilla slot colours (the
 * bottles themselves are drawn by the menu's slots), and a steam wisp rising off the panel — the
 * heated cauldron's cue. Pure drawing over {@link BatchBrew}'s shared geometry; called from the
 * screen's background pass so slot cells sit behind their bottles.
 */
public final class BatchRowRenderer {

    private static final int PANEL_FILL = 0xFFC6C6C6;
    private static final int PANEL_BORDER = 0xFF373737;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int SLOT = 18;

    private BatchRowRenderer() {
    }

    public static void render(GuiGraphics graphics, int leftPos, int topPos, long animTick) {
        int firstX = BatchBrew.BATCH_SLOT_X[0];
        int lastX = BatchBrew.BATCH_SLOT_X[BatchBrew.BATCH_SLOT_X.length - 1];
        int rowY = BatchBrew.BATCH_SLOT_Y;

        int x1 = leftPos + firstX - 5;
        int x2 = leftPos + lastX + SLOT + 5;
        int y1 = topPos + rowY - 6;
        int y2 = topPos + rowY + SLOT + 1;
        graphics.fill(x1, y1, x2, y2, PANEL_BORDER);
        graphics.fill(x1 + 1, y1 + 1, x2 - 1, y2, PANEL_FILL);

        for (int slotX : BatchBrew.BATCH_SLOT_X) {
            int sx = leftPos + slotX;
            int sy = topPos + rowY;
            graphics.fill(sx - 1, sy - 1, sx + SLOT - 1, sy + SLOT - 1, SLOT_BORDER);
            graphics.fill(sx, sy, sx + SLOT - 2, sy + SLOT - 2, SLOT_FILL);
        }

        renderSteam(graphics, leftPos + (firstX + lastX + SLOT) / 2, y1, animTick);
    }

    /** Three translucent wisps rising off the panel's midline, alpha and height pulsing out of phase. */
    private static void renderSteam(GuiGraphics graphics, int centerX, int panelTop, long animTick) {
        for (int wisp = 0; wisp < 3; wisp++) {
            int wx = centerX - 6 + wisp * 6;
            double phase = animTick / 8.0 + wisp * 2.1;
            int alpha = (int) (70 + 55 * Math.sin(phase));
            if (alpha <= 0) {
                continue;
            }
            int color = (Math.min(alpha, 255) << 24) | 0x00FFFFFF;
            int rise = (int) (2 + 2 * Math.sin(phase + 1.0));
            graphics.fill(wx, panelTop - 3 - rise, wx + 2, panelTop - 1 - rise, color);
            graphics.fill(wx, panelTop - 6 - rise, wx + 2, panelTop - 4 - rise, color);
        }
    }
}
