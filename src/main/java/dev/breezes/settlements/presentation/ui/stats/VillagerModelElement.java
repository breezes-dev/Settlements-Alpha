package dev.breezes.settlements.presentation.ui.stats;

import dev.breezes.settlements.infrastructure.rendering.animation.ObservationalVillagerRenderFlag;
import dev.breezes.settlements.presentation.ui.framework.BaseElement;
import dev.breezes.settlements.presentation.ui.framework.Bounds;
import dev.breezes.settlements.presentation.ui.framework.Insets;
import dev.breezes.settlements.presentation.ui.framework.SizeConstraint;
import dev.breezes.settlements.presentation.ui.framework.UITheme;
import dev.breezes.settlements.shared.annotations.functional.ClientSide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import java.util.function.IntSupplier;

/**
 * A UI element that renders a 3D villager model preview inside a bordered area.
 * <p>
 * Follows vanilla's {@code InventoryScreen.renderEntityInInventoryFollowsAngle} approach
 * with mouse-tracking rotation. Quaternion and Vector instances are cached to avoid
 * per-frame allocations.
 */
@ClientSide
class VillagerModelElement extends BaseElement {

    private static final int MAX_SIZE = 55;
    private static final float MOUSE_ROTATION_SENSITIVITY = 40.0F;
    private static final float ROTATION_SCALE_DEGREES = 20.0F;
    private static final int MODEL_AREA_INSET = 2;
    private static final int DEFAULT_ENTITY_SCALE = 20;

    private final IntSupplier entityIdSupplier;
    private final ObservationalVillagerRenderFlag observationalRenderFlag;
    private final UITheme theme;

    // Cached per-frame math objects, can be modified via .set()
    private final Quaternionf cachedPose = new Quaternionf();
    private final Quaternionf cachedCamera = new Quaternionf();
    private final Vector3f cachedTranslate = new Vector3f();

    VillagerModelElement(@Nonnull IntSupplier entityIdSupplier,
                         @Nonnull ObservationalVillagerRenderFlag observationalRenderFlag,
                         @Nonnull UITheme theme) {
        super(SizeConstraint.FILL, SizeConstraint.fixed(MAX_SIZE), Insets.NONE);
        this.entityIdSupplier = entityIdSupplier;
        this.observationalRenderFlag = observationalRenderFlag;
        this.theme = theme;
    }

    @Override
    public void measure(int availableWidth, int availableHeight) {
        int w = resolveSize(widthConstraint, availableWidth, availableWidth);
        int h = resolveSize(heightConstraint, availableHeight, MAX_SIZE);
        setMeasuredSize(w, h);
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Bounds b = bounds();

        // Background
        graphics.fill(b.x(), b.y(), b.right(), b.bottom(), theme.rowColor());

        // Border
        graphics.hLine(b.x(), b.right() - 1, b.y(), theme.borderDark());
        graphics.hLine(b.x(), b.right() - 1, b.bottom() - 1, theme.borderDark());
        graphics.vLine(b.x(), b.y(), b.bottom() - 1, theme.borderDark());
        graphics.vLine(b.right() - 1, b.y(), b.bottom() - 1, theme.borderDark());

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        Entity entity = mc.level.getEntity(entityIdSupplier.getAsInt());
        if (entity instanceof LivingEntity livingEntity) {
            renderEntityInArea(graphics, livingEntity,
                    b.x() + MODEL_AREA_INSET, b.y() + MODEL_AREA_INSET,
                    b.right() - MODEL_AREA_INSET, b.bottom() - MODEL_AREA_INSET,
                    mouseX, mouseY);
        } else {
            // Graceful degradation: placeholder
            Font font = Minecraft.getInstance().font;
            graphics.drawCenteredString(font, "?", b.x() + b.width() / 2,
                    b.y() + b.height() / 2 - 4, theme.subtleTextColor());
        }
    }

    private void renderEntityInArea(@Nonnull GuiGraphics graphics,
                                    @Nonnull LivingEntity entity,
                                    int x1, int y1, int x2, int y2,
                                    float mouseX, float mouseY) {
        float centerX = (float) (x1 + x2) / 2.0F;
        float centerY = (float) (y1 + y2) / 2.0F;
        float angleX = (float) Math.atan((centerX - mouseX) / MOUSE_ROTATION_SENSITIVITY);
        float angleY = (float) Math.atan((centerY - mouseY) / MOUSE_ROTATION_SENSITIVITY);

        // Vanilla uses rotateZ(PI) to flip the entity right-side up, plus a camera pitch quaternion
        cachedPose.identity().rotateZ((float) Math.PI);
        cachedCamera.identity().rotateX(angleY * ROTATION_SCALE_DEGREES * ((float) Math.PI / 180.0F));
        cachedPose.mul(cachedCamera);

        // Save entity rotation state
        float savedBodyYaw = entity.yBodyRot;
        float savedYaw = entity.getYRot();
        float savedPitch = entity.getXRot();
        float savedHeadRotO = entity.yHeadRotO;
        float savedHeadRot = entity.yHeadRot;

        // Apply look-at rotations
        entity.yBodyRot = 180.0F + angleX * ROTATION_SCALE_DEGREES;
        entity.setYRot(180.0F + angleX * MOUSE_ROTATION_SENSITIVITY);
        entity.setXRot(-angleY * ROTATION_SCALE_DEGREES);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();

        try {
            // Compute scale adjusted for entity size
            float entityScale = entity.getScale();
            cachedTranslate.set(0.0F, entity.getBbHeight() / 2.0F + 0.0625F * entityScale, 0.0F);
            float adjustedScale = (float) DEFAULT_ENTITY_SCALE / entityScale;

            graphics.enableScissor(x1, y1, x2, y2);
            // The preview overwrites the live villager's rotations and renders at a partial tick of 1.0, so its
            // animation must be evaluated without committing anything to the villager's own animation state
            this.observationalRenderFlag.raise();
            try {
                InventoryScreen.renderEntityInInventory(graphics, centerX, centerY, adjustedScale, cachedTranslate, cachedPose, cachedCamera, entity);
            } finally {
                this.observationalRenderFlag.lower();
            }
            graphics.disableScissor();
        } finally {
            // Restore entity rotation state even if rendering throws
            entity.yBodyRot = savedBodyYaw;
            entity.setYRot(savedYaw);
            entity.setXRot(savedPitch);
            entity.yHeadRotO = savedHeadRotO;
            entity.yHeadRot = savedHeadRot;
        }
    }

}
