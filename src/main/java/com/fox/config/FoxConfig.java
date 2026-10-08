package com.fox.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;
import java.util.Arrays;

public final class FoxConfig {

    // 半神 Demigod
    public static int demigodMaxLevel = 1;
    public static float demigodPerLevel = 0.5f;
    public static boolean demigodHalveHealing = false;
    public static int demigodFreeModifiers = 1;
    public static String demigodItems = "minecraft:dragon_egg";

    // 芝诺的乌龟 Zeno's Turtle
    public static int turtleMaxLevel = 1;
    public static float turtlePerLevel = 0.5f;
    public static int turtleFreeModifiers = 1;
    public static double turtleMinHealth = 0.0;
    public static boolean turtleStackWithDemigod = false;
    public static String turtleItems = "minecraft:totem_of_undying";

    // 落花之情 Falling Blossom
    public static int blossomMaxLevel = 1;
    public static double blossomDurabilityPerDamage = 100.0;
    public static int blossomDamageTiming = 2;
    public static int blossomFreeModifiers = 1;
    public static String blossomItems = "minecraft:red_flower";

    // 残命之契 Life Contract
    public static int lifeContractMaxLevel = 10;
    public static int lifeContractFreeModifiers = 1;
    public static int lifeContractDamageTiming = 2;
    public static float lifeContractDamageReductionScale = 1.0f;
    public static String lifeContractItems = "minecraft:golden_apple";

    // 防毒面具 Gas Mask
    public static int gasMaskMaxLevel = 5;
    public static float gasMaskPerLevel = 0.10f;
    public static int gasMaskFreeModifiers = 1;
    public static int gasMaskCoalAmount = 10;
    public static String gasMaskItems = "minecraft:coal:1";

    // 超级防毒面具 Super Gas Mask
    public static int superGasMaskPaperAmount = 64;
    public static String superGasMaskItems = "minecraft:paper";

    // 反击 Counterstrike（工具强化）
    public static int counterMaxLevel = 1;
    public static float counterChance = 1.0f;
    public static int counterMaxPerTick = 20;
    public static int counterFreeModifiers = 1;
    public static String counterItems = "minecraft:diamond_sword";

    // 自动攻击 Auto Attack（工具强化）
    public static int autoAttackMaxLevel = 1;
    public static int autoAttackFreeModifiers = 1;
    public static String autoAttackItems = "minecraft:iron_sword";

    // 诸武精通 Weapon Mastery（远程工具强化）
    public static int weaponMasterMaxLevel = 5;
    public static float weaponMasterPerLevel = 0.10f;
    public static int weaponMasterFreeModifiers = 1;
    public static String weaponMasterItems = "minecraft:arrow";

    // 多重打击 Multi Strike（投射物强化）
    public static int multiStrikeMaxLevel = 20;
    public static int multiStrikeFreeModifiers = 1;
    public static String multiStrikeItems = "minecraft:bow";
    public static float multiStrikeDamageMultiplier = 1.0f;

    private FoxConfig() {
    }

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        cfg.load();

        migrateProperty(cfg, "minHealth", "turtle", "turtle", "minHealth");
        migrateProperty(cfg, "durabilityPerDamage", "blossom", "blossom", "durabilityPerDamage");
        removeProperty(cfg, "blossom", "durabilityCost");
        removeProperty(cfg, "blossom", "usePercentDurabilityCost");
        removeProperty(cfg, "blossom", "minDurabilityRatio");
        removeProperty(cfg, "durabilityCost", "blossom");
        removeProperty(cfg, "minDurabilityRatio", "blossom");
        removeProperty(cfg, "usePercentDurabilityCost", "blossom");

        demigodMaxLevel = cfg.getInt(
                "maxLevel", "demigod", demigodMaxLevel, 1, 100,
                "半神词条的最大强化等级。"
        );
        demigodPerLevel = cfg.getFloat(
                "perLevel", "demigod", demigodPerLevel, 0f, 1f,
                "半神每级对生命值修改的削减比例（作用于损失量，开启 halveHealing 时也作用于增加量）。"
        );
        demigodHalveHealing = cfg.getBoolean(
                "halveHealing", "demigod", demigodHalveHealing,
                "是否对生命值的增加量（回血）也按同样比例削减。默认关闭（只削减损失量）。"
        );
        demigodFreeModifiers = cfg.getInt(
                "freeModifiers", "demigod", demigodFreeModifiers, 0, 100,
                "半神每级消耗的强化槽数量，0 表示不消耗。"
        );
        demigodItems = cfg.getString(
                "items", "demigod", demigodItems,
                "半神词条的强化物，多个物品用逗号或分号分隔，填物品注册名（如 minecraft:dragon_egg）。"
        );

