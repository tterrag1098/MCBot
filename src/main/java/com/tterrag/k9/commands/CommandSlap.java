package com.tterrag.k9.commands;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import com.google.common.collect.Lists;
import com.google.gson.reflect.TypeToken;
import com.tterrag.k9.commands.api.Argument;
import com.tterrag.k9.commands.api.Command;
import com.tterrag.k9.commands.api.CommandContext;
import com.tterrag.k9.commands.api.CommandPersisted;
import com.tterrag.k9.commands.api.Flag;
import com.tterrag.k9.util.ListMessageBuilder;
import com.tterrag.k9.util.Requirements;
import com.tterrag.k9.util.Requirements.RequiredType;

import discord4j.core.object.util.Permission;
import reactor.core.publisher.Mono;

@Command
public class CommandSlap extends CommandPersisted<List<String>> {
    
    private static final Flag FLAG_ADD = new SimpleFlag('a', "add", "Adds a new slap.", true);
    private static final Flag FLAG_REMOVE = new SimpleFlag('r', "remove", "Removes a slap.", true);
    private static final Flag FLAG_LS = new SimpleFlag('l', "ls", "Lists all current slap strings.", false);
    
    private static final Argument<String> ARG_TARGET = new SentenceArgument("target", "The target of the slap.", true) {
        
        @Override
        public boolean required(Collection<Flag> flags) {
            return flags.isEmpty();
        }
    };
    
    private static final Requirements ADD_PERMS = Requirements.builder().with(Permission.MANAGE_MESSAGES, RequiredType.ALL_OF).build();
    
    private static final int PER_PAGE = 10;
    
    private static final List<String> DEFAULTS = Arrays.asList("with a large trout!", "with a big bat!", "with a frying pan!");

    private final Random rand = new Random();

    public CommandSlap() {
        super("slap", false, () -> Lists.newArrayList(DEFAULTS));
    }
    
    @Override
    public TypeToken<List<String>> getDataType() {
        return new TypeToken<List<String>>(){};
    }
    
    @Override
    public Mono<?> process(CommandContext ctx) {
        if (ctx.hasFlag(FLAG_LS)) {
            return storage.get(ctx).flatMap(data -> new ListMessageBuilder<String>("custom slap suffixes").addObjects(data).objectsPerPage(PER_PAGE).build(ctx).send());
        }

        if (ctx.hasFlag(FLAG_ADD)) {
            return ADD_PERMS.matches(ctx)
                    .filter(b -> b)
                    .switchIfEmpty(ctx.error("You do not have permission to add slaps!"))
                    .flatMap($ -> storage.get(ctx))
                    .switchIfEmpty(ctx.error("Cannot add slap suffixes in DMs."))
                    .doOnNext(list -> list.add(ctx.getFlag(FLAG_ADD)))
                    .flatMap($ -> ctx.reply("Added new slap suffix."));
        }
        if (ctx.hasFlag(FLAG_REMOVE)) {
            return ADD_PERMS.matches(ctx)
                    .filter(b -> b)
                    .switchIfEmpty(ctx.error("You do not have permission to remove slaps!"))
                    .map($ -> Integer.parseInt(ctx.getFlag(FLAG_REMOVE)) - 1)
                    .onErrorResume(NumberFormatException.class, $ -> ctx.error("Not a valid number."))
                    .flatMap(idx -> storage.get(ctx)
                            .switchIfEmpty(ctx.error("Cannot remove slap suffixes in DMs."))
                            .filter(suffixes -> idx >= 0 && idx < suffixes.size())
                            .switchIfEmpty(ctx.error("Index out of range."))
                            .flatMap(suffixes -> ctx.reply("Removed slap suffix: \"" + suffixes.remove(idx.intValue()) + '"')));
        }

        String target = ctx.getArg(ARG_TARGET).trim();
        
        return Mono.zip(ctx.getClient().getSelf().flatMap(ctx::getDisplayName), 
                        ctx.getMessage().getUserMentions().any(u -> u.getId().equals(ctx.getClient().getSelfId().get())),
                        ctx.getDisplayName(),
                        storage.get(ctx).defaultIfEmpty(DEFAULTS))
                .flatMap(t -> {
                    boolean nou = target.equalsIgnoreCase(t.getT1()) || t.getT2();
                    StringBuilder builder = new StringBuilder(nou ? target : t.getT3());
                    builder.append(" slapped ").append(nou ? t.getT3() : target).append(" " + t.getT4().get(rand.nextInt(t.getT4().size())));
                    return ctx.reply(builder.toString());
                });
    }
    
    @Override
    public String getDescription(CommandContext ctx) {
        return "For when someone just needs a good slap.";
    }
}
