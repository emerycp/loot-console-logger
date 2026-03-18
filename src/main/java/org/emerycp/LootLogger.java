package org.emerycp;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;

@Slf4j
@PluginDescriptor(
	name = "Loot Console Logger",
	description = "Let you log loot drops in your message box."
)
public class LootLogger extends Plugin {

	private static final String SEPARATOR = " / ";
	private static final String MESSAGE_SUFFIX = ".";

	@Inject
	private Client client;

	@Inject
	private LootLoggerConfig config;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Subscribe
	public void onLootReceived(LootReceived event) {
		if (!config.dropEnabled() && !config.highlightEnabled()) {
			return;
		}

		final Collection<ItemStack> items = event.getItems();
		if (items == null || items.isEmpty()) {
			return;
		}

		final String mobKilled = event.getName();
		final List<String> ignoredMonsters = parseConfigList(
			config.getIgnoreMonster()
		);

		final StringBuilder itemStack = new StringBuilder();
		final List<String> ignoredItems = parseConfigList(
			config.getIgnoreList()
		);

		final StringBuilder highlightStack = new StringBuilder();
		final List<String> highlightedItems = parseConfigList(
			config.getHighlightList()
		);

		for (ItemStack item : items) {
			final String itemName = getItemName(item.getId());
			final String normalizedItemName = normalize(itemName);

			if (
				config.dropEnabled() &&
				!containsMatch(ignoredItems, normalizedItemName)
			) {
				appendItem(itemStack, item.getQuantity(), itemName);
			}

			if (
				config.highlightEnabled() &&
				containsMatch(highlightedItems, normalizedItemName)
			) {
				appendItem(highlightStack, item.getQuantity(), itemName);
			}
		}

		if (
			config.dropEnabled() &&
			!containsMatch(ignoredMonsters, normalize(mobKilled)) &&
			itemStack.length() > 0
		) {
			sendMessage(mobKilled, itemStack.toString() + MESSAGE_SUFFIX);
		}

		if (config.highlightEnabled() && highlightStack.length() > 0) {
			sendHighlightMessage(highlightStack.toString() + MESSAGE_SUFFIX);
		}
	}

	private void sendMessage(String mobKilled, String itemStack) {
		final String message = "Drop from " + mobKilled + ": " + itemStack;
		queueConsoleMessage(config.getDropColor(), message);
	}

	private void sendHighlightMessage(String highlightStack) {
		String message = config.getHighlightMessage();
		message = message == null ? "" : message.trim();
		message += !message.isEmpty() ? ": " : "";

		queueConsoleMessage(
			config.getHighlightColor(),
			message + highlightStack
		);
	}

	private void queueConsoleMessage(Color color, String message) {
		final String formattedMessage = new ChatMessageBuilder()
			.append(color, message)
			.build();

		chatMessageManager.queue(
			QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(formattedMessage)
				.build()
		);
	}

	private String getItemName(int itemId) {
		try {
			final ItemComposition itemDefinition = client.getItemDefinition(
				itemId
			);
			if (itemDefinition == null) {
				return "Item " + itemId;
			}

			final String itemName = itemDefinition.getName();
			if (itemName == null || itemName.trim().isEmpty()) {
				return "Item " + itemId;
			}

			return itemName;
		} catch (RuntimeException ex) {
			log.debug(
				"Unable to resolve item definition for id {}",
				itemId,
				ex
			);
			return "Item " + itemId;
		}
	}

	private void appendItem(
		StringBuilder builder,
		int quantity,
		String itemName
	) {
		if (builder.length() > 0) {
			builder.append(SEPARATOR);
		}

		builder.append(quantity).append("x ").append(itemName);
	}

	private boolean containsMatch(List<String> patterns, String value) {
		if (value == null || value.isEmpty()) {
			return false;
		}

		for (String pattern : patterns) {
			if (value.contains(pattern)) {
				return true;
			}
		}

		return false;
	}

	private List<String> parseConfigList(String configValue) {
		final List<String> values = new ArrayList<>();
		if (configValue == null || configValue.trim().isEmpty()) {
			return values;
		}

		for (String entry : configValue.split(",")) {
			final String normalized = normalize(entry);
			if (!normalized.isEmpty()) {
				values.add(normalized);
			}
		}

		return values;
	}

	private String normalize(String value) {
		if (value == null) {
			return "";
		}

		final String lowerCased = value.toLowerCase();
		final StringBuilder normalized = new StringBuilder(lowerCased.length());
		for (int i = 0; i < lowerCased.length(); i++) {
			final char currentCharacter = lowerCased.charAt(i);
			if (!Character.isWhitespace(currentCharacter)) {
				normalized.append(currentCharacter);
			}
		}

		return normalized.toString();
	}

	@Provides
	LootLoggerConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(LootLoggerConfig.class);
	}
}
