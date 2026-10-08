package com.fox.handler;

import com.fox.config.FoxConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityMultiPart;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import slimeknights.tconstruct.library.entity.EntityProjectileBase;
import slimeknights.tconstruct.library.modifiers.ModifierNBT;
import slimeknights.tconstruct.library.utils.TinkerUtil;

/**
 * 多重打击（Multi Strike）的运行时核心。
 *
 * <p>规则（等级 = N）：
 * <ul>
 *   <li>对每个目标合计造成 N+1 次伤害：首次命中 1 次 + 滞留期补 N 次；</li>
 *   <li>第 k 次伤害 = 首次伤害 × {@code damageMultiplier}^(k-1)（默认等量）；</li>
 *   <li>滞留持续到"该目标伤害次数打满"为止，与 tick 数无关；</li>
 *   <li>穿透：最多对 N 个不同目标触发多重打击，打完一个目标才扣 1 次穿透；</li>
 *   <li>穿透耗尽后，后续命中按普通弹射物处理；</li>
 *   <li>滞留结束只恢复命中前的瞬时速度，不做位移、不给免疫；</li>
 *   <li>同一目标在 {@link #REHIT_COOLDOWN_MS} 毫秒内被再次命中时，不造成伤害、也不消失，
 *       投射物直接穿过；超过该时间后该目标的伤害次数恢复，可再次被多重打击。</li>
 * </ul>
 *
 * <p>与匠魂生命周期的配合：
 * <ul>
 *   <li>{@link #onHit}：在 {@code EntityProjectileBase.onHitEntity} 的 {@code afterHit} 阶段调用；</li>
 *   <li>{@link #shouldSkipRemoval}：由 {@code WeaponMasterTransformer} 改写的
 *       {@code EntityProjectileBase.onEntityHit} 查询，决定是否跳过 {@code setDead()}；</li>
 *   <li>{@link #onUpdate}：由 {@link com.fox.traits.TraitMultiStrike#onProjectileUpdate} 每 tick 调用，
 *       驱动滞留期伤害与结束逻辑。</li>
 * </ul>
 *
 * <p>状态存储在投射物的 {@code getEntityData()}（即实体持久 NBT）中，随实体在端间同步。
 */
public final class MultiStrikeHandler {

    private static final String MODIFIER_ID = "multistrike";

    // 投射物持久 NBT 的键前缀
    private static final String TAG = "fox:multistrike";
    private static final String KEY_INITIALIZED = "initialized";
    private static final String KEY_LODGING = "lodging";
    private static final String KEY_SKIP_REMOVAL = "skipRemoval";
    // 锁定位置与伤害作用的实体（普通生物=本体；多部位实体=命中的部位）
    private static final String KEY_TARGET = "target";
    // 无敌帧重置作用的实体（普通生物=本体；多部位实体=其 parent 本体）。未设置时回退到 KEY_TARGET。
    private static final String KEY_PARENT = "parentId";
    private static final String KEY_HITS_LEFT = "hitsLeft";
    private static final String KEY_PIERCE_LEFT = "pierceLeft";
    private static final String KEY_HIT_INDEX = "hitIndex";
    private static final String KEY_MX = "mx";
    private static final String KEY_MY = "my";
    private static final String KEY_MZ = "mz";
    private static final String KEY_BASE_DAMAGE = "baseDamage";
    // 多重打击自身的存在计时（毫秒时间戳）。触发过多重打击的投射物用它替代原版 ticksExisted 超时；
    // 每次进入滞留都会刷新。未触发过多重打击的投射物仍走原版超时。
    private static final String KEY_DEADLINE = "deadline";

    // 触发过多重打击的投射物的存在时间（毫秒）。滞留中永久豁免；穿过后飞往下一个目标途中受此限制。
    private static final long MULTI_STRIKE_LIFETIME_MS = 4000L;

