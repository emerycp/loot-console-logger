package org.emerycp;

import net.runelite.client.chat.ChatColorType;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

import java.awt.*;

@ConfigGroup("LootConsoleLogger")
public interface LootLoggerConfig extends Config
{
	@ConfigItem(
			keyName = "drop-console-log",
			name = "Loot Drop Console Log",
			description = "Enables loot message on a monster's death.",
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
			position = 2
	)
	default String getIgnoreMonster()
	{
		return "";
	}

	@ConfigItem(
			keyName = "highlight-console-log",
			name = "Highlight Drop Console Log",
			description = "Enables highlighted loot messages on a monster's death.",
			position = 3
	)
	default boolean highlightEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "highlight-console-list",
			name = "Highlighted Items",
			description = "Enter the item name separated by a comma. i.e.: Dragon spear, Rune arrow",
			position = 4
	)
	default String getHighlightList()
	{
		return "";
	}

	@ConfigItem(
			keyName = "highlight-console-message",
			name = "Highlighted Message",
			description = "Set the text in front of a highlighted loot message.",
			position = 5
	)
	default String getHighlightMessage()
	{
		return "Drops";
	}

	@ConfigItem(
			keyName = "highlight-console-color",
			name = "Highlight Color",
			description = "Set the color of a highlighted loot message.",
			position = 6
	)
	default Color getHighlightColor()
	{
		return new Color(202, 19, 19);
	}

}
