package de.saschat.telecreate.mixin;

import com.google.common.base.Predicates;
import com.simibubi.create.content.contraptions.components.structureMovement.glue.SuperGlueEntity;
import com.simibubi.create.content.logistics.trains.track.TrackBlock;
import com.simibubi.create.content.logistics.trains.track.TrackShape;
import com.simibubi.create.content.logistics.trains.track.TrackTileEntity;
import com.simibubi.create.foundation.utility.BlockFace;
import com.simibubi.create.foundation.utility.Iterate;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.foundation.utility.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalForcer;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.List;

import static com.simibubi.create.content.logistics.trains.track.TrackBlock.HAS_TE;
import static com.simibubi.create.content.logistics.trains.track.TrackBlock.SHAPE;

@Mixin(value = TrackBlock.class, remap = false)
public class TrackBlockMixin {

    @Inject(at = @At("HEAD"), method = "connectToNether", cancellable = true)
    public void connectToNether(ServerLevel level, BlockPos pos, BlockState state, CallbackInfo cir) {
        // welcome to hell :)
        TrackShape shape = state.getValue(SHAPE);
        Direction.Axis portalTest = shape == TrackShape.XO ? Direction.Axis.X : shape == TrackShape.ZO ? Direction.Axis.Z : null;
        if (portalTest == null)
            return;

        boolean pop = false;
        String fail = null;
        BlockPos failPos = null;

        for (Direction d : Iterate.directionsInAxis(portalTest)) {
            BlockPos portalPos = pos.relative(d);
            BlockState portalState = level.getBlockState(portalPos);
            Portal relevantPortal = findRelevantPortal(level, portalPos, d);
            boolean isVanilla = portalState.getBlock() instanceof NetherPortalBlock;
            if (!(portalState.getBlock() instanceof NetherPortalBlock) && relevantPortal == null)
                continue;
            pop = true;
            Pair<ServerLevel, BlockFace> otherSide = getOtherSide(level, new BlockFace(pos, d));
            // Why am I still here, just to suffer?
            if (otherSide == null) {
                fail = "missing";
                continue;
            }

            ServerLevel otherLevel = otherSide.getFirst();
            BlockFace otherTrack = otherSide.getSecond();
            BlockPos otherTrackPos = otherTrack.getPos();
            BlockState existing = otherLevel.getBlockState(otherTrackPos);
            if (!existing.getMaterial()
                .isReplaceable()) {
                fail = "blocked";
                failPos = otherTrackPos;
                continue;
            }

            level.setBlock(pos, state.setValue(SHAPE, TrackShape.asPortal(d))
                .setValue(HAS_TE, true), 3);
            BlockEntity te = level.getBlockEntity(pos);
            if (te instanceof TrackTileEntity tte)
                tte.bind(otherLevel.dimension(), otherTrackPos);

            otherLevel.setBlock(otherTrackPos, state.setValue(SHAPE, TrackShape.asPortal(otherTrack.getFace()))
                .setValue(HAS_TE, true), 3);
            BlockEntity otherTe = otherLevel.getBlockEntity(otherTrackPos);
            if (otherTe instanceof TrackTileEntity tte)
                tte.bind(level.dimension(), pos);

            pop = false;
        }

        if (!pop)
            return;

        level.destroyBlock(pos, true);

        if (fail == null)
            return;
        Player player = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 10, Predicates.alwaysTrue());
        if (player == null)
            return;
        player.displayClientMessage(new TextComponent("<!> ").append(Lang.translateDirect("portal_track.failed"))
            .withStyle(ChatFormatting.GOLD), false);
        MutableComponent component =
            failPos != null ? Lang.translateDirect("portal_track." + fail, failPos.getX(), failPos.getY(), failPos.getZ())
                : Lang.translateDirect("portal_track." + fail);
        player.displayClientMessage(new TextComponent(" - ").withStyle(ChatFormatting.GRAY)
            .append(component.withStyle(st -> st.withColor(0xFFD3B4))), false);
        cir.cancel();
    }

    public List<Portal> findNearbyPortals(Level l, BlockPos pos) {
        return l.getEntitiesOfClass(Portal.class, new AABB(pos));
    }

    public Portal findRelevantPortal(Level l, BlockPos pos, Direction dir) {
        List<Portal> nearbyPortals = findNearbyPortals(l, pos);
        for (Portal nearbyPortal : nearbyPortals) {
            Vec3 cdir = nearbyPortal.getNormal().scale(-1);
            Direction direction = Direction.fromNormal((int) cdir.x, (int) cdir.y, (int) cdir.z);
            if (direction.getName().equals(dir.getName())) { // weird comparison but trust me?
                return nearbyPortal;
            }
        }
        return null;
    }

    public Pair<ServerLevel, BlockFace> getOtherSide(ServerLevel level, BlockFace inboundTrack) {
        Portal relevantPortal = findRelevantPortal(level, inboundTrack.getPos().relative(inboundTrack.getFace()), inboundTrack.getFace());
        System.out.println(relevantPortal);
        BlockPos portalPos = inboundTrack.getConnectedPos();
        BlockState portalState = level.getBlockState(portalPos);
        boolean isVanilla = portalState.getBlock() instanceof NetherPortalBlock;
        if (!(portalState.getBlock() instanceof NetherPortalBlock) && relevantPortal == null) // i get checking twice is nice but why?
            return null;
        MinecraftServer minecraftserver = level.getServer();
        ResourceKey<Level> resourcekey = isVanilla ? (level.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER) : relevantPortal.dimensionTo;
        ServerLevel otherLevel = minecraftserver.getLevel(resourcekey);
        if (otherLevel == null || (isVanilla && !minecraftserver.isNetherEnabled()))
            return null;

        if (isVanilla) {
            PortalForcer teleporter = otherLevel.getPortalForcer();
            SuperGlueEntity probe = new SuperGlueEntity(level, new AABB(portalPos));
            probe.setYRot(inboundTrack.getFace()
                .toYRot());
            PortalInfo portalinfo = probe.findDimensionEntryPoint(otherLevel);
            if (portalinfo == null)
                return null;

            BlockPos otherPortalPos = new BlockPos(portalinfo.pos);
            BlockState otherPortalState = otherLevel.getBlockState(otherPortalPos);
            if (!(otherPortalState.getBlock() instanceof NetherPortalBlock))
                return null;


            Direction targetDirection = inboundTrack.getFace();
            if (targetDirection.getAxis() == otherPortalState.getValue(NetherPortalBlock.AXIS))
                targetDirection = targetDirection.getClockWise();
            BlockPos otherPos = otherPortalPos.relative(targetDirection);
            return Pair.of(otherLevel, new BlockFace(otherPos, targetDirection.getOpposite()));
        } else {
            BlockPos pos = inboundTrack.getPos().relative(inboundTrack.getFace());
            Vec3 offset = relevantPortal.position().subtract(new Vec3(pos.getX(), pos.getY(), pos.getZ()));
            Portal counter = findRelevantPortal(otherLevel, new BlockPos(relevantPortal.destination), inboundTrack.getFace());
            if(counter == null)
                return null;
            Vec3 destPos = relevantPortal.getDestPos();
            Vec3 newDest = destPos.subtract(offset);
            return Pair.of(otherLevel, new BlockFace(new BlockPos(newDest).relative(inboundTrack.getFace()), inboundTrack.getFace().getOpposite()));
        }
    }
}
