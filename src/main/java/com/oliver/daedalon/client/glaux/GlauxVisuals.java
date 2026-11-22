package com.oliver.daedalon.client.glaux;

import com.oliver.daedalon.Daedalon;
import com.oliver.daedalon.client.glaux.model.GlauxBlockEntityRenderer;
import com.oliver.daedalon.client.glaux.model.GlauxModelLoadingPlugin;
import com.oliver.daedalon.client.glaux.model.GlauxMotion;
import com.oliver.daedalon.client.glaux.model.GlauxGestures;
import com.oliver.daedalon.client.glaux.model.GlauxRig;
import com.oliver.daedalon.glaux.GlauxBlock;
import com.oliver.daedalon.glaux.GlauxRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Client-only cosmetic state. No block ticking, world scan or animation packets. */
public final class GlauxVisuals {
    private static final Map<BlockPos,Pose> POSES=new HashMap<>();
    private static final Set<BlockPos> STILL=new HashSet<>();
    private static Frustum frustum;
    private static Object worldIdentity;
    private static boolean registered,reducedMotion;
    private GlauxVisuals() { }
    public static void register(){if(registered)return;registered=true;GlauxModelLoadingPlugin.register();BlockEntityRendererRegistry.register(GlauxRegistry.blockEntityType(),GlauxBlockEntityRenderer::new);
        WorldRenderEvents.START.register(context->frustum=null);
        WorldRenderEvents.AFTER_SETUP.register(context->frustum=context.frustum());
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener(){@Override public Identifier getFabricId(){return new Identifier("daedalon","glaux_rig");}@Override public void reload(ResourceManager manager){try{GlauxRig.current=GlauxRig.load(manager);}catch(Exception e){GlauxRig.current=null;Daedalon.LOGGER.error("Unable to load Glaux rigid pose recipe",e);}}});
        ClientTickEvents.END_CLIENT_TICK.register(GlauxVisuals::tick);
    }
    public static void setReducedMotion(boolean value){reducedMotion=value;if(value)for(Pose pose:POSES.values())pose.reset();}
    public static void setAttentive(BlockPos pos,boolean attentive){Pose pose=pose(pos);pose.attentive=attentive;if(attentive)STILL.remove(pos);else{STILL.add(pos.toImmutable());pose.shake=-10000;}}
    public static void gesture(BlockPos pos,String name){MinecraftClient client=MinecraftClient.getInstance();if(client.world==null)return;Pose pose=pose(pos);double now=client.world.getTime();switch(name.toLowerCase(Locale.ROOT)){
        case "still"->setAttentive(pos,false);case "attentive"->setAttentive(pos,true);
        case "acknowledge","nod"->{if(GlauxGestures.mayMove(pose.attentive,reducedMotion))pose.nod=now;}
        case "goodbye"->{if(GlauxGestures.mayMove(pose.attentive,reducedMotion))pose.nod=now;pose.settleUntil=now+30;}
        case "shake"->{long clock=System.nanoTime();if(GlauxGestures.mayShake(pose.attentive,reducedMotion,clock,pose.lastShakeNanos)){pose.shake=now;pose.lastShakeNanos=clock;}}
        default->{ }
    }}
    public static Pose pose(BlockPos pos){MinecraftClient client=MinecraftClient.getInstance();if(worldIdentity!=client.world){POSES.clear();STILL.clear();worldIdentity=client.world;}Pose pose=POSES.computeIfAbsent(pos.toImmutable(),ignored->{Pose next=new Pose();next.attentive=!STILL.contains(pos);return next;});if(client.world!=null)pose.lastSeen=client.world.getTime();return pose;}
    private static void tick(MinecraftClient client){if(client.world==null||client.player==null){POSES.clear();STILL.clear();frustum=null;worldIdentity=client.world;return;}if(worldIdentity!=client.world){POSES.clear();STILL.clear();worldIdentity=client.world;}
        STILL.removeIf(pos->client.world.isChunkLoaded(pos)&&!GlauxRegistry.isGlaux(client.world.getBlockState(pos)));
        long now=client.world.getTime();Iterator<Map.Entry<BlockPos,Pose>> iterator=POSES.entrySet().iterator();
        while(iterator.hasNext()){Map.Entry<BlockPos,Pose> entry=iterator.next();BlockPos pos=entry.getKey();Pose pose=entry.getValue();if(now-pose.lastSeen>100||!client.world.isChunkLoaded(pos)||!GlauxRegistry.isGlaux(client.world.getBlockState(pos))){iterator.remove();continue;}pose.previousYaw=pose.motion.yaw();pose.previousPitch=pose.pitch;if(reducedMotion){pose.reset();continue;}
            double facing=Math.toRadians(GlauxBlock.rotationDegrees(client.world.getBlockState(pos).get(GlauxBlock.FACING)));GlauxRig loadedRig=GlauxRig.current;double pivotX=loadedRig==null?.17818823:loadedRig.pivot.x,pivotZ=loadedRig==null?.10094695:loadedRig.pivot.z,offsetX=Math.cos(facing)*pivotX+Math.sin(facing)*pivotZ,offsetZ=Math.cos(facing)*pivotZ-Math.sin(facing)*pivotX;
            Vec3d eye=client.player.getEyePos();double dx=eye.x-pos.getX()-.5-offsetX,dz=eye.z-pos.getZ()-.5-offsetZ,dy=eye.y-pos.getY()-1.7,distance=dx*dx+dy*dy+dz*dz;double target=0;float pitch=0;
            if(pose.attentive&&GlauxMotion.withinAwareness(distance)&&now>=pose.settleUntil){
                if(Math.floorMod(now+pos.asLong(),5)==0){HitResult hit=client.world.raycast(new RaycastContext(eye,new Vec3d(pos.getX()+.5+offsetX,pos.getY()+1.7,pos.getZ()+.5+offsetZ),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,client.player));pose.visible=hit.getType()==HitResult.Type.MISS||hit instanceof net.minecraft.util.hit.BlockHitResult block&&GlauxRegistry.basePos(client.world,block.getBlockPos()).equals(pos);}
                if(pose.visible){target=Math.atan2(dx,dz)-facing;pitch=(float)Math.max(-.04,Math.min(.04,-Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));}
            }
            pose.motion.advance(target,.05);pose.pitch+=(pitch-pose.pitch)*.2F;
        }
    }
    public static boolean reducedMotion(){return reducedMotion;}
    public static boolean inFrustum(Box bounds){return frustum==null||frustum.isVisible(bounds);}
    public static int trackedOwls(){return POSES.size();}
    public static final class Pose {
        public final GlauxMotion motion=new GlauxMotion();
        public double previousYaw,nod=-10000,shake=-10000,settleUntil;
        public float pitch,previousPitch;
        public boolean attentive=true,visible=false;
        public long lastSeen,lastShakeNanos=Long.MIN_VALUE/2;
        private void reset(){motion.reset();previousYaw=0;pitch=previousPitch=0;nod=shake=-10000;}
        public float yaw(float delta){return(float)(previousYaw+GlauxMotion.shortestDelta(previousYaw,motion.yaw())*delta);}
        public float pitch(float delta,double time){float nodAngle=GlauxGestures.nod(time-nod);return previousPitch+(pitch-previousPitch)*delta+nodAngle;}
        public float roll(double time){return GlauxGestures.shiver(time-shake);}
    }
}
