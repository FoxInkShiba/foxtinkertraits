package com.fox.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 匠魂（TConstruct）投射物的字节码改写器。
 *
 * 直接改写匠魂的类，不依赖 Mixin 及其加载时机。负责三处改动：
 * <ul>
 *   <li>{@code EntityProjectileBase.onHitEntity} 头部：调用
 *       {@link WeaponMasterHook#capture(net.minecraft.entity.Entity)}，
 *       赶在匠魂移除射手双手攻击属性之前记录完整近战攻击力（诸武精通）；</li>
 *   <li>{@code EntityProjectileBase.onHitEntity} 内对 {@code onEntityHit(...)} 的调用：
 *       包裹"多重打击滞留/穿过"判断——滞留中或冷却内重复命中时跳过该调用，
 *       从而跳过子类的 {@code setDead()}（覆盖所有投射物，含重写 {@code onEntityHit}
 *       且不调 super 的类型，如匠魂进化的魔法飞弹）；</li>
 *   <li>{@code ProjectileCore.dealDamageRanged}：把最终伤害在写入实体前经
 *       {@link WeaponMasterHook#modifyDamage} 叠加诸武精通加成，
 *       并记录该伤害供多重打击的额外段数复用。</li>
 * </ul>
 *
 * 该 Transformer 通过 {@code FoxCorePlugin.getASMTransformerClass()} 注册，
 * 由 LaunchClassLoader 在目标类加载时调用，天然不受“类已提前加载”影响。
 * 目标类名与两个匠魂版本（原版匠魂 2.13 与匠魂怀古 2.13）一致，二者字节码结构相同。
 */
public class WeaponMasterTransformer implements IClassTransformer {

    private static final String TARGET_PROJECTILE_ENTITY =
            "slimeknights.tconstruct.library.entity.EntityProjectileBase";
    private static final String TARGET_PROJECTILE_CORE =
            "slimeknights.tconstruct.library.tools.ranged.ProjectileCore";

    /**
     * 额外需要注入 {@code dealDamageRanged} 的发射器类。
     *
     * <p>这些类自己重写了 {@code dealDamageRanged}（不走 {@code ProjectileCore} 的实现），
     * 若不单独改写，ASM 对 {@code ProjectileCore.dealDamageRanged} 的注入会被覆盖，
     * 导致 {@code recordRangedDamage} 从未被调用、多重打击的 base 取到 0。
     *
     * <p>类不存在时（未安装对应 mod）不会被加载，自然也不会触发本 transformer，无需担心。
     */
    private static final String[] EXTRA_RANGED_TARGETS = {
            // 匠魂进化：法杖
            "xyz.phanta.tconevo.item.tool.ItemToolSceptre",
    };

    /**
     * 需要改写存在时间超时逻辑的投射物类（自身重写 onUpdate 且带硬编码超时）。
     *
     * <p>当前为匠魂进化的魔法飞弹：其 {@code onUpdate} 内 {@code ticksExisted > 32}
     * 会强制 {@code onHitSomething() → setDead()}，且发生在命中处理之外，会打断多重打击的
     * 滞留与穿透。这里在其 {@code onHitSomething()} 调用前插入判断，由
     * {@link MultiStrikeHook#shouldTimeout} 决定是否真的超时（滞留豁免 / NBT 计时）。
     */
    private static final String TARGET_MAGIC_MISSILE =
            "xyz.phanta.tconevo.entity.EntityMagicMissile";

    private static final String ON_UPDATE_DESC = "()V";
    private static final String ON_HIT_SOMETHING_DESC = "()V";

    private static final String HOOK = "com/fox/asm/WeaponMasterHook";
    private static final String MULTI_STRIKE_HOOK = "com/fox/asm/MultiStrikeHook";

    private static final String ON_HIT_ENTITY_DESC =
            "(Lnet/minecraft/util/math/RayTraceResult;)V";

    private static final String ON_ENTITY_HIT_DESC =
            "(Lnet/minecraft/entity/Entity;)V";

    private static final String TARGET_DEAL_DAMAGE_RANGED_DESC =
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/Entity;"
                    + "Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/entity/Entity;F)Z";

    /**
     * 匠魂投射物基类从 {@code EntityArrow} 继承的射手字段。
     *
     * 运行期 forge 对原版类使用 SRG 名，{@code EntityArrow.shootingEntity}
     * 的 SRG 名为 {@code field_70250_c}，因此这里必须用 SRG 名而非 MCP 可读名。
     * 手写 ASM 没有 Mixin 的 refmap 自动重映射，写错会导致 NoSuchFieldError。
     */
    private static final String SHOOTING_ENTITY_FIELD = "field_70250_c";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        String target = transformedName != null ? transformedName : name;
        if (TARGET_PROJECTILE_ENTITY.equals(target)) {
            return transformProjectileEntity(basicClass);
        }
        if (TARGET_PROJECTILE_CORE.equals(target) || isExtraRangedTarget(target)) {
            return transformDealDamageRanged(basicClass);
        }
        if (TARGET_MAGIC_MISSILE.equals(target)) {
            return transformMagicMissile(basicClass);
        }
        return basicClass;
    }

    /** 目标是否属于"自行实现 dealDamageRanged 的发射器"清单。 */
    private static boolean isExtraRangedTarget(String target) {
        for (String t : EXTRA_RANGED_TARGETS) {
            if (t.equals(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 改写 {@code EntityProjectileBase.onHitEntity}：
     * <ul>
     *   <li>方法头部捕获射手近战攻击力（诸武精通）；</li>
     *   <li>把对 {@code onEntityHit(...)} 的调用包上"多重打击滞留/穿过"判断——
     *       滞留中或冷却内重复命中时跳过该调用，从而跳过子类 {@code onEntityHit}
     *       内的 {@code setDead()}。</li>
     * </ul>
     *
     * <p>注入点选在父类 {@code onHitEntity}，而非 {@code onEntityHit} 方法体：因为
     * 部分投射物（如匠魂进化的 {@code EntityMagicMissile}）会重写 {@code onEntityHit}
     * 且不调用 {@code super}，若注入到 {@code onEntityHit} 头部则被绕过。{@code onHitEntity}
     * 是父类共用方法，其内部对 {@code onEntityHit} 的 {@code invokevirtual} 会动态分派到
     * 子类实现，因此在此拦截可覆盖所有投射物。
     *
     * <p>由于新增了跳转指令，需用 {@code COMPUTE_FRAMES} 重算 StackMapTable；
     * {@link SafeClassWriter} 覆写 {@code getCommonSuperClass} 以避免类加载期解析。
     */
    private byte[] transformProjectileEntity(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM5, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                if (mv == null) {
                    return null;
                }
                if ("onHitEntity".equals(name) && ON_HIT_ENTITY_DESC.equals(desc)) {
                    return new MethodVisitor(Opcodes.ASM5, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            // WeaponMasterHook.capture(((EntityArrow) this).field_70250_c);
                            visitVarInsn(Opcodes.ALOAD, 0);
                            visitFieldInsn(Opcodes.GETFIELD,
                                    "net/minecraft/entity/projectile/EntityArrow",
                                    SHOOTING_ENTITY_FIELD,
                                    "Lnet/minecraft/entity/Entity;");
                            visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "capture",
                                    "(Lnet/minecraft/entity/Entity;)V", false);
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mname,
                                                    String mdesc, boolean itf) {
                            // 拦截对 onEntityHit(Entity) 的调用（父类 onHitEntity 内唯一一处）。
                            // 进入时栈: [..., this, entityHit]
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                    && "onEntityHit".equals(mname)
                                    && ON_ENTITY_HIT_DESC.equals(mdesc)) {
                                // 复制 this 用于判断：DUP2 得到 [.., this, entityHit, this, entityHit]
                                super.visitInsn(Opcodes.DUP2);
                                // 丢弃多余的 entityHit：POP 得到 [.., this, entityHit, this]
                                super.visitInsn(Opcodes.POP);
                                // MultiStrikeHook.isLodging(this) 消费掉 this，压入 Z
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, MULTI_STRIKE_HOOK, "isLodging",
                                        "(Lslimeknights/tconstruct/library/entity/EntityProjectileBase;)Z",
                                        false);
                                org.objectweb.asm.Label doCall = new org.objectweb.asm.Label();
                                org.objectweb.asm.Label after = new org.objectweb.asm.Label();
                                // Z == 0（不滞留）→ 正常调用
                                super.visitJumpInsn(Opcodes.IFEQ, doCall);
                                // 滞留/穿过：丢弃 [this, entityHit]，跳过调用
                                super.visitInsn(Opcodes.POP2);
                                super.visitJumpInsn(Opcodes.GOTO, after);
                                super.visitLabel(doCall);
                                super.visitMethodInsn(opcode, owner, mname, mdesc, itf);
                                super.visitLabel(after);
                                return;
                            }
                            super.visitMethodInsn(opcode, owner, mname, mdesc, itf);
                        }
                    };
                }
                return mv;
            }
        };
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }

    /**
     * 改写魔法飞弹 {@code onUpdate}（MCP）/ {@code func_70071_h_}（SRG）的存在时间超时逻辑。
     *
     * <p>原逻辑在外层判断 {@code ticksExisted > 32} 后调用 {@code onHitSomething()}（内部
     * {@code setDead()}）。这里在该调用前插入一层判断：
     * {@code if (!MultiStrikeHook.shouldTimeout(this)) skip onHitSomething()}。
     * 未参与多重打击时 {@code shouldTimeout} 仍返回 {@code ticksExisted > 32}（保持原行为）；
     * 参与后滞留中豁免、非滞留按 NBT 计时（默认 4 秒）。
     *
     * <p>进入 {@code onHitSomething} 调用前栈为 {@code [this]}，用 {@code DUP} 复制 this 供判断，
     * {@code IFEQ}/{@code GOTO} 为新增跳转，故用 {@code COMPUTE_FRAMES}。
     */
    private byte[] transformMagicMissile(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM5, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                if (mv == null) {
                    return null;
                }
                boolean isUpdate = ("onUpdate".equals(name) || "func_70071_h_".equals(name))
                        && ON_UPDATE_DESC.equals(desc);
                if (!isUpdate) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM5, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mname,
                                                String mdesc, boolean itf) {
                        // 拦截 onUpdate 内对 onHitSomething() 的调用（栈: [this]）。
                        if (opcode == Opcodes.INVOKEVIRTUAL
                                && "onHitSomething".equals(mname)
                                && ON_HIT_SOMETHING_DESC.equals(mdesc)) {
                            // DUP → [this, this]
                            super.visitInsn(Opcodes.DUP);
                            // shouldTimeout(this) 消费栈顶 this → [this, Z]
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, MULTI_STRIKE_HOOK, "shouldTimeout",
                                    "(Lslimeknights/tconstruct/library/entity/EntityProjectileBase;)Z",
                                    false);
                            org.objectweb.asm.Label doCall = new org.objectweb.asm.Label();
                            org.objectweb.asm.Label after = new org.objectweb.asm.Label();
                            // Z != 0（应超时）→ 正常调用
                            super.visitJumpInsn(Opcodes.IFNE, doCall);
                            // 不该超时：丢弃栈上原 this，跳过调用
                            super.visitInsn(Opcodes.POP);
                            super.visitJumpInsn(Opcodes.GOTO, after);
                            super.visitLabel(doCall);
                            super.visitMethodInsn(opcode, owner, mname, mdesc, itf);
                            super.visitLabel(after);
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, mname, mdesc, itf);
                    }
                };
            }
        };
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }

    /**
     * 改写发射器（{@code ProjectileCore} 及自行实现 {@code dealDamageRanged} 的类）的远程伤害结算：
     * 把写入实体的最终伤害替换为经 {@link WeaponMasterHook#modifyDamage} 修正后的值（诸武精通加成），
     * 并把该值记录给多重打击复用。
     *
     * <p>原序列中，{@code dealDamageRanged} 在调用 {@code attackEntityFrom(source, damage)} 前会
     * 加载 {@code damage}（即 {@code FLOAD 5}）。这里把该处替换为：
     * {@code FLOAD 5; ALOAD 2(projectile); ALOAD 3(EntityLivingBase shooter);
     * INVOKESTATIC WeaponMasterHook.modifyDamage(...)F; DUP;
     * INVOKESTATIC MultiStrikeHook.recordRangedDamage(F)V}。
     * 栈净变化为 0（消费原 damage，压回修正后的 damage），且额外 {@code DUP} 一份交给记录钩子。
     *
     * <p>参数槽约定（与 {@code ProjectileCore.dealDamageRanged} 及 {@code ItemToolSceptre} 一致）：
     * {@code 0=this, 1=stack, 2=projectile, 3=EntityLivingBase(player/shooter), 4=entity(target), 5=damage}。
     */
    private byte[] transformDealDamageRanged(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM5, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                if (mv == null) {
                    return null;
                }
                boolean target = "dealDamageRanged".equals(name)
                        && TARGET_DEAL_DAMAGE_RANGED_DESC.equals(desc);
                if (!target) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM5, mv) {
                    @Override
                    public void visitVarInsn(int opcode, int var) {
                        // 命中攻击写入前的伤害加载（FLOAD 5），替换为 hook 修正值。
                        if (opcode == Opcodes.FLOAD && var == 5) {
                            super.visitVarInsn(Opcodes.FLOAD, 5);
                            super.visitVarInsn(Opcodes.ALOAD, 2);
                            super.visitVarInsn(Opcodes.ALOAD, 3);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "modifyDamage",
                                    "(FLnet/minecraft/entity/Entity;"
                                            + "Lnet/minecraft/entity/EntityLivingBase;)F", false);
                            // 复制栈顶的最终伤害并交给多重打击记录，不影响原有栈序。
                            super.visitInsn(Opcodes.DUP);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, MULTI_STRIKE_HOOK, "recordRangedDamage",
                                    "(F)V", false);
                            return;
                        }
                        super.visitVarInsn(opcode, var);
                    }
                };
            }
        };
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }

    /**
     * 覆盖 getCommonSuperClass，避免类加载期触发目标类解析。
     * 这里仅使用 COMPUTE_MAXS，仍保留该覆盖作为防御。
     */
    private static final class SafeClassWriter extends ClassWriter {
        private SafeClassWriter(ClassReader reader, int flags) {
            super(reader, flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            return "java/lang/Object";
        }
    }
}