    // 已触发过多重打击的目标记录：NBTTagList，元素为 {id:int, time:long}
    private static final String KEY_HIT_LOG = "hitLog";
    private static final String LOG_ID = "id";
    private static final String LOG_TIME = "time";

    /** 同一目标在此毫秒数内再次被命中时，多重打击不生效（直接穿过）；之后伤害次数恢复。 */
    private static final long REHIT_COOLDOWN_MS = 1000L;

    // 本次伤害计算中、当前投射物造成的最终远程伤害，供额外打击复用。
    // 由 dealDamageRanged 的改写写入、afterHit 读取。
    private static final ThreadLocal<Float> LAST_RANGED_DAMAGE = new ThreadLocal<>();

    private MultiStrikeHandler() {
    }

    /**
     * 记录某次远程攻击的最终伤害（由 {@code ProjectileCore.dealDamageRanged} 的改写调用）。
     */
    public static void recordRangedDamage(float damage) {
        LAST_RANGED_DAMAGE.set(damage);
    }

    /**
     * 读取并清空最近一次记录的远程伤害；无记录返回 0。
     */
    public static float consumeRangedDamage() {
        Float value = LAST_RANGED_DAMAGE.get();
        LAST_RANGED_DAMAGE.remove();
        return value == null ? 0.0F : value;
    }

    /**
     * 读取投射物所携带物品上的多重打击等级；未带词条返回 0。
     */
    public static int getLevel(ItemStack ammo) {
        if (ammo == null || ammo.isEmpty()) {
            return 0;
        }
        NBTTagCompound tag = TinkerUtil.getModifierTag(ammo, MODIFIER_ID);
        if (tag == null) {
            return 0;
        }
        return ModifierNBT.readTag(tag).level;
    }

    /**
     * 命中回调：在匠魂 {@code onHitEntity} 的 {@code afterHit} 阶段调用，启动滞留。
     *
     * <p>首次命中的伤害由匠魂正常结算，本方法只负责开启后续的多段打击。
     * 若当前目标处于重复命中冷却内，或穿透次数已用尽，则不启动滞留（让投射物直接穿过）。
     */
    public static void onHit(EntityProjectileBase projectile, ItemStack ammoStack,
                             EntityLivingBase shooter, Entity target, float lastDamage) {
        if (projectile == null || projectile.getEntityWorld() == null) {
            return;
        }
        World world = projectile.getEntityWorld();
        if (world.isRemote) {
            return;
        }
        if (!(target instanceof EntityLivingBase) && !(target instanceof MultiPartEntityPart)) {
            return;
        }
        if (target == shooter) {
            return;
        }

        // 归一化出"活体本体"：普通生物=自身；多部位实体=其 parent（如末影龙）。
        // 用于存活判断、射手比较、无敌帧重置。
        EntityLivingBase living = resolveLiving(target);
        if (living == null || !living.isEntityAlive()) {
            return;
        }
        if (living == shooter) {
            return;
        }

        int level = getLevel(ammoStack);
        if (level <= 0) {
            return;
        }

        NBTTagCompound state = getState(projectile);

        // 命中已被处理（无论是滞留还是穿过），先清除上一次残留的跳过标记。
        state.setBoolean(KEY_SKIP_REMOVAL, false);

        if (state.getBoolean(KEY_LODGING)) {
            setState(projectile, state);
            return;
        }

        // 本投射物的首次命中：初始化穿透次数（只写一次，之后不再重置）。
        if (!state.getBoolean(KEY_INITIALIZED)) {
            state.setBoolean(KEY_INITIALIZED, true);
            state.setInteger(KEY_PIERCE_LEFT, level);
        }

        long now = System.currentTimeMillis();
        int targetId = target.getEntityId();

        // 同一目标在冷却内再次命中：不再触发多重，但仍要让它"穿过"（跳过移除）。
        if (isWithinRehitCooldown(state, targetId, now)) {
            state.setBoolean(KEY_SKIP_REMOVAL, true);
            setState(projectile, state);
            return;
        }

        // 穿透次数已用尽：按普通弹射物处理，不启动滞留、不跳过移除。
        if (state.getInteger(KEY_PIERCE_LEFT) <= 0) {
            setState(projectile, state);
            return;
        }

        // 记录命中前的速度，用于结束后恢复飞行。
        state.setDouble(KEY_MX, projectile.motionX);
        state.setDouble(KEY_MY, projectile.motionY);
        state.setDouble(KEY_MZ, projectile.motionZ);

        state.setBoolean(KEY_LODGING, true);
        // 锁定与伤害作用在命中的实体上（多部位实体时即该部位，位置随本体更新）。
        state.setInteger(KEY_TARGET, targetId);
        // 无敌帧重置作用在活体本体上（多部位实体时即 parent）。
        state.setInteger(KEY_PARENT, living.getEntityId());
        // 滞留期还要补打 level 次（连同首次命中，共 level+1 次）。
        state.setInteger(KEY_HITS_LEFT, level);
        state.setInteger(KEY_HIT_INDEX, 0);
        float base = lastDamage > 0.0F ? lastDamage : 0.0F;
        state.setFloat(KEY_BASE_DAMAGE, base);
        // 刷新存在计时：每次进入滞留重新获得 4 秒存在时间（滞留中另永久豁免）。
        state.setLong(KEY_DEADLINE, System.currentTimeMillis() + MULTI_STRIKE_LIFETIME_MS);
        setState(projectile, state);
    }

