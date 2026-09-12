package dev.rbd.phantom;

import dev.rbd.runtime.GameSession;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import java.io.UncheckedIOException;
import java.util.*;

/** An execution, not inflated ordinary damage. Only the explicit aura call opens this scope. */
public final class PhantomExecution {
    private static final ThreadLocal<Set<UUID>> EXECUTING=ThreadLocal.withInitial(HashSet::new);
    public static boolean executing(LivingEntity entity){return EXECUTING.get().contains(entity.getUUID());}
    public static void kill(LivingEntity victim){
        if(!(victim.level() instanceof ServerLevel)||victim.isRemoved())return;
        DamageSource source=victim.damageSources().genericKill();
        var game=GameSession.current;
        // A holder must truly die in RBD's ledger, then continue through its connected return.
        // Ordinary totems, armor and third-party cancelled death events cannot intercept this.
        if(game!=null&&game.isHolder(victim.getUUID())){
            try{victim.setHealth(0);game.death(victim,source);victim.setHealth(1);}
            catch(java.io.IOException e){throw new UncheckedIOException(e);}
            return;
        }
        var scope=EXECUTING.get();if(!scope.add(victim.getUUID()))return;
        try{
            victim.setInvulnerable(false);victim.invulnerableTime=0;victim.setAbsorptionAmount(0);
            victim.setHealth(0);victim.die(source);
            // Some boss subclasses implement resurrection in die()/setHealth(). Their current
            // entity still has to leave the living world; spawned successor entities face the next pulse.
            victim.setHealth(0);
            if(!(victim instanceof net.minecraft.server.level.ServerPlayer))victim.remove(Entity.RemovalReason.KILLED);
        }finally{scope.remove(victim.getUUID());if(scope.isEmpty())EXECUTING.remove();}
    }
    private PhantomExecution(){}
}
