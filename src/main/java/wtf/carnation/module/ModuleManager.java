package wtf.carnation.module;

import lombok.Getter;
import wtf.carnation.event.EventTarget;
import wtf.carnation.event.impl.input.KeyEvent;
import wtf.carnation.module.impl.combat.AntiBots;
import wtf.carnation.module.impl.combat.AttackCrystal;
import wtf.carnation.module.impl.combat.AutoClicker;
import wtf.carnation.module.impl.combat.AutoHeal;
import wtf.carnation.module.impl.combat.AutoThrow;
import wtf.carnation.module.impl.combat.AimAssist;
import wtf.carnation.module.impl.combat.Criticals;
import wtf.carnation.module.impl.combat.GhostHand;
import wtf.carnation.module.impl.combat.KillAura;
import wtf.carnation.module.impl.combat.Velocity;
import wtf.carnation.module.impl.movement.AutoMLG;
import wtf.carnation.module.impl.movement.Blink;
import wtf.carnation.module.impl.movement.FastCobweb;
import wtf.carnation.module.impl.movement.FastWeb;
import wtf.carnation.module.impl.movement.Flight;
import wtf.carnation.module.impl.movement.InventoryMove;
import wtf.carnation.module.impl.movement.LegitSpeed;
import wtf.carnation.module.impl.movement.LongJump;
import wtf.carnation.module.impl.movement.NoFall;
import wtf.carnation.module.impl.movement.NoJumpDelay;
import wtf.carnation.module.impl.movement.NoSlow;
import wtf.carnation.module.impl.movement.SafeWalk;
import wtf.carnation.module.impl.movement.Speed;
import wtf.carnation.module.impl.movement.Sprint;
import wtf.carnation.module.impl.movement.StopMove;
import wtf.carnation.module.impl.movement.Stuck;
import wtf.carnation.module.impl.movement.TargetStrafe;
import wtf.carnation.module.impl.misc.AntiFireball;
import wtf.carnation.module.impl.misc.AutoTools;
import wtf.carnation.module.impl.misc.ClientFriend;
import wtf.carnation.module.impl.misc.FastPlace;
import wtf.carnation.module.impl.misc.KillSay;
import wtf.carnation.module.impl.misc.MidPearl;
import wtf.carnation.module.impl.misc.Spammer;
import wtf.carnation.module.impl.misc.Teams;
import wtf.carnation.module.impl.render.AntiBlindness;
import wtf.carnation.module.impl.render.AntiNausea;
import wtf.carnation.module.impl.render.BedPlates;
import wtf.carnation.module.impl.render.ChestESP;
import wtf.carnation.module.impl.render.ClickGui;
import wtf.carnation.module.impl.render.Compass;
import wtf.carnation.module.impl.render.EffectDisplay;
import wtf.carnation.module.impl.render.FullBright;
import wtf.carnation.module.impl.render.Glow;
import wtf.carnation.module.impl.render.HotKeys;
import wtf.carnation.module.impl.render.ItemTags;
import wtf.carnation.module.impl.render.ItemPhysics;
import wtf.carnation.module.impl.render.Interface;
import wtf.carnation.module.impl.render.Island;
import wtf.carnation.module.impl.render.LowFire;
import wtf.carnation.module.impl.render.ModuleList;
import wtf.carnation.module.impl.render.MotionBlur;
import wtf.carnation.module.impl.render.NameProtect;
import wtf.carnation.module.impl.render.NameTags;
import wtf.carnation.module.impl.render.NoFOV;
import wtf.carnation.module.impl.render.NoHurtCam;
import wtf.carnation.module.impl.render.NoRender;
import wtf.carnation.module.impl.render.PearlInfo;
import wtf.carnation.module.impl.render.PostProcess;
import wtf.carnation.module.impl.render.ProjectileESP;
import wtf.carnation.module.impl.render.Rotation;
import wtf.carnation.module.impl.render.Scoreboard;
import wtf.carnation.module.impl.render.ScoreboardSpoof;
import wtf.carnation.module.impl.render.TargetInfo;
import wtf.carnation.module.impl.render.TimeChanger;
import wtf.carnation.module.impl.render.ViewClip;
import wtf.carnation.module.impl.render.Watermark;
import wtf.carnation.module.value.Value;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    @Getter
    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        registerModules(
                new Sprint(), new SafeWalk(), new NoJumpDelay(), new StopMove(),
                new LegitSpeed(), new Speed(), new Flight(), new NoFall(),
                new FastCobweb(), new FastWeb(), new NoSlow(), new LongJump(), new Stuck(),
                new Blink(), new AutoMLG(),
                new KillSay(), new MidPearl(), new Spammer(), new Teams(),
                new AntiFireball(), new ClientFriend(), new FastPlace(), new AutoTools(),
                new InventoryMove(), new TargetStrafe(),
                new AutoClicker(), new AutoHeal(), new AntiBots(), new GhostHand(), new AttackCrystal(),
                new AimAssist(), new Criticals(), new KillAura(), new Velocity(), new AutoThrow(),
                new Watermark(), new ModuleList(), new Compass(), new EffectDisplay(),
                new PearlInfo(), new TargetInfo(), new HotKeys(),
                new NameTags(), new ItemTags(), new ProjectileESP(), new Glow(),
                new BedPlates(), new ChestESP(),
                new FullBright(), new NoFOV(), new NoHurtCam(), new LowFire(),
                new ViewClip(), new AntiBlindness(), new AntiNausea(),
                new NameProtect(), new TimeChanger(), new NoRender(),
                new Scoreboard(), new ScoreboardSpoof(), new ItemPhysics(),
                new PostProcess(), new MotionBlur(), new Island(), new Interface(),
                new Rotation(),
                new ClickGui());
    }

    private void registerModules(Module... modules) {
        for (Module module : modules) {
            registerModule(module);
        }
    }

    public void registerModule(Module module) {
        if (getModuleByName(module.getName()) != null) {
            throw new IllegalArgumentException("Module already registered: " + module.getName());
        }
        // Every Value field becomes a setting, in declaration order.
        for (Field field : module.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                if (field.get(module) instanceof Value<?> value) module.addValues(value);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        modules.add(module);
    }

    public void unregisterModule(Module module) {
        if (module == null) return;
        if (module.isEnabled()) module.setState(false);
        modules.remove(module);
    }

    public Module getModuleByName(String name) {
        if (name == null) return null;
        for (Module module : modules) {
            if (module.getName().equalsIgnoreCase(name)) return module;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> type) {
        for (Module module : modules) {
            if (type.isInstance(module)) return (T) module;
        }
        return null;
    }

    public List<Module> getModulesByCategory(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.getCategory() == category) result.add(module);
        }
        return result;
    }

    @EventTarget
    public void onKey(KeyEvent e) {
        if (!e.state()) return;
        for (Module module : modules) {
            if (module.getKey() != 0 && module.getKey() == e.key()) {
                module.toggle();
            }
        }
    }
}