    /**
     * 每 tick 回调：驱动滞留期的移动锁定与多段伤害。
     */
    public static void onUpdate(EntityProjectileBase projectile, World world, ItemStack ammoStack) {
        NBTTagCompound state = getState(projectile);
        if (!state.getBoolean(KEY_LODGING)) {
            return;
        }
        if (world.isRemote) {
            return;
        }

        // 命中实体（锁定位置与伤害作用；多部位实体时为该部位）
        Entity hitEntity = world.getEntityByID(state.getInteger(KEY_TARGET));
        // 活体本体（存活判断与无敌帧重置；回退到命中实体）
        EntityLivingBase living = resolveLivingById(world, state.getInteger(KEY_PARENT), hitEntity);

        if (hitEntity == null || !hitEntity.isEntityAlive()
                || living == null || !living.isEntityAlive()) {
            endLodging(projectile, state);
            return;
        }

        // 锁在命中实体身上（多部位实体时即部位，随本体移动）
        projectile.setPosition(hitEntity.posX, hitEntity.posY + hitEntity.getEyeHeight() / 2.0F, hitEntity.posZ);
        projectile.motionX = 0.0D;
        projectile.motionY = 0.0D;
        projectile.motionZ = 0.0D;

        // 补打一次额外伤害
        if (state.getInteger(KEY_HITS_LEFT) > 0) {
            dealExtraHit(projectile, hitEntity, living, state, ammoStack);
            state.setInteger(KEY_HITS_LEFT, state.getInteger(KEY_HITS_LEFT) - 1);
            state.setInteger(KEY_HIT_INDEX, state.getInteger(KEY_HIT_INDEX) + 1);
        }

        // 伤害次数打满即结束滞留（与 tick 数无关）。
        if (state.getInteger(KEY_HITS_LEFT) <= 0) {
            endLodging(projectile, state);
        } else {
            setState(projectile, state);
        }
    }

    /**
     * 把命中的实体归一化为"活体本体"：
     * 普通生物返回自身，多部位实体（如末影龙）返回其 parent；其余返回 null。
     */
    public static EntityLivingBase resolveLiving(Entity hit) {
        if (hit instanceof EntityLivingBase) {
            return (EntityLivingBase) hit;
        }
        if (hit instanceof MultiPartEntityPart) {
            IEntityMultiPart parent = ((MultiPartEntityPart) hit).parent;
            if (parent instanceof EntityLivingBase) {
                return (EntityLivingBase) parent;
            }
        }
        return null;
    }

