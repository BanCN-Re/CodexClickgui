package mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import wtf.carnation.Client;
import wtf.carnation.module.Module;
import wtf.carnation.module.impl.render.NameProtect;

/** Rewrites the local player name in incoming chat when NameProtect is on. */
@Mixin(ChatComponent.class)
public class MixinChatComponent {

    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"), argsOnly = true, index = 1)
    private Component carnation$nameProtect(Component message) {
        if (Client.instance == null || message == null) return message;
        Module module = Client.instance.getModuleManager().getModuleByName("NameProtect");
        if (!(module instanceof NameProtect nameProtect) || !nameProtect.isEnabled()) return message;

        var player = Minecraft.getInstance().player;
        if (player == null) return message;
        String realName = player.getName().getString();
        if (realName == null || realName.isEmpty()) return message;
        String text = message.getString();
        if (!text.contains(realName)) return message;
        return Component.literal(text.replace(realName, nameProtect.alias()));
    }
}