        turtleMaxLevel = cfg.getInt(
                "maxLevel", "turtle", turtleMaxLevel, 1, 100,
                "芝诺的乌龟词条的最大强化等级。"
        );
        turtlePerLevel = cfg.getFloat(
                "perLevel", "turtle", turtlePerLevel, 0f, 1f,
                "芝诺的乌龟每级允许单次扣除当前生命值的比例上限。"
        );
        turtleFreeModifiers = cfg.getInt(
                "freeModifiers", "turtle", turtleFreeModifiers, 0, 100,
                "芝诺的乌龟每级消耗的强化槽数量，0 表示不消耗。"
        );
        Property turtleMinHealthProp = cfg.get(
                "turtle", "minHealth", 0.0,
                "乌龟生效所需的最小生命值。当玩家当前生命值低于此值时，乌龟词条不生效（半神不受影响）。",
                0.0, Double.MAX_VALUE
        );
        turtleMinHealth = turtleMinHealthProp.getDouble();
        turtleStackWithDemigod = cfg.getBoolean(
                "stackWithDemigod", "turtle", turtleStackWithDemigod,
                "乌龟生效时是否同时让半神生效。默认关闭；开启后先按半神减少本次掉血，再由乌龟限制单次最大掉血。"
        );
        turtleItems = cfg.getString(
                "items", "turtle", turtleItems,
                "芝诺的乌龟词条的强化物，多个物品用逗号或分号分隔，填物品注册名（如 minecraft:totem_of_undying）。"
        );

        blossomMaxLevel = cfg.getInt(
                "maxLevel", "blossom", blossomMaxLevel, 1, 100,
                "落花之情词条的最大强化等级。每级使该件盔甲的耐久上限按倍数计入减伤池。"
        );
        Property blossomPerDamageProp = cfg.get(
                "blossom", "durabilityPerDamage", 100.0,
                "每多少点耐久上限可抵扣 1 点伤害（固定减伤）。默认 100，即 100 耐久 = 1 点减伤。",
                0.0000000000000001, Double.MAX_VALUE
        );
        blossomDurabilityPerDamage = blossomPerDamageProp.getDouble();
        blossomDamageTiming = cfg.getInt(
                "damageTiming", "blossom", blossomDamageTiming, 1, 3,
                "落花之情的减伤时机：1=原始攻击（LivingAttackEvent，护甲和保护前），2=LivingHurtEvent（默认，与事件型保护同阶段），3=最终生命值写入（与半神、乌龟同阶段）。"
        );
        blossomFreeModifiers = cfg.getInt(
                "freeModifiers", "blossom", blossomFreeModifiers, 0, 100,
                "落花之情每级消耗的强化槽数量，0 表示不消耗。"
        );
        blossomItems = cfg.getString(
                "items", "blossom", blossomItems,
                "落花之情词条的强化物，多个物品用逗号或分号分隔，填物品注册名（如 minecraft:red_flower）。"
        );

        lifeContractMaxLevel = cfg.getInt(
                "maxLevel", "lifecontract", lifeContractMaxLevel, 1, 100,
                "残命之契词条的最大强化等级。默认 10 级；等级越高，生命值上限越低，伤害减免越高。"
        );
        lifeContractDamageTiming = cfg.getInt(
                "damageTiming", "lifecontract", lifeContractDamageTiming, 1, 3,
                "残命之契的减伤时机：1=原始攻击（LivingAttackEvent，护甲和保护前），2=LivingHurtEvent（默认，与事件型保护同阶段），3=最终生命值写入。"
        );
        lifeContractDamageReductionScale = cfg.getFloat(
                "damageReductionScale", "lifecontract", lifeContractDamageReductionScale, -255f, 255f,
                "残命之契最终伤害减免比例的系数（乘在按等级算出的减伤上）。默认 1；0 表示不减伤；"
                        + "大于 1 提高减伤（最多到 100%）；负值按 0 处理（不减伤）。"
        );
        lifeContractFreeModifiers = cfg.getInt(
                "freeModifiers", "lifecontract", lifeContractFreeModifiers, 0, 100,
                "残命之契每级消耗的强化槽数量，0 表示不消耗。"
        );
        lifeContractItems = cfg.getString(
                "items", "lifecontract", lifeContractItems,
                "残命之契词条的强化物，多个物品用逗号或分号分隔，默认金苹果。"
        );

