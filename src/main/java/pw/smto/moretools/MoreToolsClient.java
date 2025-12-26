package pw.smto.moretools;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.NotNull;
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

        RenderStateDataKey<@NotNull List<BlockPos>> highlightedBlocks = RenderStateDataKey.create(() -> "MoreTools Highlighted Blocks");

        WorldRenderEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register((context, hit) -> {
            BlockOutlineRenderState ors = context.worldState().blockOutlineRenderState;

            if (ors == null) return;

            ors.setData(highlightedBlocks, List.of());

            if (hit instanceof BlockHitResult blockHit) {
                if (blockHit.isInside()) return;

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

                    ors.setData(highlightedBlocks, blocks);
                }
            }
        });

        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, renderState) -> {
            var blocks = renderState.getDataOrDefault(highlightedBlocks, List.of());

            if (!blocks.isEmpty()) {
                Vec3 pos = context.worldState().cameraRenderState.pos;

                context.commandQueue().submitCustomGeometry(context.matrices(), RenderTypes.lines(), ((pose, vertexConsumer) -> {
                    for(BlockPos block : blocks) {
                        ShapeRenderer.renderShape(
                            context.matrices(),
                            vertexConsumer,
                            Shapes.block(),
                            block.getX() - pos.x, block.getY() - pos.y, block.getZ() - pos.z, 0xFFFFFFFF, 0.4F
                        );
                    }
                }));

                return false;
            }

            return true;
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
