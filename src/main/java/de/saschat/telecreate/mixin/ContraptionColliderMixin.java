package de.saschat.telecreate.mixin;

import com.simibubi.create.content.contraptions.components.structureMovement.ContraptionCollider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.List;
import java.util.function.Predicate;

@Mixin(value = ContraptionCollider.class, remap = false)
public class ContraptionColliderMixin {
    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private static List<Entity> getEntitiesOfClass(Level instance, Class aClass, AABB aabb, Predicate predicate) {
        // Wow, such advanced mixin. <doge>
        List<Entity> entitiesOfClass = instance.getEntitiesOfClass(aClass, aabb, predicate);
        entitiesOfClass.removeIf(a -> a instanceof Portal); // collision bugfix woohoo
        return entitiesOfClass;
    }
}
