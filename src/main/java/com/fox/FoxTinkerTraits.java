package com.fox;

import com.fox.config.FoxConfig;
import com.fox.autoattack.CapabilityOffhandCooldown;
import com.fox.autoattack.OffhandCooldownHandler;
import com.fox.handler.BlossomHandler;
import com.fox.handler.CounterHandler;
import com.fox.handler.GasMaskHandler;
import com.fox.health.LifeContractHandler;
import com.fox.network.AutoAttackNetwork;
import com.fox.traits.TraitAutoAttack;
import com.fox.traits.TraitDemigod;
import com.fox.traits.TraitTurtle;
import com.fox.traits.TraitBlossom;
import com.fox.traits.TraitLifeContract;
import com.fox.traits.TraitCounter;
import com.fox.traits.TraitGasMask;
import com.fox.traits.TraitSuperGasMask;
import com.fox.traits.TraitWeaponMaster;
import com.fox.traits.TraitMultiStrike;
import c4.conarm.lib.modifiers.ArmorModifierTrait;
import c4.conarm.lib.utils.RecipeMatchHolder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.Modifier;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;

@Mod(
        modid = FoxTinkerTraits.MODID,
        name = FoxTinkerTraits.NAME,
        version = FoxTinkerTraits.VERSION,
        dependencies = "required-after:conarm;required-after:tconstruct"
)
public class FoxTinkerTraits {

    public static final String MODID = "foxtinkertraits";
    public static final String NAME = "Fox's Tinker Traits";
    public static final String VERSION = "1.0";

    public static TraitDemigod demigod;
    public static TraitTurtle turtle;
    public static TraitBlossom blossom;
    public static TraitLifeContract lifeContract;
    public static TraitGasMask gasMask;
    public static TraitSuperGasMask superGasMask;
    public static TraitCounter counter;
    public static TraitAutoAttack autoAttack;
    public static TraitWeaponMaster weaponMaster;
    public static TraitMultiStrike multiStrike;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent evt) {
        FoxConfig.load(evt.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent evt) {
        demigod = new TraitDemigod(FoxConfig.demigodMaxLevel, FoxConfig.demigodFreeModifiers);
        turtle = new TraitTurtle(FoxConfig.turtleMaxLevel, FoxConfig.turtleFreeModifiers);
        blossom = new TraitBlossom(FoxConfig.blossomMaxLevel, FoxConfig.blossomFreeModifiers);
        lifeContract = new TraitLifeContract(FoxConfig.lifeContractMaxLevel, FoxConfig.lifeContractFreeModifiers);
        gasMask = new TraitGasMask(FoxConfig.gasMaskMaxLevel, FoxConfig.gasMaskFreeModifiers);
        superGasMask = new TraitSuperGasMask(FoxConfig.gasMaskFreeModifiers);
        counter = new TraitCounter(FoxConfig.counterMaxLevel, FoxConfig.counterFreeModifiers);
        autoAttack = new TraitAutoAttack(FoxConfig.autoAttackMaxLevel, FoxConfig.autoAttackFreeModifiers);
        weaponMaster = new TraitWeaponMaster(FoxConfig.weaponMasterMaxLevel, FoxConfig.weaponMasterFreeModifiers);
        multiStrike = new TraitMultiStrike(FoxConfig.multiStrikeMaxLevel, FoxConfig.multiStrikeFreeModifiers);
        AutoAttackNetwork.init();
        CapabilityOffhandCooldown.register();
        MinecraftForge.EVENT_BUS.register(BlossomHandler.class);
        MinecraftForge.EVENT_BUS.register(LifeContractHandler.class);
        MinecraftForge.EVENT_BUS.register(GasMaskHandler.class);
        MinecraftForge.EVENT_BUS.register(CounterHandler.class);
        MinecraftForge.EVENT_BUS.register(OffhandCooldownHandler.class);
        if (net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide().isClient()) {
            try {
                MinecraftForge.EVENT_BUS.register(
                        Class.forName("com.fox.client.AutoAttackHandler")
                );
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to register client auto attack handler", e);
            }
        }
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent evt) {
        registerItems(demigod, FoxConfig.demigodItems);
        registerItems(turtle, FoxConfig.turtleItems);
        registerItems(blossom, FoxConfig.blossomItems);
        registerItems(lifeContract, FoxConfig.lifeContractItems);
        registerItems(gasMask, FoxConfig.gasMaskItems, FoxConfig.gasMaskCoalAmount);
        registerItems(superGasMask, FoxConfig.superGasMaskItems, FoxConfig.superGasMaskPaperAmount);
        registerItems(counter, FoxConfig.counterItems);
        registerItems(autoAttack, FoxConfig.autoAttackItems);
        registerItems(weaponMaster, FoxConfig.weaponMasterItems);
        registerItems(multiStrike, FoxConfig.multiStrikeItems);
    }

    private static void registerItems(Modifier trait, String itemList) {
        registerItems(trait, itemList, 1);
    }

    private static void registerItems(Modifier trait, String itemList, int amount) {
        for (String entry : parseItems(itemList)) {
            String id = entry;
            int meta = 0;
            int colon = entry.lastIndexOf(':');
            // 支持 "modid:item:meta" 形式的元数据
            if (colon > 0 && colon < entry.length() - 1) {
                String suffix = entry.substring(colon + 1);
                try {
                    meta = Integer.parseInt(suffix);
                    id = entry.substring(0, colon);
                } catch (NumberFormatException ignored) {
                    // 不是数字，当作普通物品 id
                }
            }
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            if (item != null) {
                ItemStack ingredient = new ItemStack(item, 1, meta);
                // 两种强化台使用各自的配方表；消耗 amount 个物品执行一次强化。
                if (trait instanceof ArmorModifierTrait) {
                    RecipeMatchHolder.addItem(trait, ingredient, amount, 1);
                } else {
                    trait.addItem(ingredient, amount, 1);
                }
            }
        }
    }

    private static List<String> parseItems(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String part : raw.split("[,;\\s]+")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                out.add(id);
            }
        }
        return out;
    }
}
