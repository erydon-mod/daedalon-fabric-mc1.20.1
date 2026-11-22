package com.oliver.daedalon.client.glaux.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.io.Reader;

/** Small immutable pose/profile recipe, loaded once per client resource reload. */
public final class GlauxRig {
    public static volatile GlauxRig current;
    public final Vector3f pivot;
    public final Hinge[] hinges;
    private final float[][] profiles;
    private final float bottom,top;
    private GlauxRig(Vector3f pivot,Hinge[] hinges,float[][] profiles,float bottom,float top){this.pivot=pivot;this.hinges=hinges;this.profiles=profiles;this.bottom=bottom;this.top=top;}
    public static GlauxRig load(ResourceManager manager) throws Exception {
        try(Reader reader=manager.getResource(new Identifier("daedalon","glaux/rig.json")).orElseThrow().getReader()){
            JsonObject json=JsonParser.parseReader(reader).getAsJsonObject();Vector3f pivot=vector(json.getAsJsonArray("pivot"));JsonArray rows=json.getAsJsonArray("profiles");
            if(rows.size()<2||rows.size()>128)throw new IllegalArgumentException("Invalid Glaux profile rows");float[][] profiles=new float[rows.size()][];
            for(int i=0;i<rows.size();i++){JsonArray row=rows.get(i).getAsJsonArray();if(row.size()<8||row.size()>1024||i>0&&row.size()!=profiles[0].length)throw new IllegalArgumentException("Invalid Glaux profile width");profiles[i]=new float[row.size()];for(int j=0;j<row.size();j++){float value=row.get(j).getAsFloat();if(!Float.isFinite(value)||value<0||value>2)throw new IllegalArgumentException("Invalid Glaux profile radius");profiles[i][j]=value;}}
            JsonArray list=json.getAsJsonArray("hinges");if(list.size()>64)throw new IllegalArgumentException("Too many Glaux feather hinges");Hinge[] hinges=new Hinge[list.size()];
            for(int i=0;i<hinges.length;i++){JsonObject h=list.get(i).getAsJsonObject();Vector3f axis=vector(h.getAsJsonArray("axis"));if(axis.lengthSquared()<.99F||axis.lengthSquared()>1.01F)throw new IllegalArgumentException("Invalid Glaux hinge axis");hinges[i]=new Hinge(vector(h.getAsJsonArray("root")),axis.normalize(),number(h,"angle"),number(h,"rootRadius"),number(h,"rootY"),number(h,"dr"),number(h,"dy"));}
            float bottom=number(json,"profileBottom"),top=number(json,"profileTop");if(top<=bottom)throw new IllegalArgumentException("Invalid Glaux profile height");return new GlauxRig(pivot,hinges,profiles,bottom,top);
        }
    }
    private static float number(JsonObject json,String name){float value=json.get(name).getAsFloat();if(!Float.isFinite(value)||Math.abs(value)>100)throw new IllegalArgumentException("Invalid Glaux rig value");return value;}
    private static Vector3f vector(JsonArray a){if(a.size()!=3)throw new IllegalArgumentException("Invalid Glaux vector");Vector3f v=new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());if(!v.isFinite())throw new IllegalArgumentException("Invalid Glaux vector");return v;}
    private float raw(float y,float angle){float row=Math.max(0,Math.min(profiles.length-1,(y-bottom)/(top-bottom)*(profiles.length-1)));int a=(int)row,b=Math.min(profiles.length-1,a+1),width=profiles[a].length;float column=(float)(angle/(Math.PI*2)*width);column=((column%width)+width)%width;int c=(int)column,d=(c+1)%width;float f=column-c;return(profiles[a][c]*(1-f)+profiles[a][d]*f)*(1-row+a)+(profiles[b][c]*(1-f)+profiles[b][d]*f)*(row-a);}
    private float radius(float y,float angle){float r=(raw(y,angle-.018F)+raw(y,angle)+raw(y,angle+.018F))/3;if(Math.abs(GlauxMotion.wrap(angle-1.34))<.26&&y>1.388)r=Math.min(r,(raw(y,angle-.32F)+raw(y,angle+.32F))/2);return r;}
    public float featherAngle(int index,float yaw){if(index<0||index>=hinges.length)return 0;Hinge h=hinges[index];float lo=-.43F,hi=.43F,theta=h.angle+yaw;for(int k=0;k<10;k++){float a=(lo+hi)/2,c=(float)Math.cos(a),s=(float)Math.sin(a),r=h.rootRadius+h.dr*c-h.dy*s,y=h.rootY+h.dr*s+h.dy*c,target=radius(y,theta)-.0115F;if(r<target)lo=a;else hi=a;}return(lo+hi)/2;}
    public record Hinge(Vector3f root,Vector3f axis,float angle,float rootRadius,float rootY,float dr,float dy) { }
}
