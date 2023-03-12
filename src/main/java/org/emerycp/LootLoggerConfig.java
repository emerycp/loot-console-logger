package org.emerycp;

import net.runelite.client.chat.ChatColorType;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

import java.awt.*;

@ConfigGroup("LootConsoleLogger")
public interface LootLoggerConfig extends Config
{
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
			name = "Enabled",
			description = "Enables loot messages on a monster's death.",
			section = dropSection,
			position = 0
	)
	default boolean dropEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "drop-console-list",
			name = "Ignored Items",
			description = "Enter the item name separated by a comma. i.e.: Bones, Coins",
			section = dropSection,
			position = 1
	)
	default String getIgnoreList()
	{
		return "";
	}
	@ConfigItem(
			keyName = "drop-console-monster-list",
			name = "Ignored Monsters",
			description = "Enter the monster name separated by a comma. i.e.: Goblin, TzTok-Jad",
			section = dropSection,
			position = 2
	)
	default String getIgnoreMonster()
	{
		return "";
	}

	@ConfigItem(
			keyName = "drop-console-color",
			name = "Drop Color",
			description = "Set the color of the loot messages.",
			section = dropSection,
			position = 3
	)
	default Color getDropColor()
	{
		return new Color(0, 0, 0);
	}

	@ConfigItem(
			keyName = "highlight-console-log",
			name = "Enabled",
			description = "Enables highlighted loot messages on a monster's death.",
			section = highlightSection,
			position = 0
	)
	default boolean highlightEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "highlight-console-list",
			name = "Highlighted Items",
			description = "Enter the item name separated by a comma. i.e.: Dragon spear, Rune arrow",
			section = highlightSection,
			position = 1
	)
	default String getHighlightList()
	{
		return "";
	}

	@ConfigItem(
			keyName = "highlight-console-message",
			name = "Highlighted Message",
			description = "Set the text in front of a highlighted loot message.",
			section = highlightSection,
			position = 2
	)
	default String getHighlightMessage()
	{
		return "Drops";
	}

	@ConfigItem(
			keyName = "highlight-console-color",
			name = "Highlight Color",
			description = "Set the color of a highlighted loot message.",
			section = highlightSection,
			position = 3
	)
	default Color getHighlightColor()
	{
		return new Color(202, 19, 19);
	}

}
