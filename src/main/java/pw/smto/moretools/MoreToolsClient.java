package pw.smto.moretools;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import pw.smto.moretools.item.BaseToolItem;

import java.util.List;
import java.util.Objects;

@Environment(EnvType.CLIENT)
public class MoreToolsClient implements ClientModInitializer {
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public static final String VERSION = FabricLoader.getInstance().getModContainer(MoreTools.MOD_ID).get().getMetadata().getVersion().toString();
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(MoreTools.Payloads.S2CHandshake.ID, (payload, context) -> {
            // Using the context.client() autocloseable crashes minecraft clients lol?
            context.client().execute(() -> ClientPlayNetworking.send(new MoreTools.Payloads.C2SHandshakeCallbackWithVersion(MoreToolsClient.VERSION.split("\\+")[0])));
        });

        RenderStateDataKey<@NotNull List<BlockPos>> highlightedBlocks = RenderStateDataKey.create();
        RenderStateDataKey<@NotNull Vector3d> renderPos = RenderStateDataKey.create();

        WorldRenderEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register((context, hit) -> {
            BlockOutlineRenderState ors = context.worldState().blockOutlineRenderState;

            if (ors == null) return;

            ors.setData(highlightedBlocks, List.of());

            if (hit instanceof BlockHitResult blockHit) {
                if (blockHit.isInside()) return;

                // This one too... what the hell is going on?
                Player player = context.gameRenderer().getMinecraft().player;
                if(player == null) return;
                if (player.isSpectator()) return;
                if (player.isShiftKeyDown()) return;

                if (player.level().getBlockState(blockHit.getBlockPos()).isAir()) return;
                ItemStack tool = MoreToolsClient.convertPolymerStack(player.getMainHandItem());
                if(tool.isEmpty()) return;
                if (tool.getItem() instanceof BaseToolItem t) {
                    var blocks = t.getAffectedArea(player.level(), blockHit.getBlockPos(), player.level().getBlockState(blockHit.getBlockPos()), blockHit.getDirection(), player.level().getBlockState(blockHit.getBlockPos()).getBlock());
                    if (blocks == null || blocks.isEmpty()) return;

                    double d0 = player.xOld + (player.getX() - player.xOld) * Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
                    double d1 = player.yOld + player.getEyeHeight() + (player.getY() - player.yOld) * Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
                    double d2 = player.zOld + (player.getZ() - player.zOld) * Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);

                    ors.setData(highlightedBlocks, blocks);
                    ors.setData(renderPos, new Vector3d(d0, d1, d2));
                }
            }
        });

        Vector3d zero = new Vector3d();

        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, renderState) -> {
            var blocks = renderState.getDataOrDefault(highlightedBlocks, List.of());

            if (!blocks.isEmpty()) {
                Vector3d pos = renderState.getDataOrDefault(renderPos, zero);

                for(BlockPos block : blocks) {
                    ShapeRenderer.renderShape(
                        context.matrices(),
                        context.consumers().getBuffer(RenderTypes.lines()),
                        Shapes.create(new AABB(block).move(-pos.x(), -pos.y(), -pos.z())),
                        1, 1, 1, 0xFFFFFF, 0.4F
                    );
                }

                return false;
            }

            return true;
        });

        WorldRenderEvents.END_MAIN.register(context -> {
            BlockOutlineRenderState renderState = context.worldState().blockOutlineRenderState;

            if (renderState == null) return;

            var blocks = renderState.getDataOrDefault(highlightedBlocks, List.of());

            if (!blocks.isEmpty()) {
                Vector3d pos = renderState.getDataOrDefault(renderPos, new Vector3d());

                for(BlockPos block : blocks) {
                    ShapeRenderer.renderShape(
                        context.matrices(),
                        context.consumers().getBuffer(RenderTypes.lines()),
                        Shapes.create(new AABB(block).move(-pos.x(), -pos.y(), -pos.z())),
                        1, 1, 1, 0xFFFFFF, 0.4F
                    );
                }
            }
        });
    }

    public static ItemStack convertPolymerStack(ItemStack stack) {
        if (stack == null) return ItemStack.EMPTY;
        if (stack.getComponents().has(DataComponents.CUSTOM_DATA)) {
            var nbt = Objects.requireNonNull(stack.get(DataComponents.CUSTOM_DATA)).copyTag();
            if (nbt.contains("$polymer:stack")) {
                nbt = nbt.getCompound("$polymer:stack").orElse(new CompoundTag());
                if (nbt.contains("id")) {
                    Identifier id = Identifier.tryParse(nbt.getString("id").orElse(""));
                    if (id != null) {
                        Item item = BuiltInRegistries.ITEM.getValue(id);
                        ItemStack newStack = item.getDefaultInstance();
                        try {
                            nbt = nbt.getCompound("components").orElse(new CompoundTag()).getCompound("minecraft:custom_data").orElse(new CompoundTag());
                        } catch (Exception ignored) {}
                        newStack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
                        return newStack;
                    }
                }
            }
        }
        return stack;
    }
}
