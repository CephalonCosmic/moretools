package pw.smto.moretools.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.utils.PolymerClientDecoded;
import eu.pb4.polymer.core.api.utils.PolymerKeepModel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import pw.smto.moretools.MoreTools;
import pw.smto.moretools.util.BlockBoxUtils;
import pw.smto.moretools.util.CustomMaterial;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.List;

public class VeinExcavatorToolItem extends BaseToolItem implements PolymerItem, PolymerKeepModel, PolymerClientDecoded {
    private final Item baseItem;
    private final int range;

    private static Properties createSettings(ToolMaterial baseMaterial) {
        var settings = new Properties()
                .shovel(CustomMaterial.of(baseMaterial).multiplyDurability(3).toVanilla(), Math.max(baseMaterial.attackDamageBonus()-4, 1.0F), -3.0f)
                .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.moretools.vein_excavator.tooltip").withStyle(ChatFormatting.GOLD))));
        if (baseMaterial.equals(ToolMaterial.NETHERITE)) settings.fireResistant();
        return settings;
    }


    public VeinExcavatorToolItem(ShovelItem base, ToolMaterial baseMaterial, int range) {
        super(base, VeinExcavatorToolItem.createSettings(baseMaterial), Identifier.fromNamespaceAndPath(MoreTools.MOD_ID, BuiltInRegistries.ITEM.getKey(base).getPath().replace("shovel", "vein_excavator")), baseMaterial, BlockTags.MINEABLE_WITH_SHOVEL);
        this.baseItem = base;
        this.range = range;
    }

    public VeinExcavatorToolItem(ShovelItem base, ToolMaterial baseMaterial) {
        this(base, baseMaterial, 3);
    }

    @Override
    public List<Component> getLore() {
        return List.of(Component.translatable("item.moretools.vein_excavator.tooltip").withStyle(ChatFormatting.GOLD));
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
        if (MoreTools.PLAYERS_WITH_CLIENT.contains(context.getPlayer())) {
            return this;
        }
        return this.baseItem;
    }


    @Override
    public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
        return super.id;
    }

    @Override
    public List<BlockPos> getAffectedArea(@Nullable Level world, BlockPos pos, BlockState state, @Nullable Direction d, @Nullable Block target) {
        var list = new ArrayList<BlockPos>();
        int range = 3;
        BlockState targetState = null;
        if (target != null) targetState = target.defaultBlockState();
        if (targetState == null) return list;
        boolean useVanillaDirections = true;
        if (targetState.is(MoreTools.BlockTags.VEIN_EXCAVATOR_APPLICABLE)) {
            range = this.range;
            useVanillaDirections = false;
        }

        List<BlockPos> result;
        if (useVanillaDirections) {
            result = BlockBoxUtils.getBlockCluster(target, pos, world, range, BlockBoxUtils.DirectionSets.CARDINAL);
        } else result = BlockBoxUtils.getBlockCluster(target, pos, world, range, BlockBoxUtils.DirectionSets.EXTENDED);

        if (world != null) {
            for (BlockPos blockPos : result) {
                if (world.getBlockState(blockPos).is(BlockTags.MINEABLE_WITH_SHOVEL)) {
                    list.add(blockPos);
                }
            }
        }
        return list;
    }

    public void doToolPower(BlockState state, BlockPos pos, Direction d, ServerPlayer player, Level world) {
        List<BlockPos> selection = this.getAffectedArea(world, pos, state, d, state.getBlock());
        for (BlockPos blockBoxSelectionPos : selection) {
            if (!blockBoxSelectionPos.equals(pos)) {
                player.gameMode.destroyBlock(blockBoxSelectionPos);
            }
        }
    }
}
