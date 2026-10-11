package com.enn3developer.gtblocker.core;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Gates crafting at the recipe object rather than at any crafting table: every concrete
 * {@code IRecipe.matches(InventoryCrafting, World)} gets {@code LockHooks.filterMatches(result, this, inv)} wrapped
 * around each {@code IRETURN}, so a recipe whose output is locked simply stops matching. Vanilla and modded tables,
 * autocrafters and AE2's own recipe walk all find recipes through {@code matches}, so none of them needs a hook of
 * its own.
 * <p>
 * A mixin can't do this: mixins target named classes, and the point here is every implementor, including
 * subclasses that override {@code matches} without calling {@code super}. The descriptor is unique to
 * {@code IRecipe.matches}, so matching name + descriptor identifies overrides without walking class hierarchies.
 * Both the SRG name (production) and the MCP name (dev) are accepted, which avoids environment detection.
 */
public class RecipeMatchTransformer implements IClassTransformer {

    private static final Logger LOG = LogManager.getLogger("GTBlocker|Core");

    private static final String SRG_NAME = "func_77569_a";
    private static final String MCP_NAME = "matches";
    private static final String DESC = "(Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraft/world/World;)Z";

    private static final String HOOK_OWNER = "com/enn3developer/gtblocker/lock/LockHooks";
    private static final String HOOK_NAME = "filterMatches";
    private static final String HOOK_DESC = "(ZLjava/lang/Object;Lnet/minecraft/inventory/InventoryCrafting;)Z";

    // Constant-pool needle: classes that never mention the descriptor can't declare the method, and skipping the
    // parse keeps this transformer close to free on the thousands of classes a GTNH instance loads.
    private static final byte[] DESC_BYTES = DESC.getBytes(StandardCharsets.UTF_8);

    private static final List<String> PATCHED = new CopyOnWriteArrayList<>();

    /** Names of every class whose {@code matches} got gated so far, for the startup log line. */
    public static List<String> patchedClasses() {
        return PATCHED;
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !contains(basicClass, DESC_BYTES)
            || transformedName.startsWith("com.enn3developer.gtblocker.")) {
            return basicClass;
        }
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            if ((node.access & Opcodes.ACC_INTERFACE) != 0 || !gate(node)) {
                return basicClass;
            }
            // COMPUTE_MAXS only: the injected code is straight-line and leaves the stack as it found it (one int
            // under the IRETURN), so every existing stack map frame stays valid.
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            PATCHED.add(transformedName);
            return writer.toByteArray();
        } catch (Throwable t) {
            LOG.error("Could not gate recipe class {}, its crafts will not honour locks", transformedName, t);
            return basicClass;
        }
    }

    private static boolean gate(ClassNode node) {
        boolean changed = false;
        for (MethodNode method : node.methods) {
            if (!DESC.equals(method.desc) || !(SRG_NAME.equals(method.name) || MCP_NAME.equals(method.name))
                || (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_STATIC)) != 0) {
                continue;
            }
            if (reassignsInventorySlot(method)) {
                // The hook reloads the inventory from slot 1; if the method reuses that slot for something else,
                // the patched class would fail verification at load time. Leave it alone and say so.
                LOG.warn("{}.{} reuses its inventory slot, not gating it", node.name, method.name);
                continue;
            }
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() != Opcodes.IRETURN) {
                    continue;
                }
                // Stack before: [result]. After: [filterMatches(result, this, inv)].
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, HOOK_NAME, HOOK_DESC, false));
                method.instructions.insertBefore(insn, hook);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean reassignsInventorySlot(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof VarInsnNode var && var.var == 1 && var.getOpcode() >= Opcodes.ISTORE
                && var.getOpcode() <= Opcodes.ASTORE) {
                return true;
            }
            if (insn instanceof IincInsnNode iinc && iinc.var == 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        byte first = needle[0];
        int last = haystack.length - needle.length;
        outer: for (int i = 0; i <= last; i++) {
            if (haystack[i] != first) {
                continue;
            }
            for (int j = 1; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
