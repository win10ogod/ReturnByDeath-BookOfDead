package dev.rbd.client;

import com.google.gson.JsonObject;
import dev.rbd.runtime.VillagerNaming;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class VillagerNameScreen extends Screen {
    private final JsonObject data;
    private EditBox name;
    private Button save;
    private Component error=Component.empty();
    private boolean waiting;
    public VillagerNameScreen(JsonObject data){super(Component.translatable("screen.rbd.name.title"));this.data=data.deepCopy();}
    @Override protected void init(){
        String previous=name==null?data.get("original").getAsString():name.getValue();
        int panel=Math.min(300,width-20),left=(width-panel)/2,top=height/2-76;
        name=new EditBox(font,left+16,top+48,panel-32,20,Component.translatable("screen.rbd.name.label"));
        name.setMaxLength(data.get("limit").getAsInt()*2);name.setValue(previous);name.setEditable(!waiting);addRenderableWidget(name);
        save=addRenderableWidget(Button.builder(Component.translatable("screen.rbd.name.confirm"),b->submit()).bounds(left+16,top+116,(panel-40)/2,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(left+panel/2+4,top+116,(panel-40)/2,20).build());
        name.setResponder(value->{error=Component.empty();updateButton();});updateButton();setInitialFocus(name);
        name.setCursorPosition(name.getValue().length());name.setHighlightPos(0);
    }
    private void updateButton(){save.active=!waiting&&VillagerNaming.validName(name.getValue().strip(),data.get("limit").getAsInt());}
    private void submit(){
        if(!save.active)return;
        var request=data.deepCopy();request.addProperty("kind","villager_name_save");request.addProperty("name",name.getValue());
        waiting=true;name.setEditable(false);updateButton();RbdClient.send(request);
    }
    public boolean accepts(JsonObject reply){return data.get("edit").getAsString().equals(reply.get("edit").getAsString());}
    public void result(JsonObject reply){
        if(reply.get("ok").getAsBoolean()){onClose();return;}
        waiting=false;name.setEditable(true);error=Component.translatable(reply.get("message").getAsString());updateButton();
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){submit();return true;}return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        int panel=Math.min(300,width-20),left=(width-panel)/2,top=height/2-76;
        g.fill(0,0,width,height,0x99000000);g.fill(left-1,top-1,left+panel+1,top+153,0xFF967A4F);g.fill(left,top,left+panel,top+152,0xFF211A30);
        g.drawCenteredString(font,title,width/2,top+14,0xFFE5C58B);
        g.drawString(font,Component.translatable("screen.rbd.name.label"),left+16,top+34,0xFFE2D8E8,false);
        g.drawString(font,Component.translatable("screen.rbd.name.limit",data.get("limit").getAsInt()),left+16,top+76,0xFFBDA7CE,false);
        g.drawWordWrap(font,waiting?Component.translatable("screen.rbd.name.saving"):error,left+16,top+91,panel-32,0xFFFFC0A0);
        super.render(g,x,y,partial);
    }
}
