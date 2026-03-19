package org.emerycp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Singleton;

@Singleton
class LootSessionTracker
{
	private static final int MAX_RECENT_DROPS = 10;

	private long nextDropId = 1L;
	private final List<LoggedDrop> loggedDrops = new ArrayList<>();

	synchronized long recordDrop(
		String source,
		String itemsText,
		List<LootItemSummary> items,
		List<String> highlightedItemNames,
		int geValue,
		int haValue
	)
	{
		final long dropId = nextDropId++;
		loggedDrops.add(
			new LoggedDrop(
				dropId,
				source,
				itemsText,
				new ArrayList<>(items),
				new ArrayList<>(highlightedItemNames),
				geValue,
				haValue
			)
		);
		return dropId;
	}

	synchronized void reset()
	{
		loggedDrops.clear();
		nextDropId = 1L;
	}

	synchronized boolean removeRecentDrop(long dropId)
	{
		final Iterator<LoggedDrop> iterator = loggedDrops.iterator();
		while (iterator.hasNext())
		{
			if (iterator.next().dropId == dropId)
			{
				iterator.remove();
				return true;
			}
		}

		return false;
	}

	synchronized boolean removeSource(String source)
	{
		boolean removed = false;
		final Iterator<LoggedDrop> iterator = loggedDrops.iterator();
		while (iterator.hasNext())
		{
			if (iterator.next().source.equals(source))
			{
				iterator.remove();
				removed = true;
			}
		}

		return removed;
	}

	synchronized SessionSnapshot snapshot()
	{
		int loggedDropCount = 0;
		long totalGeValue = 0L;
		long totalHaValue = 0L;
		final Map<String, SourceSummary> sourceSummaries = new LinkedHashMap<>();
		final Deque<RecentDropSnapshot> recentDrops = new ArrayDeque<>();

		for (int i = loggedDrops.size() - 1; i >= 0; i--)
		{
			final LoggedDrop drop = loggedDrops.get(i);
			final Set<String> highlightedItems = new HashSet<>(drop.highlightedItemNames);
			loggedDropCount++;
			totalGeValue += drop.geValue;
			totalHaValue += drop.haValue;

			final SourceSummary sourceSummary = sourceSummaries.computeIfAbsent(drop.source, SourceSummary::new);

			sourceSummary.dropCount++;
			sourceSummary.totalGeValue += drop.geValue;
			sourceSummary.totalHaValue += drop.haValue;

			for (LootItemSummary item : drop.items)
			{
				final ItemAggregate aggregate = sourceSummary.itemAggregates.computeIfAbsent(
					item.getItemName(),
					ItemAggregate::new
				);

				aggregate.quantity += item.getQuantity();
				aggregate.totalGeValue += item.getGeValue();
				aggregate.totalHaValue += item.getHaValue();
				aggregate.highlighted |= highlightedItems.contains(item.getItemName());
			}

			if (recentDrops.size() < MAX_RECENT_DROPS)
			{
				recentDrops.addLast(
					new RecentDropSnapshot(
						drop.dropId,
						drop.source,
						drop.itemsText,
						new ArrayList<>(drop.highlightedItemNames),
						drop.geValue,
						drop.haValue
					)
				);
			}
		}

		final List<SourceSnapshot> sources = new ArrayList<>();
		for (SourceSummary summary : sourceSummaries.values())
		{
			final List<ItemQuantitySnapshot> topItems = new ArrayList<>();
			for (ItemAggregate aggregate : summary.itemAggregates.values())
			{
				topItems.add(
					new ItemQuantitySnapshot(
						aggregate.itemName,
						aggregate.quantity,
						aggregate.totalGeValue,
						aggregate.totalHaValue,
						aggregate.highlighted
					)
				);
			}

			sources.add(
				new SourceSnapshot(
					summary.source,
					summary.dropCount,
					summary.totalGeValue,
					summary.totalHaValue,
					topItems
				)
			);
		}

		Collections.sort(
			sources,
			Comparator.comparingLong(SourceSnapshot::getTotalGeValue).reversed()
				.thenComparing(SourceSnapshot::getSource)
		);

		return new SessionSnapshot(
			loggedDropCount,
			totalGeValue,
			totalHaValue,
			sources,
			new ArrayList<>(recentDrops)
		);
	}

	static class LootItemSummary
	{
		private final String itemName;
		private final int quantity;
		private final int geValue;
		private final int haValue;

		LootItemSummary(String itemName, int quantity, int geValue, int haValue)
		{
			this.itemName = itemName;
			this.quantity = quantity;
			this.geValue = geValue;
			this.haValue = haValue;
		}

		String getItemName()
		{
			return itemName;
		}

		int getQuantity()
		{
			return quantity;
		}

		int getGeValue()
		{
			return geValue;
		}

		int getHaValue()
		{
			return haValue;
		}
	}

