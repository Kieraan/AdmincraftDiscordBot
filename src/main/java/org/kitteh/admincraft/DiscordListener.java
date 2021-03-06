/*
 * * Copyright (C) 2018 Matt Baxter http://kitteh.org
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.kitteh.admincraft;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageSendEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.reaction.ReactionAddEvent;
import sx.blah.discord.handle.impl.events.guild.member.NicknameChangedEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserJoinEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserLeaveEvent;
import sx.blah.discord.handle.impl.events.shard.ShardReadyEvent;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;
import sx.blah.discord.handle.obj.ActivityType;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.handle.obj.StatusType;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.MessageHistory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Iterator;
import java.util.List;

/**
 * Discord event listening.
 */
public class DiscordListener {
    private static final ReactionEmoji TOOT_TOOT = ReactionEmoji.of("\uD83C\uDFBA"); // Trumpet emoji
    private static final long ADMINCRAFT_ROLE = 470781969978884117L;
    private static final String UPBOAT = "upvote";
    private static final String DOWNBOAT = "downvote";

    @EventSubscriber
    public void login(ShardReadyEvent event) {
        Admincraft.queue(() -> event.getClient().changeUsername(Admincraft.config.getName()));
        Admincraft.queue(() -> event.getClient().changePresence(StatusType.ONLINE, ActivityType.PLAYING, "Minecraft!"));
        try {
            IChannel channel = event.getClient().getChannelByID(Admincraft.config.getRoleChannelId());
            IGuild guild = channel.getGuild();
            MessageHistory history = channel.getFullMessageHistory();
            Iterator<IMessage> it = history.iterator();
            IUser self = event.getClient().getOurUser();
            while (it.hasNext()) {
                IMessage message = it.next();
                if (message.getContent().contains("Are you a ")) {
                    String part = message.getContent().substring(message.getContent().indexOf("Are you a ") + "Are you a ".length());
                    String roleName = part.substring(0, part.indexOf('?'));
                    List<IRole> roles = channel.getGuild().getRolesByName(roleName);
                    if (roles.size() == 1) {
                        IRole role = roles.get(0);
                        for (IUser user : message.getReactionByEmoji(TOOT_TOOT).getUsers()) {
                            if (user != null && !self.equals(user)) {
                                if (channel.getGuild().getUsersByRole(role).contains(user)) {
                                    Admincraft.queue(() -> user.removeRole(role));
                                } else {
                                    Admincraft.queue(() -> user.addRole(role));
                                }
                                Admincraft.queue(() -> message.removeReaction(user, TOOT_TOOT));
                            }
                        }
                    }
                }
            }
            IRole active = guild.getRoleByID(ADMINCRAFT_ROLE);
            List<IUser> activeUsers = guild.getUsersByRole(active);
            guild.getRoles().stream()
                    .flatMap(role -> guild.getUsersByRole(role).stream())
                    .distinct()
                    .filter(user -> !activeUsers.contains(user) && user.getRolesForGuild(guild).size() > 1)
                    .forEach(user -> Admincraft.queue(() -> user.addRole(active)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventSubscriber
    public void message(MessageSendEvent event) {
        if (event.getChannel().getLongID() == Admincraft.config.getPostChannelId()) {
            Admincraft.queue(() -> event.getMessage().addReaction(ReactionEmoji.of(event.getGuild().getEmojiByName(UPBOAT))));
        }
    }

    @EventSubscriber
    public void messageReceived(MessageReceivedEvent event) {
        if (event.getChannel().getLongID() == Admincraft.config.getRoleChannelId()) {
            event.getMessage().addReaction(TOOT_TOOT);
        }
    }

    @EventSubscriber
    public void react(ReactionAddEvent event) {
        if (event.getUser().equals(event.getClient().getOurUser())) {
            return; // Hey it's me
        }
        if (event.getChannel().getLongID() == Admincraft.config.getPostChannelId() &&
                event.getUser().equals(event.getClient().getOurUser()) &&
                event.getReaction().getEmoji().equals(ReactionEmoji.of(event.getGuild().getEmojiByName(UPBOAT)))) {
            Admincraft.queue(() -> event.getMessage().addReaction(ReactionEmoji.of(event.getGuild().getEmojiByName(DOWNBOAT))));
        }
        if (event.getChannel().getLongID() == Admincraft.config.getRoleChannelId()) {
            IChannel channel = event.getChannel();
            IMessage message = event.getMessage();
            IUser user = event.getUser();
            if (message.getContent().contains("Are you a ")) {
                String part = message.getContent().substring(message.getContent().indexOf("Are you a ") + "Are you a ".length());
                String roleName = part.substring(0, part.indexOf('?'));
                List<IRole> roles = channel.getGuild().getRolesByName(roleName);
                if (roles.size() == 1) {
                    IRole role = roles.get(0);
                    if (event.getGuild().getUsersByRole(role).contains(user)) {
                        Admincraft.queue(() -> user.removeRole(role));
                        if (user.getRolesForGuild(event.getGuild()).size() < 3) {
                            Admincraft.queue(() -> user.removeRole(event.getGuild().getRoleByID(ADMINCRAFT_ROLE)));
                        }
                    } else {
                        Admincraft.queue(() -> user.addRole(role));
                    }
                    Admincraft.queue(() -> event.getMessage().removeReaction(user, TOOT_TOOT));
                }
            }
        }
    }

    @EventSubscriber
    public void join(UserJoinEvent event) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.withColor(50, 150, 75);
        builder.withFooterText(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(event.getJoinTime()));
        builder.appendField("Joined:", event.getUser().getName(), true);
        Admincraft.log(event, builder.build());
    }

    @EventSubscriber
    public void leave(UserLeaveEvent event) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.withColor(150, 50, 75);
        builder.withFooterText(LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.MEDIUM)));
        builder.appendField("Left:", event.getUser().getName(), true);
        Admincraft.log(event, builder.build());
    }

    @EventSubscriber
    public void nick(NicknameChangedEvent event) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.withAuthorName("Nickname change!");
        builder.withColor(66, 134, 244);
        builder.withFooterText(LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.MEDIUM)));
        builder.appendField("Old nick:", event.getOldNickname().orElse(event.getUser().getName()), true);
        builder.appendField("New nick:", event.getNewNickname().orElse(event.getUser().getName()), true);
        Admincraft.log(event, builder.build());
    }
}
