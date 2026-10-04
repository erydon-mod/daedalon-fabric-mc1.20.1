package com.oliver.daedalon.client.model.obj;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class FinialParticleHookTest {
    private static final String OUTLINE_DESCRIPTOR="(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;";
    @Test void particleHookMatchesTheActualMinecraftClientCallAndPreservesOtherMixins() throws Exception {
        List<String> annotations=new ArrayList<>();
        try(var input=getClass().getResourceAsStream("/com/oliver/daedalon/mixin/client/FinialBreakParticlesMixin.class")) {
            assertNotNull(input);
            var visitor=new AnnotationVisitor(Opcodes.ASM9) {
                @Override public void visit(String name,Object value) {
                    if(value instanceof String string) annotations.add(string);
                    else if(value instanceof Type type) annotations.add(type.getClassName());
                }
                @Override public AnnotationVisitor visitAnnotation(String name,String descriptor) { return this; }
                @Override public AnnotationVisitor visitArray(String name) { return this; }
            };
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible) { return visitor; }
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible) {
                            annotations.add(descriptor); return visitor;
                        }
                    };
                }
            },ClassReader.SKIP_CODE);
        }
        assertTrue(annotations.contains("net.minecraft.client.particle.ParticleManager"));
        assertTrue(annotations.contains("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;"));
        assertTrue(annotations.contains("addBlockBreakParticles"));
        assertTrue(annotations.contains("Lnet/minecraft/block/BlockState;getOutlineShape"+OUTLINE_DESCRIPTOR));
        int[] matches={0};
        try(var input=getClass().getResourceAsStream("/net/minecraft/client/particle/ParticleManager.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                    if(!name.equals("addBlockBreakParticles")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean isInterface) {
                            if(owner.equals("net/minecraft/block/BlockState") && name.equals("getOutlineShape")
                                    && descriptor.equals(OUTLINE_DESCRIPTOR)) matches[0]++;
                        }
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        assertEquals(1,matches[0],"The wrapped client outline call must exist exactly once in current Minecraft");
    }
}