    /**
     * 依 NBT 中的 id 取活体本体；id 无效时回退到给定的命中实体（若其为活体或可归一化）。
     */
    private static EntityLivingBase resolveLivingById(World world, int parentId, Entity fallbackHit) {
        if (parentId != 0 && parentId != -1) {
            Entity parent = world.getEntityByID(parentId);
            if (parent instanceof EntityLivingBase) {
                return (EntityLivingBase) parent;
            }
        }
        return resolveLiving(fallbackHit);
    }

    /**
     * 该投射物是否应因存在时间到限而销毁（供魔法飞弹超时逻辑改写查询）。
     *
     * <ul>
     *   <li>未触发过多重打击的投射物：沿用原版行为（{@code ticksExisted > 32}）；</li>
     *   <li>触发过多重打击的投射物：滞留中永不超时；非滞留时以 NBT 计时为准，
     *       每次进入滞留刷新为 {@link #MULTI_STRIKE_LIFETIME_MS} 毫秒。</li>
     * </ul>
     */
    public static boolean shouldTimeout(EntityProjectileBase projectile) {
        if (projectile == null) {
            return false;
        }
        NBTTagCompound state = getState(projectile);
        if (!state.getBoolean(KEY_INITIALIZED)) {
            // 未参与多重打击：保持魔法飞弹原本的 32 tick（1.6s）超时。
            return projectile.ticksExisted > 32;
        }
        if (state.getBoolean(KEY_LODGING)) {
            // 滞留中永久豁免超时。
            return false;
        }
        long deadline = state.getLong(KEY_DEADLINE);
        if (deadline <= 0L) {
            return false;
        }
        return System.currentTimeMillis() > deadline;
    }

    /**
     * 是否应跳过投射物的移除（{@code setDead}）。
     *
     * <p>两种情形需要跳过：
     * <ul>
     *   <li>正处于滞留中；</li>
     *   <li>冷却内的重复命中（不伤害、也不消失，直接穿过）。</li>
     * </ul>
     *
     * <p>供 {@code onEntityHit} 的改写查询。
     */
    public static boolean shouldSkipRemoval(EntityProjectileBase projectile) {
        if (projectile == null) {
            return false;
        }
        NBTTagCompound state = getState(projectile);
        return state.getBoolean(KEY_LODGING) || state.getBoolean(KEY_SKIP_REMOVAL);
    }

    private static void dealExtraHit(EntityProjectileBase projectile, Entity hitEntity,
                                     EntityLivingBase living, NBTTagCompound state, ItemStack ammoStack) {
        float base = state.getFloat(KEY_BASE_DAMAGE);
        if (base <= 0.0F) {
            return;
        }
        int index = state.getInteger(KEY_HIT_INDEX);
        float multiplier = (float) Math.pow(FoxConfig.multiStrikeDamageMultiplier, index + 1);
        float damage = base * multiplier;
        if (damage <= 0.0F) {
            return;
        }

        Entity shooter = projectile.shootingEntity;
        DamageSource source = new EntityDamageSourceIndirect("arrow", projectile,
                shooter == null ? projectile : shooter);

        // 伤害打在命中实体上：
        // - 普通生物：即本体；
        // - 多部位实体（如末影龙）：打在命中的部位上，由其 parent.attackEntityFromPart 转发扣血。
        // 无敌帧则重置在活体本体上（末影龙的真实 EntityLivingBase.attackEntityFrom 在龙实例上执行），
        // 否则龙受击后的无敌帧（> maxHurtResistantTime/2）会吞掉等量伤害。
        int hurtTime = living.hurtResistantTime;
        living.hurtResistantTime = 0;
        try {
            hitEntity.attackEntityFrom(source, damage);
        } finally {
            living.hurtResistantTime = hurtTime;
        }
    }

