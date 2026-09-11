package dev.rbd.client;
import com.google.gson.*;
import dev.rbd.network.RbdNetwork;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
public final class LibraryScreen extends Screen {
    private final JsonObject data;
    private static final ResourceLocation ICON=ResourceLocation.fromNamespaceAndPath("rbd","textures/gui/icons/archive_shelf.png");
    public LibraryScreen(JsonObject data){super(Component.translatable("screen.rbd.archive"));this.data=data;}
    @Override protected void init(){
        int left=width/2-150,panel=Math.min(274,height-16),top=(height-panel)/2,row=Math.max(12,Math.min(22,(panel-88)/8));
        int i=0;for(JsonElement element:data.getAsJsonArray("entries")){
            JsonObject entry=element.getAsJsonObject();boolean known=entry.get("known").getAsBoolean();
            var button=Button.builder(known?Component.literal(entry.get("name").getAsString()):Component.translatable("message.rbd.unfamiliar"),b->{var message=RbdNetwork.message("select_book");message.addProperty("id",entry.get("id").getAsString());RbdClient.send(message);}).bounds(left+16,top+50+i*row,268,row-1).build();
            button.active=known;addRenderableWidget(button);i++;
        }
        var previous=Button.builder(Component.literal("‹"),b->page(-1)).bounds(left+16,top+panel-26,40,20).build();previous.active=data.get("page").getAsInt()>0;addRenderableWidget(previous);
        var next=Button.builder(Component.literal("›"),b->page(1)).bounds(left+244,top+panel-26,40,20).build();next.active=data.get("page").getAsInt()+1<data.get("pages").getAsInt();addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(left+100,top+panel-26,100,20).build());
    }
    private void page(int direction){var msg=RbdNetwork.message("page");msg.addProperty("direction",direction);RbdClient.send(msg);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        g.fill(0,0,width,height,0xB80C0814);int left=width/2-150,panel=Math.min(274,height-16),top=(height-panel)/2,row=Math.max(12,Math.min(22,(panel-88)/8));
        g.fill(left-1,top-1,left+301,top+panel+1,0xFF967A4F);g.fill(left,top,left+300,top+panel,0xFF211A30);
        g.blit(ICON,left+16,top+12,0,0,32,32,32,32);g.drawString(font,title,left+58,top+14,0xFFE5C58B,false);
        g.drawString(font,Component.translatable("screen.rbd.shelf",data.get("shelf").getAsInt(),data.get("page").getAsInt()+1,data.get("pages").getAsInt()),left+58,top+30,0xFFBDA7CE,false);
        if(data.getAsJsonArray("entries").isEmpty())g.drawCenteredString(font,Component.translatable("screen.rbd.empty_shelf"),width/2,top+114,0xFFBDA7CE);
        super.render(g,x,y,partial);
    }
}