        gasMaskMaxLevel = cfg.getInt(
                "maxLevel", "gasmask", gasMaskMaxLevel, 1, 100,
                "防毒面具词条的最大强化等级。默认 5 级。"
        );
        gasMaskPerLevel = cfg.getFloat(
                "perLevel", "gasmask", gasMaskPerLevel, 0f, 1f,
                "防毒面具每级取消负面效果的几率。默认 0.1（10%）。"
        );
        gasMaskFreeModifiers = cfg.getInt(
                "freeModifiers", "gasmask", gasMaskFreeModifiers, 0, 100,
                "防毒面具每级消耗的强化槽数量，0 表示不消耗。"
        );
        gasMaskCoalAmount = cfg.getInt(
                "coalAmount", "gasmask", gasMaskCoalAmount, 1, 64,
                "防毒面具强化所需的木炭数量。默认 10。"
        );
        gasMaskItems = cfg.getString(
                "items", "gasmask", gasMaskItems,
                "防毒面具词条的强化物，多个物品用逗号或分号分隔。"
        );

        // 超级防毒面具
        superGasMaskPaperAmount = cfg.getInt(
                "paperAmount", "supergasmask", superGasMaskPaperAmount, 1, 64,
                "满级防毒面具升级为超级防毒面具所需的纸数量。默认 64。"
        );
        superGasMaskItems = cfg.getString(
                "items", "supergasmask", superGasMaskItems,
                "超级防毒面具词条的强化物，默认纸。"
        );

        counterMaxLevel = cfg.getInt(
                "maxLevel", "counter", counterMaxLevel, 1, 100,
                "反击词条的最大强化等级。"
        );
        counterChance = cfg.getFloat(
                "chance", "counter", counterChance, 0f, 1f,
                "受击时触发反击的几率。默认 1.0（100%）。"
        );
        counterMaxPerTick = cfg.getInt(
                "maxPerTick", "counter", counterMaxPerTick, 1, 1000,
                "每 tick 最多触发的反击次数。默认 20。"
        );
        counterFreeModifiers = cfg.getInt(
                "freeModifiers", "counter", counterFreeModifiers, 0, 100,
                "反击词条每级消耗的强化槽数量，0 表示不消耗。"
        );
        counterItems = cfg.getString(
                "items", "counter", counterItems,
                "反击词条的强化物，多个物品用逗号或分号分隔。"
        );

        autoAttackMaxLevel = cfg.getInt(
                "maxLevel", "autoattack", autoAttackMaxLevel, 1, 100,
                "自动攻击词条的最大强化等级。"
        );
        autoAttackFreeModifiers = cfg.getInt(
                "freeModifiers", "autoattack", autoAttackFreeModifiers, 0, 100,
                "自动攻击词条每级消耗的强化槽数量，0 表示不消耗。"
        );
        autoAttackItems = cfg.getString(
                "items", "autoattack", autoAttackItems,
                "自动攻击词条的强化物，多个物品用逗号或分号分隔，默认铁剑。"
        );

        weaponMasterMaxLevel = cfg.getInt(
                "maxLevel", "weaponmaster", weaponMasterMaxLevel, 1, 100,
                "诸武精通词条的最大强化等级。默认 5 级。"
        );
        weaponMasterPerLevel = cfg.getFloat(
                "perLevel", "weaponmaster", weaponMasterPerLevel, 0f, 1f,
                "诸武精通每级使远程伤害额外获得玩家近战攻击力的比例。默认 0.1（10%）。"
        );
        weaponMasterFreeModifiers = cfg.getInt(
                "freeModifiers", "weaponmaster", weaponMasterFreeModifiers, 0, 100,
                "诸武精通每级消耗的强化槽数量，0 表示不消耗。"
        );
        weaponMasterItems = cfg.getString(
                "items", "weaponmaster", weaponMasterItems,
                "诸武精通词条的强化物，多个物品用逗号或分号分隔，默认箭。"
        );