    /**
     * 结束滞留：恢复命中前的瞬时速度、扣一次穿透次数、记录目标冷却、清除滞留标记。
     * 不做位移、不给免疫；穿透次数用尽后，投射物再命中就按普通弹射物处理。
     */
    private static void endLodging(EntityProjectileBase projectile, NBTTagCompound state) {
        double mx = state.getDouble(KEY_MX);
        double my = state.getDouble(KEY_MY);
        double mz = state.getDouble(KEY_MZ);

        projectile.motionX = mx;
        projectile.motionY = my;
        projectile.motionZ = mz;
        // defused 与 arrowShake 是 EntityProjectileBase / EntityArrow 的公开字段，可直接重置。
        projectile.defused = false;
        projectile.arrowShake = 0;

        int targetId = state.getInteger(KEY_TARGET);
        if (targetId >= 0) {
            recordHitLog(state, targetId, System.currentTimeMillis());
        }

        int pierceLeft = state.getInteger(KEY_PIERCE_LEFT) - 1;
        state.setBoolean(KEY_LODGING, false);
        // 滞留结束的同一 tick，updateInAir 可能再次命中当前目标；标记跳过移除让其穿过。
        state.setBoolean(KEY_SKIP_REMOVAL, true);
        state.setInteger(KEY_TARGET, -1);
        state.setInteger(KEY_PARENT, -1);
        state.setInteger(KEY_HITS_LEFT, 0);
        state.setInteger(KEY_HIT_INDEX, 0);
        state.setFloat(KEY_BASE_DAMAGE, 0.0F);
        state.setInteger(KEY_PIERCE_LEFT, Math.max(0, pierceLeft));
        setState(projectile, state);
    }

    /**
     * 判断目标是否处于重复命中冷却内。命中日志中超过冷却的记录会被顺带清理。
     */
    private static boolean isWithinRehitCooldown(NBTTagCompound state, int targetId, long now) {
        NBTTagList log = state.getTagList(KEY_HIT_LOG, Constants.NBT.TAG_COMPOUND);
        NBTTagList kept = new NBTTagList();
        boolean within = false;
        for (int i = 0; i < log.tagCount(); i++) {
            NBTTagCompound entry = log.getCompoundTagAt(i);
            int id = entry.getInteger(LOG_ID);
            long time = entry.getLong(LOG_TIME);
            if (now - time >= REHIT_COOLDOWN_MS) {
                continue; // 冷却已过：丢弃，等于"伤害次数恢复"
            }
            if (id == targetId) {
                within = true;
            }
            kept.appendTag(entry);
        }
        state.setTag(KEY_HIT_LOG, kept);
        return within;
    }

    /**
     * 记录某目标刚打完多重打击的时间，用于重复命中冷却判定。
     */
    private static void recordHitLog(NBTTagCompound state, int targetId, long now) {
        NBTTagList log = state.getTagList(KEY_HIT_LOG, Constants.NBT.TAG_COMPOUND);
        NBTTagList kept = new NBTTagList();
        for (int i = 0; i < log.tagCount(); i++) {
            NBTTagCompound entry = log.getCompoundTagAt(i);
            if (now - entry.getLong(LOG_TIME) >= REHIT_COOLDOWN_MS) {
                continue;
            }
            if (entry.getInteger(LOG_ID) == targetId) {
                continue; // 覆盖旧记录
            }
            kept.appendTag(entry);
        }
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger(LOG_ID, targetId);
        entry.setLong(LOG_TIME, now);
        kept.appendTag(entry);
        state.setTag(KEY_HIT_LOG, kept);
    }

    private static NBTTagCompound getState(EntityProjectileBase projectile) {
        NBTTagCompound data = projectile.getEntityData();
        if (!data.hasKey(TAG)) {
            data.setTag(TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(TAG);
    }

    private static void setState(EntityProjectileBase projectile, NBTTagCompound state) {
        projectile.getEntityData().setTag(TAG, state);
    }
}
