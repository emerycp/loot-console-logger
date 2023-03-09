package org.emerycp;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;

import java.util.Collection;

@Slf4j
@PluginDescriptor(
	name = "Loot Console Logger",
	description = "Let you log loot drops in your message box."
)
public class LootLogger extends Plugin
{

	String mobKilled = "";
	String itemStack = "";
	String highlightStack = "";

	@Inject
	private Client client;

	@Inject
	private LootLoggerConfig config;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		itemStack = "";
		highlightStack = "";
		mobKilled = event.getName();

		// Configs
		String[] ignoreList = format(config.getIgnoreList()).split(",");
		String[] ignoreMonsterList = format(config.getIgnoreMonster()).split(",");
		String[] highlightList = format(config.getHighlightList()).split(",");

		Collection<ItemStack> iS = event.getItems();
		for (ItemStack i: iS) {
			final String currentName = client.getItemDefinition(i.getId()).getName();
			final String cleanCurrentName = format(currentName);

			//  Drop
			if(config.dropEnabled() && !find(ignoreList, cleanCurrentName))
			{
				if(!itemStack.isEmpty())
					itemStack += " / ";
				itemStack +=  (i.getQuantity()+"") + "x " + currentName;
			}

			// Highlight
			if(config.highlightEnabled() && find(highlightList, cleanCurrentName))
			{
				if(!highlightStack.isEmpty())
					highlightStack += " / ";
				highlightStack += (i.getQuantity()+"") + "x " + currentName;
			}
		}

		if(config.dropEnabled() && !find(ignoreMonsterList, format(mobKilled)) && !itemStack.isEmpty())
		{
			itemStack += ".";
			sendMessage();
		}

		if(config.highlightEnabled() && !highlightStack.isEmpty())
		{
			highlightStack += ".";
			sendHighlightMessage();
		}
	}
	public void sendMessage() {

		final String s = "Drop from " + mobKilled + ": " + itemStack;

		client.addChatMessage(ChatMessageType.CONSOLE, "", s, null);
	}

	public void sendHighlightMessage() {

		String message = config.getHighlightMessage().trim();
		message += !message.isEmpty() ? ": " : "";

		final String formattedMessage = new ChatMessageBuilder()
				.append(config.getHighlightColor(), message + highlightStack)
				.build();

		chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(formattedMessage)
				.build());
	}

	public boolean find(String[] st, String s) {
		for (String t:
			 st) {
			if(!t.isEmpty() && s.contains(t))
				return true;
		}
		return false;
	}

	public String format(String toFormat)
	{
		return toFormat.toLowerCase().replaceAll("\\s", "");
	}

	@Provides
	LootLoggerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LootLoggerConfig.class);
	}
}
