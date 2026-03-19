package org.emerycp;

public enum TopItemSortMode
{
	COUNT("Count"),
	GE_VALUE("Total Value (GE)"),
	HA_VALUE("Total Value (HA)");

	private final String displayName;

	TopItemSortMode(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
