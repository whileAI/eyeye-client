/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.GlobalChat;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class ChatStatusCommand extends Command {
    public ChatStatusCommand() {
        super("chat-status", "Enables or disables EyEye Chat.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(argument("enabled", BoolArgumentType.bool()).executes(context -> {
            boolean enabled = BoolArgumentType.getBool(context, "enabled");
            GlobalChat chat = Modules.get().get(GlobalChat.class);
            if (enabled) chat.enable();
            else chat.disable();
            info("EyEye Chat %s.", enabled ? "enabled" : "disabled");
            return SINGLE_SUCCESS;
        }));
    }
}
