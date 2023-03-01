package org.emerycp;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("LootConsoleLogger")
public interface LootLoggerConfig extends Config
{
	@ConfigItem(
			keyName = "drop-console-log",
			name = "Loot Drop Console Log",
			description = "Enables loot message on mob death."
	)
	default boolean dropEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "drop-console-list",
			name = "Loot Ignore Items",
			description = "Enter the item name separated by a comma. i.e.: Bones, Coins"
	)
	default String getIgnoreList()
	{
		return "";
	}

	@ConfigItem(
			keyName = "highlight-console-log",
			name = "Highlight Drop Console Log",
			description = "Enables highlighted loot messages on mob death."
	)
	default boolean highlightEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "highlight-console-list",
			name = "Loot Highlight Items",
			description = "Enter the item name separated by a comma. i.e.: Dragon spear, Rune arrow"
	)
	default String getHighlightList()
	{
		return "";
	}
}
