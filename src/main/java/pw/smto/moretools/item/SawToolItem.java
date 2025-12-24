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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import pw.smto.moretools.MoreTools;
import pw.smto.moretools.util.BlockBoxUtils;
import pw.smto.moretools.util.CustomMaterial;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.List;

public class SawToolItem extends BaseToolItem implements PolymerItem, PolymerKeepModel, PolymerClientDecoded {
    private final Item baseItem;

    private static Properties createSettings(ToolMaterial baseMaterial) {
        var settings = new Properties()
                .axe(CustomMaterial.of(baseMaterial).multiplyDurability(3).toVanilla(), Math.max(baseMaterial.attackDamageBonus()-4, 1.0F), -3.0f)
                .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.moretools.saw.tooltip").withStyle(ChatFormatting.GOLD), Component.translatable("item.moretools.saw.tooltip.2").withStyle(ChatFormatting.GOLD))));
        if (baseMaterial.equals(ToolMaterial.NETHERITE)) settings.fireResistant();
        return settings;
    }

    public SawToolItem(AxeItem base, ToolMaterial baseMaterial) {
        super(base, SawToolItem.createSettings(baseMaterial), Identifier.fromNamespaceAndPath(MoreTools.MOD_ID, BuiltInRegistries.ITEM.getKey(base).getPath().replace("axe", "saw")), baseMaterial, MoreTools.BlockTags.SAW_MINEABLE);
        this.baseItem = base;
    }

    @Override
    public List<Component> getLore() {
        return List.of(Component.translatable("item.moretools.saw.tooltip").withStyle(ChatFormatting.GOLD), Component.translatable("item.moretools.saw.tooltip.2").withStyle(ChatFormatting.GOLD));
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
    public List<BlockPos> getAffectedArea(Level world, BlockPos pos, BlockState state, @Nullable Direction d, @Nullable Block target) {
        var list = new ArrayList<BlockPos>();
        if (world == null) return list;
        if (!state.is(MoreTools.BlockTags.SAW_APPLICABLE)) return list;
        list.addAll(BlockBoxUtils.getBlockCluster(target, pos, world, 30, BlockBoxUtils.DirectionSets.DOWN_RESTRICTED_EXTENDED));
        return list;
    }

    @Override
    public void doToolPower(BlockState state, BlockPos pos, Direction d, ServerPlayer player, Level world) {
        int damage = 0;
        int maxDamage = Math.abs(player.getMainHandItem().getMaxDamage() - player.getMainHandItem().getDamageValue());
        for (BlockPos blockPos : this.getAffectedArea(world, pos, state, d, state.getBlock())) {
            if (damage >= maxDamage-1) break;
            player.gameMode.destroyBlock(blockPos);
            damage++;
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null) return InteractionResult.PASS;
        if (context.getPlayer().isShiftKeyDown()) return this.baseItem.useOn(context);
        for (BlockPos blockPos : this.getAffectedArea(context.getLevel(), context.getClickedPos(), context.getLevel().getBlockState(context.getClickedPos()), context.getClickedFace(), context.getLevel().getBlockState(context.getClickedPos()).getBlock())) {
            this.baseItem.useOn(new UseOnContext(context.getLevel(), context.getPlayer(), context.getHand(), context.getItemInHand(), new BlockHitResult(context.getClickLocation(), context.getClickedFace(), blockPos, context.isInside())));
        }
        return this.baseItem.useOn(context);
    }
}