	static class SessionSnapshot
	{
		private final int loggedDropCount;
		private final long totalGeValue;
		private final long totalHaValue;
		private final List<SourceSnapshot> sources;
		private final List<RecentDropSnapshot> recentDrops;

		SessionSnapshot(
			int loggedDropCount,
			long totalGeValue,
			long totalHaValue,
			List<SourceSnapshot> sources,
			List<RecentDropSnapshot> recentDrops
		)
		{
			this.loggedDropCount = loggedDropCount;
			this.totalGeValue = totalGeValue;
			this.totalHaValue = totalHaValue;
			this.sources = sources;
			this.recentDrops = recentDrops;
		}

		int getLoggedDropCount()
		{
			return loggedDropCount;
		}

		long getTotalGeValue()
		{
			return totalGeValue;
		}

		long getTotalHaValue()
		{
			return totalHaValue;
		}

		List<SourceSnapshot> getSources()
		{
			return sources;
		}

		List<RecentDropSnapshot> getRecentDrops()
		{
			return recentDrops;
		}
	}

	static class SourceSnapshot
	{
		private final String source;
		private final int dropCount;
		private final long totalGeValue;
		private final long totalHaValue;
		private final List<ItemQuantitySnapshot> topItems;

		SourceSnapshot(
			String source,
			int dropCount,
			long totalGeValue,
			long totalHaValue,
			List<ItemQuantitySnapshot> topItems
		)
		{
			this.source = source;
			this.dropCount = dropCount;
			this.totalGeValue = totalGeValue;
			this.totalHaValue = totalHaValue;
			this.topItems = topItems;
		}

		String getSource()
		{
			return source;
		}

		int getDropCount()
		{
			return dropCount;
		}

		long getTotalGeValue()
		{
			return totalGeValue;
		}

		long getTotalHaValue()
		{
			return totalHaValue;
		}

		List<ItemQuantitySnapshot> getTopItems()
		{
			return topItems;
		}
	}

	static class ItemQuantitySnapshot
	{
		private final String itemName;
		private final int quantity;
		private final long totalGeValue;
		private final long totalHaValue;
		private final boolean highlighted;

		ItemQuantitySnapshot(
			String itemName,
			int quantity,
			long totalGeValue,
			long totalHaValue,
			boolean highlighted
		)
		{
			this.itemName = itemName;
			this.quantity = quantity;
			this.totalGeValue = totalGeValue;
			this.totalHaValue = totalHaValue;
			this.highlighted = highlighted;
		}

		String getItemName()
		{
			return itemName;
		}

		int getQuantity()
		{
			return quantity;
		}

		long getTotalGeValue()
		{
			return totalGeValue;
		}

		long getTotalHaValue()
		{
			return totalHaValue;
		}

		boolean isHighlighted()
		{
			return highlighted;
		}
	}

	static class RecentDropSnapshot
	{
		private final long dropId;
		private final String source;
		private final String itemsText;
		private final List<String> highlightedItemNames;
		private final int geValue;
		private final int haValue;

		RecentDropSnapshot(
			long dropId,
			String source,
			String itemsText,
			List<String> highlightedItemNames,
			int geValue,
			int haValue
		)
		{
			this.dropId = dropId;
			this.source = source;
			this.itemsText = itemsText;
			this.highlightedItemNames = highlightedItemNames;
			this.geValue = geValue;
			this.haValue = haValue;
		}

		long getDropId()
		{
			return dropId;
		}

		String getSource()
		{
			return source;
		}

		String getItemsText()
		{
			return itemsText;
		}

		List<String> getHighlightedItemNames()
		{
			return highlightedItemNames;
		}

		int getGeValue()
		{
			return geValue;
		}

		int getHaValue()
		{
			return haValue;
		}
	}

	private static class SourceSummary
	{
		private final String source;
		private int dropCount;
		private long totalGeValue;
		private long totalHaValue;
		private final Map<String, ItemAggregate> itemAggregates = new LinkedHashMap<>();

		private SourceSummary(String source)
		{
			this.source = source;
		}
	}

	private static class ItemAggregate
	{
		private final String itemName;
		private int quantity;
		private long totalGeValue;
		private long totalHaValue;
		private boolean highlighted;

		private ItemAggregate(String itemName)
		{
			this.itemName = itemName;
		}
	}

	private static class LoggedDrop
	{
		private final long dropId;
		private final String source;
		private final String itemsText;
		private final List<LootItemSummary> items;
		private final List<String> highlightedItemNames;
		private final int geValue;
		private final int haValue;

		private LoggedDrop(
			long dropId,
			String source,
			String itemsText,
			List<LootItemSummary> items,
			List<String> highlightedItemNames,
			int geValue,
			int haValue
		)
		{
			this.dropId = dropId;
			this.source = source;
			this.itemsText = itemsText;
			this.items = items;
			this.highlightedItemNames = highlightedItemNames;
			this.geValue = geValue;
			this.haValue = haValue;
		}
	}
}
