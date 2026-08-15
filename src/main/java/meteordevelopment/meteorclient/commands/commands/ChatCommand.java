/*
 * This file is part of EyEye Client.
 * Copyright (c) whileai and gpt.
 */

package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.GlobalChat;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class ChatCommand extends Command {
    public ChatCommand() {
        super("chat", "Sends a message to EyEye users on this server.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(argument("message", StringArgumentType.greedyString()).executes(context -> {
            Modules.get().get(GlobalChat.class).send(StringArgumentType.getString(context, "message"));
            return SINGLE_SUCCESS;
        }));
    }
}
