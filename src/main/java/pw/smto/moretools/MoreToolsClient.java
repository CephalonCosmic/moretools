package pw.smto.moretools;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.state.OutlineRenderState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShapes;
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
            OutlineRenderState ors = context.worldState().outlineRenderState;

            if (ors == null) return;

            ors.setData(highlightedBlocks, List.of());

            if (hit instanceof BlockHitResult blockHit) {
                if (blockHit.isInsideBlock()) return;

                // This one too... what the hell is going on?
                PlayerEntity player = context.gameRenderer().getClient().player;
                if(player == null) return;
                if (player.isSpectator()) return;
                if (player.isSneaking()) return;

                if (player.getEntityWorld().getBlockState(blockHit.getBlockPos()).isAir()) return;
                ItemStack tool = MoreToolsClient.convertPolymerStack(player.getMainHandStack());
                if(tool.isEmpty()) return;
                if (tool.getItem() instanceof BaseToolItem t) {
                    var blocks = t.getAffectedArea(player.getEntityWorld(), blockHit.getBlockPos(), player.getEntityWorld().getBlockState(blockHit.getBlockPos()), blockHit.getSide(), player.getEntityWorld().getBlockState(blockHit.getBlockPos()).getBlock());
                    if (blocks == null || blocks.isEmpty()) return;

                    double d0 = player.lastRenderX + (player.getX() - player.lastRenderX) * MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true);
                    double d1 = player.lastRenderY + player.getStandingEyeHeight() + (player.getY() - player.lastRenderY) * MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true);
                    double d2 = player.lastRenderZ + (player.getZ() - player.lastRenderZ) * MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true);

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
                    VertexRendering.drawOutline(
                        context.matrices(),
                        context.consumers().getBuffer(RenderLayers.lines()),
                        VoxelShapes.cuboid(new Box(block).offset(-pos.x(), -pos.y(), -pos.z())),
                        1, 1, 1, 0xFFFFFF, 0.4F
                    );
                }

                return false;
            }

            return true;
        });

        WorldRenderEvents.END_MAIN.register(context -> {
            OutlineRenderState renderState = context.worldState().outlineRenderState;

            if (renderState == null) return;

            var blocks = renderState.getDataOrDefault(highlightedBlocks, List.of());

            if (!blocks.isEmpty()) {
                Vector3d pos = renderState.getDataOrDefault(renderPos, new Vector3d());

                for(BlockPos block : blocks) {
                    VertexRendering.drawOutline(
                        context.matrices(),
                        context.consumers().getBuffer(RenderLayers.lines()),
                        VoxelShapes.cuboid(new Box(block).offset(-pos.x(), -pos.y(), -pos.z())),
                        1, 1, 1, 0xFFFFFF, 0.4F
                    );
                }
            }
        });
    }

    public static ItemStack convertPolymerStack(ItemStack stack) {
        if (stack == null) return ItemStack.EMPTY;
        if (stack.getComponents().contains(DataComponentTypes.CUSTOM_DATA)) {
            var nbt = Objects.requireNonNull(stack.get(DataComponentTypes.CUSTOM_DATA)).copyNbt();
            if (nbt.contains("$polymer:stack")) {
                nbt = nbt.getCompound("$polymer:stack").orElse(new NbtCompound());
                if (nbt.contains("id")) {
                    Identifier id = Identifier.tryParse(nbt.getString("id").orElse(""));
                    if (id != null) {
                        Item item = Registries.ITEM.get(id);
                        ItemStack newStack = item.getDefaultStack();
                        try {
                            nbt = nbt.getCompound("components").orElse(new NbtCompound()).getCompound("minecraft:custom_data").orElse(new NbtCompound());
                        } catch (Exception ignored) {}
                        newStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
                        return newStack;
                    }
                }
            }
        }
        return stack;
    }
}