        multiStrikeMaxLevel = cfg.getInt(
                "maxLevel", "multistrike", multiStrikeMaxLevel, 1, 100,
                "多重打击词条的最大强化等级。默认 20 级：等级即穿透目标数，也即滞留 tick 数；"
                        + "每次命中额外打出等级次伤害（连同首次命中共等级+1次）。"
        );
        multiStrikeFreeModifiers = cfg.getInt(
                "freeModifiers", "multistrike", multiStrikeFreeModifiers, 0, 100,
                "多重打击每级消耗的强化槽数量，0 表示不消耗。"
        );
        multiStrikeItems = cfg.getString(
                "items", "multistrike", multiStrikeItems,
                "多重打击词条的强化物，多个物品用逗号或分号分隔，默认弓。"
        );
        multiStrikeDamageMultiplier = cfg.getFloat(
                "damageMultiplier", "multistrike", multiStrikeDamageMultiplier, 0f, 100f,
                "多重打击每次伤害相对上一次的倍率。默认 1.0（每次等量）；大于 1 递增，小于 1 递减。"
        );

        organizeCategory(cfg, "demigod", "半神：盔甲强化。",
                "maxLevel", "freeModifiers", "items", "perLevel", "halveHealing");
        organizeCategory(cfg, "turtle", "芝诺的乌龟：盔甲强化，需要半神前置。",
                "maxLevel", "freeModifiers", "items", "perLevel", "minHealth", "stackWithDemigod");
        organizeCategory(cfg, "blossom", "落花之情：未损坏的盔甲按耐久上限提供定值减伤，多件叠加，不额外消耗耐久。",
                "maxLevel", "freeModifiers", "items", "durabilityPerDamage", "damageTiming");
        organizeCategory(cfg, "lifecontract", "残命之契：限制玩家当前生命值，并按等级提供伤害减免。",
                "maxLevel", "freeModifiers", "items", "damageTiming", "damageReductionScale");
        organizeCategory(cfg, "gasmask", "防毒面具：头盔专属强化。",
                "maxLevel", "freeModifiers", "items", "coalAmount", "perLevel");
        organizeCategory(cfg, "supergasmask", "超级防毒面具：头盔专属强化，100% 取消负面效果的施加。",
                "items", "paperAmount");
        organizeCategory(cfg, "counter", "反击：匠魂工具强化，手持于主手或副手时生效。",
                "maxLevel", "freeModifiers", "items", "chance", "maxPerTick");
        organizeCategory(cfg, "autoattack", "自动攻击：按住左键时，按照工具攻击速度自动攻击准星目标。",
                "maxLevel", "freeModifiers", "items");
        organizeCategory(cfg, "weaponmaster", "诸武精通：远程工具强化，发射的匠魂投射物命中时每级额外获得玩家近战攻击力的配置比例。",
                "maxLevel", "freeModifiers", "items", "perLevel");
        organizeCategory(cfg, "multistrike", "多重打击：投射物强化。命中生物后滞留并多次造成伤害，然后继续飞行，等级决定穿透目标数、滞留 tick 数与额外伤害次数。",
                "maxLevel", "freeModifiers", "items", "damageMultiplier");

        if (cfg.hasChanged()) {
            cfg.save();
        }
    }

    private static void organizeCategory(Configuration cfg, String category, String comment, String... properties) {
        cfg.setCategoryComment(category, comment);
        cfg.setCategoryPropertyOrder(category, Arrays.asList(properties));
    }

    private static void migrateProperty(Configuration cfg, String oldCategory, String oldName,
                                        String category, String name) {
        if (!cfg.hasKey(oldCategory, oldName)) {
            return;
        }
        // 修正旧版 get(category, key) 参数颠倒产生的分类，已存在的新配置优先。
        if (!cfg.hasKey(category, name)) {
            cfg.renameProperty(oldCategory, oldName, name);
            cfg.moveProperty(oldCategory, name, category);
        } else {
            cfg.getCategory(oldCategory).remove(oldName);
        }
        removeEmptyCategory(cfg, oldCategory);
    }

    private static void removeProperty(Configuration cfg, String category, String name) {
        if (cfg.hasKey(category, name)) {
            cfg.getCategory(category).remove(name);
            removeEmptyCategory(cfg, category);
        }
    }

    private static void removeEmptyCategory(Configuration cfg, String category) {
        if (cfg.hasCategory(category) && cfg.getCategory(category).isEmpty()
                && cfg.getCategory(category).getChildren().isEmpty()) {
            cfg.removeCategory(cfg.getCategory(category));
        }
    }
}
