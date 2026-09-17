package dev.maskedinvasion;

import com.google.gson.*;
import com.kelco.kamenridercraft.item.base_items.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.neoforged.fml.loading.FMLPaths;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Resolved once on start/reload, never scans item registries during entity ticks. */
public final class FormCatalog {
    public enum Tier { BASIC, ENHANCED, SUPER, FINAL }
    public record Form(String id,Tier tier,RiderDriverItem belt,List<RiderFormChangeItem> forms,Item weapon,int weight,double health,double damage) {
        public ItemStack beltStack(){
            var stack=new ItemStack(belt);RiderDriverItem.resetFormItem(stack);
            for(var form:forms)RiderDriverItem.setFormItem(stack,form,form.getSlot());
            RiderDriverItem.setUpdateForm(stack);return stack;
        }
        public Component displayName(){return Component.translatableWithFallback("form.masked_invasion."+id,"%s · %s",Component.translatable("kamenridercraft.name."+belt.riderName),forms.isEmpty()?new ItemStack(belt.baseFormItem).getHoverName():new ItemStack(forms.getLast()).getHoverName());}
    }
    private final Map<String,Form> forms;
    private final EnumMap<Tier,List<Form>> byTier=new EnumMap<>(Tier.class);
    private FormCatalog(Map<String,Form> forms){this.forms=Map.copyOf(forms);for(var tier:Tier.values()){var list=forms.values().stream().filter(f->f.tier()==tier).toList();if(list.isEmpty())throw new IllegalArgumentException("No forms in tier "+tier);byTier.put(tier,list);}}
    public Collection<Form> all(){return forms.values();}
    public Form get(String id){var form=forms.get(id);if(form==null)throw new IllegalArgumentException("Unknown invasion form: "+id);return form;}
    public Form random(Tier tier,Random random){var list=byTier.get(tier);long total=list.stream().mapToLong(Form::weight).sum();long selected=random.nextLong(total);for(var form:list){selected-=form.weight();if(selected<0)return form;}throw new AssertionError();}
    public static FormCatalog load() throws IOException {
        Path file=FMLPaths.CONFIGDIR.get().resolve("masked_invasion-forms.json");
        if(!Files.exists(file)){Files.createDirectories(file.getParent());try(var in=FormCatalog.class.getResourceAsStream("/data/masked_invasion/default_forms.json")){if(in==null)throw new IOException("Missing built-in form catalogue");Files.copy(in,file);}}
        try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){return parse(JsonParser.parseReader(reader).getAsJsonObject());}
    }
    public static FormCatalog defaults(){try(var reader=new InputStreamReader(Objects.requireNonNull(FormCatalog.class.getResourceAsStream("/data/masked_invasion/default_forms.json")),StandardCharsets.UTF_8)){return parse(JsonParser.parseReader(reader).getAsJsonObject());}catch(IOException e){throw new UncheckedIOException(e);}}
    public static FormCatalog parse(JsonObject root){
        if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Unsupported form catalogue schema");
        Map<String,Form> entries=new LinkedHashMap<>();
        for(var raw:root.getAsJsonArray("forms")){
            var row=raw.getAsJsonObject();String id=row.get("id").getAsString();Tier tier=Tier.valueOf(row.get("tier").getAsString().toUpperCase(Locale.ROOT));
            if(!(item(row.get("belt").getAsString()) instanceof RiderDriverItem belt))throw new IllegalArgumentException(id+": belt is not a Rider driver");
            List<RiderFormChangeItem> forms=new ArrayList<>();Set<Integer> slots=new HashSet<>();
            for(var value:row.getAsJsonArray("forms")){
                if(!(item(value.getAsString()) instanceof RiderFormChangeItem form)||!form.isCompatible(belt))throw new IllegalArgumentException(id+": incompatible form "+value);
                if(form.getSlot()<1||form.getSlot()>5||!slots.add(form.getSlot()))throw new IllegalArgumentException(id+": duplicate/invalid form slot");
                forms.add(form);
            }
            Item weapon=row.has("weapon")?item(row.get("weapon").getAsString()):Items.AIR;
            int weight=row.has("weight")?row.get("weight").getAsInt():1;
            double hp=row.has("healthMultiplier")?row.get("healthMultiplier").getAsDouble():1+0.5*tier.ordinal();
            double damage=row.has("damageMultiplier")?row.get("damageMultiplier").getAsDouble():1+0.25*tier.ordinal();
            if(weight<1||!Double.isFinite(hp)||hp<=0||!Double.isFinite(damage)||damage<=0)throw new IllegalArgumentException(id+": invalid weight/stat multipliers");
            var form=new Form(id,tier,belt,List.copyOf(forms),weapon,weight,hp,damage);form.beltStack();
            if(entries.putIfAbsent(id,form)!=null)throw new IllegalArgumentException("Duplicate invasion form "+id);
        }
        return new FormCatalog(entries);
    }
    private static Item item(String name){var id=ResourceLocation.parse(name.contains(":")?name:"kamenridercraft:"+name);if(!BuiltInRegistries.ITEM.containsKey(id))throw new IllegalArgumentException("Missing item "+id);return BuiltInRegistries.ITEM.get(id);}
}
