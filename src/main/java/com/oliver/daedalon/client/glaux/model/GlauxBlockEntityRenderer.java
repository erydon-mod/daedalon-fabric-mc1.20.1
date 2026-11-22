package com.oliver.daedalon.client.glaux.model;

import com.oliver.daedalon.client.glaux.GlauxVisuals;
import com.oliver.daedalon.glaux.GlauxBlock;
import com.oliver.daedalon.glaux.GlauxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Static body is chunk geometry. Only the cached head is emitted each frame. */
public final class GlauxBlockEntityRenderer implements BlockEntityRenderer<GlauxBlockEntity> {
    private static final RenderLayer LAYER=RenderLayer.getEntityCutoutNoCull(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
    private final Matrix4f headMatrix=new Matrix4f(),partMatrix=new Matrix4f();
    private final Matrix3f headNormal=new Matrix3f(),partNormal=new Matrix3f();
    public GlauxBlockEntityRenderer(BlockEntityRendererFactory.Context context) { }
    @Override public void render(GlauxBlockEntity entity,float tickDelta,MatrixStack matrices,VertexConsumerProvider buffers,int light,int overlay){
        GlauxHeadMeshes meshes=GlauxHeadMeshes.current;GlauxRig rig=GlauxRig.current;if(meshes==null||rig==null||entity.getWorld()==null||!GlauxVisuals.inFrustum(entity.renderBounds()))return;
        MinecraftClient client=MinecraftClient.getInstance();double distance=entity.getPos().toCenterPos().squaredDistanceTo(client.gameRenderer.getCamera().getPos());
        GlauxVisuals.Pose pose=distance<400?GlauxVisuals.pose(entity.getPos()):null;double time=entity.getWorld().getTime()+tickDelta;
        float yaw=pose==null||GlauxVisuals.reducedMotion()?0:pose.yaw(tickDelta),pitch=pose==null||GlauxVisuals.reducedMotion()?0:pose.pitch(tickDelta,time),roll=pose==null||GlauxVisuals.reducedMotion()?0:pose.roll(time);
        float facing=(float)Math.toRadians(GlauxBlock.rotationDegrees(entity.getCachedState().get(GlauxBlock.FACING)));
        Vector3f pivot=rig.pivot;headMatrix.set(matrices.peek().getPositionMatrix()).translate(.5F,0,.5F).rotateY(facing).translate(pivot).rotateY(yaw).rotateX(pitch).rotateZ(roll);
        headNormal.set(matrices.peek().getNormalMatrix()).rotateY(facing).rotateY(yaw).rotateX(pitch).rotateZ(roll);
        VertexConsumer consumer=buffers.getBuffer(LAYER);
        for(GlauxHeadMeshes.Part part:meshes.atDistance(distance)){
            partMatrix.set(headMatrix);partNormal.set(headNormal);int index=part.hingeIndex();
            if(index>=0&&index<rig.hinges.length&&distance<400){GlauxRig.Hinge hinge=rig.hinges[index];float angle=rig.featherAngle(index,yaw);Vector3f root=hinge.root(),axis=hinge.axis();partMatrix.translate(root).rotate(angle,axis).translate(-root.x,-root.y,-root.z);partNormal.rotate(angle,axis);}
            emit(consumer,part.vertices(),partMatrix,partNormal,light);
        }
    }
    private static void emit(VertexConsumer out,float[] data,Matrix4f p,Matrix3f n,int light){
        for(int i=0;i<data.length;i+=8){float x=data[i],y=data[i+1],z=data[i+2],nx=data[i+3],ny=data[i+4],nz=data[i+5];
            float px=p.m00()*x+p.m10()*y+p.m20()*z+p.m30(),py=p.m01()*x+p.m11()*y+p.m21()*z+p.m31(),pz=p.m02()*x+p.m12()*y+p.m22()*z+p.m32();
            float a=n.m00()*nx+n.m10()*ny+n.m20()*nz,b=n.m01()*nx+n.m11()*ny+n.m21()*nz,c=n.m02()*nx+n.m12()*ny+n.m22()*nz;
            out.vertex(px,py,pz).color(255,255,255,255).texture(data[i+6],data[i+7]).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(a,b,c).next();
        }
    }
    @Override public boolean rendersOutsideBoundingBox(GlauxBlockEntity entity){return true;}
    @Override public int getRenderDistance(){return 512;}
}
