package dev.khanh.feature;

import dev.khanh.KhanhClient;
import dev.khanh.module.ModuleOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Screenshot combat additions: actual attack/equipment hooks and bounded, confirmed macros. */
public final class CombatPvpService {
    private static final PvpTickGate GATE=new PvpTickGate();
    private static final PvpConfirmationFence ANCHOR_ACK=new PvpConfirmationFence();
    private static final Map<Integer,Long> CRYSTALS=new LinkedHashMap<>();
    private static ClientPlayNetworkHandler session;
    private static PlayerEntity localPlayer;
    private static volatile boolean requestAnchor,requestChest;
    private static ClientPlayNetworkHandler requestSession;
    private static PlayerEntity requestPlayer;
    private static int originalSlot=-1,assignedSlot=-1,restoreAt,totemPending,forceTotemTicks,lastHurt,jumpAt=-1;
    private static int nextCrystal,nextTrigger,nextAnchor,lastManualAge=-1,sprintResetUntil=-1;
    private static boolean resumeSprint;
    private static String swapOwner="";
    private enum AnchorStage { IDLE,CHARGE,CONFIRM_CHARGE,DETONATE,CONFIRM_BLAST,REPLACE,CONFIRM_PLACE }
    private static AnchorStage anchorStage=AnchorStage.IDLE;
    private static BlockPos anchorPos;
    private static BlockState confirmedAnchorState;
    private static int anchorCycles,anchorDue,anchorTimeout;
    private static boolean manualAnchor;
    private CombatPvpService(){ }
    private static void bindRequest(){var c=MinecraftClient.getInstance();requestSession=c.getNetworkHandler();requestPlayer=c.player;}
    public static void requestDoubleAnchor(){bindRequest();requestAnchor=true;}
    public static void requestElytraSwap(){bindRequest();requestChest=true;}
    public static boolean busy(){return anchorStage!=AnchorStage.IDLE;}
    public static void shutdown(){clear();session=null;localPlayer=null;}
    private static void clear() {
        GATE.reset();ANCHOR_ACK.reset();confirmedAnchorState=null;CRYSTALS.clear();originalSlot=assignedSlot=-1;swapOwner="";
        anchorStage=AnchorStage.IDLE;anchorPos=null;anchorCycles=0;nextAnchor=nextCrystal=nextTrigger=0;
        requestAnchor=requestChest=false;totemPending=forceTotemTicks=lastHurt=0;jumpAt=-1;lastManualAge=-1;
        sprintResetUntil=-1;resumeSprint=false;
        requestSession=null;requestPlayer=null;
    }
    private static boolean usable(MinecraftClient c) {
        return c.player!=null&&c.world!=null&&c.interactionManager!=null&&c.player.isAlive()
            &&c.currentScreen==null&&!FreecamService.active()&&!c.player.isSpectator();
    }
    public static void tick(MinecraftClient c) {
        if(c.getNetworkHandler()!=session||c.player!=localPlayer) {
            boolean sameRequest=requestSession!=null&&requestSession==c.getNetworkHandler()&&requestPlayer==c.player;
            boolean anchor=sameRequest&&requestAnchor,chest=sameRequest&&requestChest;
            clear();session=c.getNetworkHandler();localPlayer=c.player;
            // GUI action requests on the first tick of an existing world are retained.
            if(c.player!=null){requestAnchor=anchor;requestChest=chest;}
        }
        GATE.advance();boolean allowed=usable(c);
        if(!allowed) {
            if(c.player!=null)restore(c,true);
            anchorStage=AnchorStage.IDLE;anchorPos=null;jumpAt=-1;
            lastHurt=c.player==null?0:c.player.hurtTime;return;
        }
        restore(c,false);tickSprintReset(c);int now=c.player.age;
        tickTotem(c);tickJump(c);
        if(requestChest){requestChest=false;if(KhanhFeatures.enabled("elytra_swap")&&!busy())swapChest(c);}
        if(tickAnchor(c))return;
        if(KhanhFeatures.enabled("auto_hit_crystal")&&!KhanhFeatures.enabled("auto_crystal")
            &&now>=nextCrystal&&health(c)>=KhanhFeatures.decimal("auto_hit_crystal","min_health",8)
            &&(!KhanhFeatures.bool("auto_hit_crystal","hold_attack",true)||c.options.attackKey.isPressed())
            &&c.crosshairTarget instanceof EntityHitResult hit&&hit.getEntity() instanceof EndCrystalEntity crystal&&reachable(c,crystal)) {
            if(attack(c,crystal))nextCrystal=now+KhanhFeatures.number("auto_hit_crystal","delay",2);
            return;
        }
        if(c.crosshairTarget instanceof EntityHitResult hit&&hit.getEntity() instanceof PlayerEntity target&&validPlayer(c,target)) {
            if(KhanhFeatures.enabled("trigger_bot")&&now>=nextTrigger
                &&c.player.getAttackCooldownProgress(.5F)>=KhanhFeatures.decimal("trigger_bot","cooldown",.95)
                &&(!KhanhFeatures.bool("trigger_bot","crit_only",false)||falling(c))) {
                if(routeAttack(c,target))nextTrigger=now+KhanhFeatures.number("trigger_bot","delay",2);
            }
        }
        CRYSTALS.entrySet().removeIf(e->GATE.tick()-e.getValue()>20);
    }
    /** Network pop cue: attempt immediately, then keep a short retry window for inventory sync ordering. */
    public static void onTotemPop(Entity entity) {
        MinecraftClient c=MinecraftClient.getInstance();
        if(c.player==null||entity!=c.player)return;
        forceTotemTicks=4;
        if(KhanhFeatures.enabled("auto_totem")&&usable(c)&&refillTotem(c)) {
            totemPending=2;forceTotemTicks=0;
        }
    }
    /** Intercepts only screenshot swap/combination modules, not unrelated vanilla hits. */
    public static boolean beforeAttack(MinecraftClient c) {
        if(!usable(c))return false;
        if(busy())return true;
        if(GATE.used()&&(KhanhFeatures.enabled("trigger_bot")||KhanhFeatures.enabled("auto_hit_crystal")))return true;
        if(!(c.crosshairTarget instanceof EntityHitResult hit)||!(hit.getEntity() instanceof PlayerEntity target)||!validPlayer(c,target))return false;
        if(!KhanhFeatures.enabled("shield_breaker")&&!KhanhFeatures.enabled("mace_swap")
            &&!KhanhFeatures.enabled("spear_swap")&&!KhanhFeatures.enabled("auto_wtap"))return false;
        if(GATE.used())return true;
        if(c.player.getAttackCooldownProgress(.5F)<.9F)return false;
        // Do not dispatch twice if a held-click tick and a vanilla input overlap.
        if(lastManualAge==c.player.age)return true;
        if(routeAttack(c,target)){lastManualAge=c.player.age;return true;}
        return false;
    }
    private static boolean routeAttack(MinecraftClient c,PlayerEntity target) {
        if(!validPlayer(c,target)||c.player.isUsingItem()||GATE.used())return false;
        int axe=find(c,s->path(s).endsWith("_axe")),mace=find(c,s->s.isOf(Items.MACE));
        String owner="";int slot=-1;
        if(KhanhFeatures.enabled("shield_breaker")&&target.isBlocking()&&axe>=0){slot=axe;owner="shield_breaker";}
        else if(KhanhFeatures.enabled("mace_swap")&&mace>=0
            &&(!KhanhFeatures.bool("mace_swap","falling_only",true)||falling(c))){slot=mace;owner="mace_swap";}
        else if(KhanhFeatures.enabled("spear_swap")
            &&(!KhanhFeatures.bool("spear_swap","falling_only",false)||falling(c))){slot=find(c,s->path(s).endsWith("_spear"));owner="spear_swap";}
        if(slot>=0)equip(c,slot,owner);
        boolean attacked=attack(c,target);
        if(attacked)startSprintReset(c);
        if(slot>=0)scheduleRestore(c,owner);
        return attacked;
    }
    public static boolean sprintResetActive(){return sprintResetUntil>=0;}
    private static void startSprintReset(MinecraftClient c) {
        if(!KhanhFeatures.enabled("auto_wtap"))return;
        boolean sprinting=c.player.isSprinting();
        if(KhanhFeatures.bool("auto_wtap","only_sprinting",true)&&!sprinting)return;
        resumeSprint=sprinting||c.options.sprintKey.isPressed();
        sprintResetUntil=c.player.age+KhanhFeatures.number("auto_wtap","reset_ticks",2);
        c.player.setSprinting(false);
    }
    private static void tickSprintReset(MinecraftClient c) {
        if(sprintResetUntil<0)return;
        if(!KhanhFeatures.enabled("auto_wtap")){sprintResetUntil=-1;resumeSprint=false;return;}
        if(c.player.age<sprintResetUntil){c.player.setSprinting(false);return;}
        if(resumeSprint&&KhanhFeatures.bool("auto_wtap","resume",true)&&c.options.forwardKey.isPressed()
            &&!c.player.isSneaking()&&!c.player.horizontalCollision&&c.player.getHungerManager().getFoodLevel()>6)c.player.setSprinting(true);
        sprintResetUntil=-1;resumeSprint=false;
    }
    private static boolean attack(MinecraftClient c,Entity target) {
        if(c.player.isUsingItem()||!reachable(c,target)||!GATE.claimAttack())return false;
        c.interactionManager.attackEntity(c.player,target);c.player.swingHand(Hand.MAIN_HAND);
        if(target instanceof LivingEntity living)KhanhFeatures.setTarget(living);
        return true;
    }
    public static boolean permitCrystalAttack(Entity target) {
        if(!(target instanceof EndCrystalEntity)||!KhanhFeatures.enabled("crystal_optimizer"))return true;
        if(!target.isAlive())return false;
        long tick=GATE.tick();Long previous=CRYSTALS.get(target.getId());
        if(previous!=null&&tick-previous<KhanhFeatures.number("crystal_optimizer","duplicate_window",2))return false;
        if(CRYSTALS.size()>=128)CRYSTALS.remove(CRYSTALS.keySet().iterator().next());
        CRYSTALS.put(target.getId(),tick);return true;
    }
    private static boolean reachable(MinecraftClient c,Entity target) {
        return target!=null&&target.isAlive()&&target.getEntityWorld()==c.world
            &&target.getBoundingBox().squaredMagnitude(c.player.getEyePos())<=Math.pow(c.player.getEntityInteractionRange(),2)
            &&c.player.canSee(target);
    }
    private static boolean validPlayer(MinecraftClient c,PlayerEntity target) {
        return target!=c.player&&!target.isSpectator()&&reachable(c,target)
            &&!(FriendService.protectsTargets()&&FriendService.isFriend(target.getGameProfile().name()));
    }
    private static boolean falling(MinecraftClient c){return !c.player.isOnGround()&&c.player.fallDistance>.1&&c.player.getVelocity().y<0&&!c.player.isGliding()&&!c.player.isTouchingWater();}
    private static double health(MinecraftClient c){return c.player.getHealth()+c.player.getAbsorptionAmount();}
    private static String path(ItemStack s){return Registries.ITEM.getId(s.getItem()).getPath();}
    private static int find(MinecraftClient c,Predicate<ItemStack> predicate){for(int i=0;i<9;i++)if(predicate.test(c.player.getInventory().getStack(i)))return i;return -1;}
    private static void select(MinecraftClient c,int slot) {
        if(slot<0||slot>8||c.player.getInventory().getSelectedSlot()==slot)return;
        c.player.getInventory().setSelectedSlot(slot);c.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }
    private static void equip(MinecraftClient c,int slot,String owner) {
        if(originalSlot<0)originalSlot=c.player.getInventory().getSelectedSlot();
        select(c,slot);assignedSlot=slot;swapOwner=owner;
    }
    private static void scheduleRestore(MinecraftClient c,String owner) {
        if(KhanhFeatures.bool(owner,"switch_back",true))restoreAt=c.player.age+KhanhFeatures.number(owner,"return_delay",2);
        else {originalSlot=assignedSlot=-1;swapOwner="";}
    }
    private static void restore(MinecraftClient c,boolean force) {
        if(originalSlot<0)return;
        if(c.player.getInventory().getSelectedSlot()!=assignedSlot){originalSlot=assignedSlot=-1;swapOwner="";return;}
        if(force||(!swapOwner.equals("double_anchor")&&!KhanhFeatures.enabled(swapOwner))||c.player.age>=restoreAt){select(c,originalSlot);originalSlot=assignedSlot=-1;swapOwner="";}
    }
    private static void tickTotem(MinecraftClient c) {
        if(totemPending>0) {
            if(c.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING))totemPending=0;
            else totemPending--;
            return;
        }
        boolean forced=forceTotemTicks>0;
        if(forceTotemTicks>0)forceTotemTicks--;
        if(!KhanhFeatures.enabled("auto_totem")
            ||c.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)||c.player.currentScreenHandler!=c.player.playerScreenHandler
            ||(!forced&&KhanhFeatures.bool("auto_totem","health_check",false)&&health(c)>KhanhFeatures.decimal("auto_totem","health",12)))return;
        if(refillTotem(c))totemPending=2;
    }
    private static boolean refillTotem(MinecraftClient c) {
        // Status 35 may arrive one client tick before the consumed offhand stack clears.
        // Report "not swapped" so the forced retry window remains alive for that tick.
        if(c.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING))return false;
        int slot=-1;for(int i=0;i<36;i++)if(c.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)){slot=i;break;}
        if(slot<0)return false;
        int screenSlot=slot<9?36+slot:slot;
        c.interactionManager.clickSlot(c.player.playerScreenHandler.syncId,screenSlot,40,SlotActionType.SWAP,c.player);
        return true;
    }
    private static void tickJump(MinecraftClient c) {
        int hurt=c.player.hurtTime;
        if(KhanhFeatures.enabled("auto_jump_reset")&&hurt>lastHurt&&c.player.isOnGround())jumpAt=c.player.age+KhanhFeatures.number("auto_jump_reset","delay",0);
        lastHurt=hurt;
        if(!KhanhFeatures.enabled("auto_jump_reset")){jumpAt=-1;return;}
        if(jumpAt>=0&&c.player.age>=jumpAt) {
            jumpAt=-1;
            if(c.player.isOnGround()&&!c.player.getAbilities().flying&&!c.player.isTouchingWater()&&!c.player.isClimbing()&&!c.player.isGliding()
                &&(!KhanhFeatures.bool("auto_jump_reset","moving_only",true)||c.player.getVelocity().horizontalLengthSquared()>.0025))c.player.jump();
        }
    }
    private static void swapChest(MinecraftClient c) {
        if(c.player.currentScreenHandler!=c.player.playerScreenHandler)return;
        boolean elytra=c.player.playerScreenHandler.getSlot(6).getStack().isOf(Items.ELYTRA);
        int slot=find(c,s->elytra?path(s).endsWith("_chestplate"):s.isOf(Items.ELYTRA));
        if(slot>=0)c.interactionManager.clickSlot(c.player.playerScreenHandler.syncId,6,slot,SlotActionType.SWAP,c.player);
    }
    private static boolean tickAnchor(MinecraftClient c) {
        if(!KhanhFeatures.enabled("double_anchor")){requestAnchor=false;if(anchorStage!=AnchorStage.IDLE)finishAnchor(c);return false;}
        if(anchorStage==AnchorStage.IDLE) {
            boolean manual=requestAnchor;requestAnchor=false;
            if(c.player.age<nextAnchor||(!manual&&!(KhanhFeatures.bool("double_anchor","hold_use",true)&&c.options.useKey.isPressed())))return false;
            if(!(c.crosshairTarget instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK||!c.world.getBlockState(hit.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR))return false;
            anchorPos=hit.getBlockPos().toImmutable();manualAnchor=manual;anchorCycles=0;
            ANCHOR_ACK.reset();confirmedAnchorState=c.world.getBlockState(anchorPos);
            if(!safeAnchor(c)){anchorPos=null;return false;}
            anchorStage=AnchorStage.CHARGE;anchorDue=c.player.age;anchorTimeout=c.player.age+30;
            originalSlot=c.player.getInventory().getSelectedSlot();assignedSlot=originalSlot;swapOwner="double_anchor";restoreAt=Integer.MAX_VALUE;
        }
        if(!safeAnchor(c)||c.player.age>anchorTimeout||c.player.getInventory().getSelectedSlot()!=assignedSlot
            ||(!manualAnchor&&!c.options.useKey.isPressed())){finishAnchor(c);return true;}
        if(c.player.age<anchorDue)return true;
        var state=c.world.getBlockState(anchorPos);int charges=state.isOf(Blocks.RESPAWN_ANCHOR)?state.get(RespawnAnchorBlock.CHARGES):0;
        BlockHitResult hit=new BlockHitResult(Vec3d.ofCenter(anchorPos),Direction.UP,anchorPos,false);
        switch(anchorStage) {
            case CHARGE -> {
                if(!state.isOf(Blocks.RESPAWN_ANCHOR)){finishAnchor(c);break;}
                if(charges>0){stage(c,AnchorStage.DETONATE);break;}
                int slot=find(c,s->s.isOf(Items.GLOWSTONE));if(slot<0){finishAnchor(c);break;}
                equip(c,slot,"double_anchor");if(interact(c,hit))stage(c,AnchorStage.CONFIRM_CHARGE);else finishAnchor(c);
            }
            case CONFIRM_CHARGE -> {
                if(ANCHOR_ACK.confirmed()&&confirmedAnchorState.isOf(Blocks.RESPAWN_ANCHOR)
                    &&confirmedAnchorState.get(RespawnAnchorBlock.CHARGES)>0)stage(c,AnchorStage.DETONATE);
            }
            case DETONATE -> {
                int slot=find(c,s->!s.isOf(Items.GLOWSTONE)&&!s.isOf(Items.RESPAWN_ANCHOR));
                if(slot<0||!state.isOf(Blocks.RESPAWN_ANCHOR)||charges<=0){finishAnchor(c);break;}
                equip(c,slot,"double_anchor");if(interact(c,hit))stage(c,AnchorStage.CONFIRM_BLAST);else finishAnchor(c);
            }
            case CONFIRM_BLAST -> {
                if(ANCHOR_ACK.confirmed()&&!confirmedAnchorState.isOf(Blocks.RESPAWN_ANCHOR)) {
                    if(++anchorCycles>=2||!KhanhFeatures.bool("double_anchor","replace",true))finishAnchor(c);
                    else stage(c,AnchorStage.REPLACE);
                }
            }
            case REPLACE -> {
                BlockPos support=anchorPos.down();int slot=find(c,s->s.isOf(Items.RESPAWN_ANCHOR));
                if(slot<0||!state.isReplaceable()||!c.world.getBlockState(support).isSolidBlock(c.world,support)){finishAnchor(c);break;}
                equip(c,slot,"double_anchor");
                var place=new BlockHitResult(Vec3d.ofCenter(support).add(0,.5,0),Direction.UP,support,false);
                if(interact(c,place))stage(c,AnchorStage.CONFIRM_PLACE);else finishAnchor(c);
            }
            case CONFIRM_PLACE -> {if(ANCHOR_ACK.confirmed()&&confirmedAnchorState.isOf(Blocks.RESPAWN_ANCHOR))stage(c,AnchorStage.CHARGE);}
            case IDLE -> { }
        }
        return true;
    }
    private static void stage(MinecraftClient c,AnchorStage next){anchorStage=next;anchorDue=c.player.age+KhanhFeatures.number("double_anchor","delay",2);anchorTimeout=c.player.age+30;}
    private static boolean safeAnchor(MinecraftClient c) {
        if(anchorPos==null||c.world.getRegistryKey()==World.NETHER||c.player.getOffHandStack().isOf(Items.GLOWSTONE)
            ||health(c)<KhanhFeatures.decimal("double_anchor","min_health",12))return false;
        double distance=Vec3d.ofCenter(anchorPos).distanceTo(c.player.getEyePos());
        return distance<=c.player.getBlockInteractionRange()
            &&Vec3d.ofCenter(anchorPos).distanceTo(c.player.getEntityPos())>=KhanhFeatures.decimal("double_anchor","self_distance",3);
    }
    public static void serverAnchorUpdate(ClientPlayNetworkHandler handler,BlockPos pos,BlockState state) {
        if(handler==session&&anchorStage!=AnchorStage.IDLE&&pos.equals(anchorPos)){confirmedAnchorState=state;ANCHOR_ACK.serverUpdate();}
    }
    private static boolean interact(MinecraftClient c,BlockHitResult hit){ANCHOR_ACK.beginAction();var result=c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,hit);if(result.isAccepted())c.player.swingHand(Hand.MAIN_HAND);return result.isAccepted();}
    private static void finishAnchor(MinecraftClient c){anchorStage=AnchorStage.IDLE;anchorPos=null;nextAnchor=c.player.age+10;restore(c,true);}
}
