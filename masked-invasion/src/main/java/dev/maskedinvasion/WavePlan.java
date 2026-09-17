package dev.maskedinvasion;
import net.minecraft.world.level.GameRules;
import java.util.*;

public final class WavePlan {
    public static List<FormCatalog.Form> create(FormCatalog catalog,GameRules rules,int wave,int players,long seed){
        Random random=new Random(seed);
        int min=InvasionRules.EXTRA_MIN.get(rules),max=Math.max(min,InvasionRules.EXTRA_MAX.get(rules));
        int enhanced=min+random.nextInt(max-min+1);
        long desired=InvasionRules.BASE_COUNT.get(rules)+(long)enhanced+(long)Math.max(0,wave-1)*InvasionRules.COUNT_GROWTH.get(rules)+(long)Math.max(0,players-1)*InvasionRules.PER_PLAYER.get(rules);
        int count=(int)Math.min(InvasionRules.MAX_COUNT.get(rules),desired);
        List<FormCatalog.Form> result=new ArrayList<>();
        int superFrom=InvasionRules.SUPER_WAVE.get(rules),finalFrom=InvasionRules.FINAL_WAVE.get(rules);
        for(int i=0;i<count;i++){
            FormCatalog.Tier tier;
            if(wave>=finalFrom&&i<1+(wave-finalFrom)/3)tier=FormCatalog.Tier.FINAL;
            else if(wave>=superFrom&&i<count/3)tier=FormCatalog.Tier.SUPER;
            else if(i<(long)enhanced+Math.max(0,(long)wave-1))tier=FormCatalog.Tier.ENHANCED;
            else tier=FormCatalog.Tier.BASIC;
            result.add(catalog.random(tier,random));
        }
        Collections.shuffle(result,random);return List.copyOf(result);
    }
    public static double growth(int wave,int percent){return 1+Math.max(0,(long)wave-1)*(percent/100.0);}
    private WavePlan(){}
}
