package org.emerycp;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
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
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Loot Console Logger",
	description = "Let you log loot drops in your message box."
)
public class LootLogger extends Plugin {

	private static final String CONFIG_GROUP = "LootConsoleLogger";
	private static final String SEPARATOR = " / ";
	private static final String MESSAGE_SUFFIX = ".";

	@Inject
	private Client client;

	@Inject
	private LootLoggerConfig config;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private LootLoggerPanel panel;

	@Inject
	private LootSessionTracker sessionTracker;

	private NavigationButton navigationButton;

	@Override
	protected void startUp() {
		final BufferedImage icon = ImageUtil.loadImageResource(
			LootLogger.class,
			"icon.png"
		);

		navigationButton = NavigationButton.builder()
			.tooltip("Loot Logger")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navigationButton);
		panel.refresh();
	}

	@Override
	protected void shutDown() {
		if (navigationButton != null) {
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}

		sessionTracker.reset();
		panel.refresh();
	}

	@Subscribe
	public void onLootReceived(LootReceived event) {
		if (!isTrackingEnabled()) {
			return;
		}

		final Collection<ItemStack> items = event.getItems();
		if (items == null || items.isEmpty()) {
			return;
		}

		final String mobKilled = event.getName();
		final ProcessedLoot processedLoot = processLootItems(items);

		if (shouldRecordRegularDrop(mobKilled, processedLoot)) {
			recordRegularDrop(mobKilled, processedLoot);
		}

		if (
			config.highlightEnabled() &&
			config.highlightConsoleMessageEnabled() &&
			processedLoot.highlightStack.length() > 0
		) {
			sendHighlightMessage(processedLoot.highlightStack.toString() + MESSAGE_SUFFIX);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!CONFIG_GROUP.equals(event.getGroup())) {
			return;
		}

		panel.syncRuleEditorsFromConfig();
		panel.refresh();
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

	private boolean isTrackingEnabled() {
		return config.dropEnabled() || config.highlightEnabled();
	}

	private ProcessedLoot processLootItems(Collection<ItemStack> items) {
		final ProcessedLoot processedLoot = new ProcessedLoot();
		final List<String> ignoredItems = parseConfigList(config.getIgnoreList());
		final RuleMatchMode ignoredItemMatchMode = config.getIgnoreItemMatchMode();
		final List<String> highlightedItems = parseConfigList(config.getHighlightList());
		final RuleMatchMode highlightMatchMode = config.getHighlightMatchMode();

		for (ItemStack item : items) {
			final String itemName = getItemName(item.getId());
			final String normalizedItemName = normalize(itemName);
			final int geValue = getGeValue(item);
			final int haValue = getHaValue(item);
			final boolean manuallyIgnored = containsMatch(
				ignoredItems,
				ignoredItemMatchMode,
				normalizedItemName,
				item.getId()
			);
			final boolean manuallyHighlighted = containsMatch(
				highlightedItems,
				highlightMatchMode,
				normalizedItemName,
				item.getId()
			);

			if (config.dropEnabled() && !manuallyIgnored && !shouldAutoIgnore(item.getQuantity(), geValue, haValue)) {
				appendItem(processedLoot.itemStack, item.getQuantity(), itemName);
				processedLoot.sessionItems.add(
					new LootSessionTracker.LootItemSummary(itemName, item.getQuantity(), geValue, haValue)
				);
				processedLoot.sessionGeValue += geValue;
				processedLoot.sessionHaValue += haValue;
			}

			if (config.highlightEnabled() && (manuallyHighlighted || shouldAutoHighlight(item.getQuantity(), geValue, haValue))) {
				appendItem(processedLoot.highlightStack, item.getQuantity(), itemName);
				processedLoot.highlightedItemNames.add(itemName);
			}
		}

		return processedLoot;
	}

	private boolean shouldRecordRegularDrop(String source, ProcessedLoot processedLoot) {
		if (!config.dropEnabled() || processedLoot.itemStack.length() == 0) {
			return false;
		}

		return !containsMatch(
			parseConfigList(config.getIgnoreMonster()),
			config.getIgnoreMonsterMatchMode(),
			normalize(source),
			-1
		);
	}

	private void recordRegularDrop(String source, ProcessedLoot processedLoot) {
		final String itemStack = processedLoot.itemStack.toString();
		if (config.dropConsoleMessageEnabled()) {
			sendMessage(source, itemStack + MESSAGE_SUFFIX);
		}

		sessionTracker.recordDrop(
			source,
			itemStack,
			processedLoot.sessionItems,
			processedLoot.highlightedItemNames,
			processedLoot.sessionGeValue,
			processedLoot.sessionHaValue
		);
		panel.refresh();
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

	private boolean containsMatch(
		List<String> patterns,
		RuleMatchMode matchMode,
		String value,
		int itemId
	) {
		if (value == null || value.isEmpty()) {
			return false;
		}

		for (String pattern : patterns) {
			if (matchesPattern(pattern, matchMode, value, itemId)) {
				return true;
			}
		}

		return false;
	}

	private boolean matchesPattern(
		String pattern,
		RuleMatchMode matchMode,
		String value,
		int itemId
	) {
		if (pattern == null || pattern.isEmpty()) {
			return false;
		}

		if (pattern.startsWith("id:")) {
			return itemId >= 0 && pattern.equals("id:" + itemId);
		}

		switch (matchMode) {
			case EXACT:
				return value.equals(pattern);
			case STARTS_WITH:
				return value.startsWith(pattern);
			case CONTAINS:
			default:
				return value.contains(pattern);
		}
	}

	private boolean shouldAutoIgnore(
		int quantity,
		int geValue,
		int haValue
	) {
		return isEnabledThreshold(config.getDropMinQuantity()) &&
			quantity < config.getDropMinQuantity() ||
			isEnabledThreshold(config.getDropMinGeValue()) &&
			geValue < config.getDropMinGeValue() ||
			isEnabledThreshold(config.getDropMinHaValue()) &&
			haValue < config.getDropMinHaValue();
	}

	private boolean shouldAutoHighlight(
		int quantity,
		int geValue,
		int haValue
	) {
		return isEnabledThreshold(config.getHighlightMinQuantity()) &&
			quantity >= config.getHighlightMinQuantity() ||
			isEnabledThreshold(config.getHighlightMinGeValue()) &&
			geValue >= config.getHighlightMinGeValue() ||
			isEnabledThreshold(config.getHighlightMinHaValue()) &&
			haValue >= config.getHighlightMinHaValue();
	}

	private boolean isEnabledThreshold(int threshold) {
		return threshold > 0;
	}

	private int getGeValue(ItemStack item) {
		try {
			final long value =
				(long) itemManager.getItemPrice(item.getId()) * item.getQuantity();
			return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
		} catch (RuntimeException ex) {
			log.debug("Unable to resolve GE value for item id {}", item.getId(), ex);
			return 0;
		}
	}

	private int getHaValue(ItemStack item) {
		try {
			final ItemComposition itemDefinition = client.getItemDefinition(
				item.getId()
			);
			if (itemDefinition == null) {
				return 0;
			}

			final long value =
				(long) itemDefinition.getHaPrice() * item.getQuantity();
			return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
		} catch (RuntimeException ex) {
			log.debug("Unable to resolve HA value for item id {}", item.getId(), ex);
			return 0;
		}
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

	private static class ProcessedLoot {
		private final StringBuilder itemStack = new StringBuilder();
		private final StringBuilder highlightStack = new StringBuilder();
		private final List<LootSessionTracker.LootItemSummary> sessionItems = new ArrayList<>();
		private final List<String> highlightedItemNames = new ArrayList<>();
		private int sessionGeValue;
		private int sessionHaValue;
	}
}
