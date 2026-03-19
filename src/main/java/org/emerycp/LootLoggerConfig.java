package org.emerycp;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("LootConsoleLogger")
public interface LootLoggerConfig extends Config {
	@ConfigSection(
		name = "Loot Drop Log",
		description = "All options that log loot drop in your message box.",
		position = 0,
		closedByDefault = false
	)
	String dropSection = "drop";

	@ConfigSection(
		name = "Highlight Drop Console Log",
		description = "All options that highlight loot drop in your message box.",
		position = 1,
		closedByDefault = false
	)
	String highlightSection = "highlight";

	@ConfigItem(
		keyName = "drop-console-log",
		name = "Track Drops",
		description = "Tracks regular loot drops for the panel and console features.",
		section = dropSection,
		position = 0
	)
	default boolean dropEnabled() {
		return true;
	}

	@ConfigItem(
		keyName = "drop-console-message-enabled",
		name = "Console Message",
		description = "Sends regular loot drops to the RuneLite message box.",
		section = dropSection,
		position = 1
	)
	default boolean dropConsoleMessageEnabled() {
		return true;
	}

	@ConfigItem(
		keyName = "drop-console-list",
		name = "Ignored Items",
		description = "Always ignore matching item names. Separate entries with commas.",
		section = dropSection,
		position = 2
	)
	default String getIgnoreList() {
		return "";
	}

	@ConfigItem(
		keyName = "drop-console-match-mode",
		name = "Item Match Mode",
		description = "How ignored item names are matched. Use id:123 to match a specific item id.",
		section = dropSection,
		position = 3
	)
	default RuleMatchMode getIgnoreItemMatchMode() {
		return RuleMatchMode.CONTAINS;
	}

	@ConfigItem(
		keyName = "drop-console-monster-list",
		name = "Ignored Monsters",
		description = "Ignore all drops from matching monsters. Separate entries with commas.",
		section = dropSection,
		position = 4
	)
	default String getIgnoreMonster() {
		return "";
	}

	@ConfigItem(
		keyName = "drop-console-monster-match-mode",
		name = "Monster Match Mode",
		description = "How ignored monster names are matched.",
		section = dropSection,
		position = 5
	)
	default RuleMatchMode getIgnoreMonsterMatchMode() {
		return RuleMatchMode.CONTAINS;
	}

	@ConfigItem(
		keyName = "drop-console-min-quantity",
		name = "Auto-Ignore Below Qty",
		description = "Ignore items below this quantity. Set to 0 to disable.",
		section = dropSection,
		position = 6
	)
	default int getDropMinQuantity() {
		return 0;
	}

	@ConfigItem(
		keyName = "drop-console-min-ge-value",
		name = "Auto-Ignore Below GE",
		description = "Ignore items below this Grand Exchange value. Set to 0 to disable.",
		section = dropSection,
		position = 7
	)
	default int getDropMinGeValue() {
		return 0;
	}

	@ConfigItem(
		keyName = "drop-console-min-ha-value",
		name = "Auto-Ignore Below HA",
		description = "Ignore items below this high alchemy value. Set to 0 to disable.",
		section = dropSection,
		position = 8
	)
	default int getDropMinHaValue() {
		return 0;
	}

	@ConfigItem(
		keyName = "drop-console-color",
		name = "Drop Color",
		description = "Set the color of the loot messages.",
		section = dropSection,
		position = 9
	)
	default Color getDropColor() {
		return new Color(0, 0, 0);
	}

	@ConfigItem(
		keyName = "highlight-console-log",
		name = "Track Highlighted Drops",
		description = "Tracks highlighted loot for panel and console features.",
		section = highlightSection,
		position = 0
	)
	default boolean highlightEnabled() {
		return true;
	}

	@ConfigItem(
		keyName = "highlight-console-message-enabled",
		name = "Console Message",
		description = "Sends highlighted loot drops to the RuneLite message box.",
		section = highlightSection,
		position = 1
	)
	default boolean highlightConsoleMessageEnabled() {
		return true;
	}

	@ConfigItem(
		keyName = "highlight-console-list",
		name = "Highlighted Items",
		description = "Always highlight matching item names. Separate entries with commas.",
		section = highlightSection,
		position = 2
	)
	default String getHighlightList() {
		return "";
	}

	@ConfigItem(
		keyName = "highlight-console-match-mode",
		name = "Item Match Mode",
		description = "How highlighted item names are matched. Use id:123 to match a specific item id.",
		section = highlightSection,
		position = 3
	)
	default RuleMatchMode getHighlightMatchMode() {
		return RuleMatchMode.CONTAINS;
	}

	@ConfigItem(
		keyName = "highlight-console-message",
		name = "Highlighted Message",
		description = "Set the text in front of a highlighted loot message.",
		section = highlightSection,
		position = 4
	)
	default String getHighlightMessage() {
		return "Drops";
	}

	@ConfigItem(
		keyName = "highlight-console-min-quantity",
		name = "Auto-Highlight Qty",
		description = "Highlight items at or above this quantity. Set to 0 to disable.",
		section = highlightSection,
		position = 5
	)
	default int getHighlightMinQuantity() {
		return 0;
	}

	@ConfigItem(
		keyName = "highlight-console-min-ge-value",
		name = "Auto-Highlight GE",
		description = "Highlight items at or above this Grand Exchange value. Set to 0 to disable.",
		section = highlightSection,
		position = 6
	)
	default int getHighlightMinGeValue() {
		return 0;
	}

	@ConfigItem(
		keyName = "highlight-console-min-ha-value",
		name = "Auto-Highlight HA",
		description = "Highlight items at or above this high alchemy value. Set to 0 to disable.",
		section = highlightSection,
		position = 7
	)
	default int getHighlightMinHaValue() {
		return 0;
	}

	@ConfigItem(
		keyName = "highlight-console-color",
		name = "Highlight Color",
		description = "Set the color of a highlighted loot message.",
		section = highlightSection,
		position = 8
	)
	default Color getHighlightColor() {
		return new Color(202, 19, 19);
	}

}
