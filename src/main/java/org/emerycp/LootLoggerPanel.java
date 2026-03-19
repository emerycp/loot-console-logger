package org.emerycp;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Container;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import javax.swing.plaf.basic.BasicComboBoxUI;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.http.api.item.ItemPrice;

@Singleton
class LootLoggerPanel extends PluginPanel
{
	private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance();
	private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 13);
	private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 12);
	private static final Font VALUE_FONT = new Font("SansSerif", Font.BOLD, 12);
	private static final int TOP_ITEM_LIMIT = 5;

	private static final Color CONTROL_BACKGROUND = new Color(42, 49, 58);
	private static final Color CONTROL_BACKGROUND_HOVER = new Color(55, 64, 74);
	private static final Color CONTROL_BORDER = new Color(87, 139, 206);
	private static final Color CONTROL_TEXT = new Color(233, 237, 241);
	private static final Color CARD_BORDER = new Color(82, 132, 214);
	private static final Color CARD_TITLE = new Color(200, 221, 255);
	private static final Color BODY_TEXT = new Color(226, 229, 233);
	private static final Color MUTED_TEXT = new Color(171, 179, 188);
	private static final Color HA_COLOR = new Color(255, 198, 113);
	private static final Color ITEM_HIGHLIGHT = new Color(203, 225, 255);
	private static final Color CHIP_BACKGROUND = new Color(60, 70, 82);
	private static final Color CHIP_BORDER = new Color(98, 111, 126);
	private static final Color RULE_CARD_BORDER = new Color(76, 86, 99);
	private static final int BASE_GAP = 4;
	private static final int BASE_PADDING = 5;
	private static final int BASE_SECTION_GAP = 6;

	private final LootSessionTracker sessionTracker;
	private final LootLoggerConfig config;
	private final ConfigManager configManager;
	private final ItemManager itemManager;

	private final JLabel totalDropsValueLabel = createValueLabel(ColorScheme.LIGHT_GRAY_COLOR);
	private final JLabel totalGeValueLabel = createValueLabel(ColorScheme.PROGRESS_COMPLETE_COLOR);
	private final JLabel totalHaValueLabel = createValueLabel(HA_COLOR);

	private final TagEditorPanel ignoredItemsEditor;
	private final TagEditorPanel highlightedItemsEditor;
	private final TagEditorPanel ignoredMonstersEditor;

	private final JPanel sourceSummaryList = createListPanel();
	private final JPanel recentDropsList = createListPanel();
	private final JComboBox<TopItemSortMode> topItemSortMode = new JComboBox<>(TopItemSortMode.values());
	private final Set<String> collapsedSources = new HashSet<>();

	private final CollapsibleSection rulesSection;
	private final CollapsibleSection sessionSection;
	private final CollapsibleSection sourceSection;
	private final CollapsibleSection recentSection;

	@Inject
	LootLoggerPanel(
		LootSessionTracker sessionTracker,
		LootLoggerConfig config,
		ConfigManager configManager,
		ItemManager itemManager
	)
	{
		this.sessionTracker = sessionTracker;
		this.config = config;
		this.configManager = configManager;
		this.itemManager = itemManager;
		this.ignoredItemsEditor = new TagEditorPanel(
			"Ignored Items",
			"Always skip matching items",
			"drop-console-list",
			config.getIgnoreList(),
			true
		);
		this.highlightedItemsEditor = new TagEditorPanel(
			"Highlighted Items",
			"Always highlight matching items",
			"highlight-console-list",
			config.getHighlightList(),
			true
		);
		this.ignoredMonstersEditor = new TagEditorPanel(
			"Ignored Monsters",
			"Freeform monster tags",
			"drop-console-monster-list",
			config.getIgnoreMonster(),
			false
		);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		final JButton resetButton = new RoundedButton("Reset session");
		styleButton(resetButton);
		resetButton.addActionListener(e ->
		{
			sessionTracker.reset();
			refresh();
		});

		topItemSortMode.setSelectedItem(TopItemSortMode.COUNT);
		styleComboBox(topItemSortMode);
		topItemSortMode.addActionListener(e -> refresh());

		rulesSection = new CollapsibleSection("Rules", createRulesPanel());
		sessionSection = new CollapsibleSection("Session", createSummaryPanel(resetButton));
		sourceSection = new CollapsibleSection("By Source", createSourceSectionContent());
		recentSection = new CollapsibleSection("Recent Drops", recentDropsList);

		add(rulesSection);
		add(Box.createVerticalStrut(sectionGap()));
		add(sessionSection);
		add(Box.createVerticalStrut(sectionGap()));
		add(sourceSection);
		add(Box.createVerticalStrut(sectionGap()));
		add(recentSection);

		getScrollPane().getVerticalScrollBar().setUnitIncrement(32);
		getScrollPane().getVerticalScrollBar().setBlockIncrement(128);

		refresh();
	}

	void refresh()
	{
		final LootSessionTracker.SessionSnapshot snapshot = sessionTracker.snapshot();
		runOnEdt(() ->
		{
			totalDropsValueLabel.setText(formatNumber(snapshot.getLoggedDropCount()));
			totalGeValueLabel.setText(formatNumber(snapshot.getTotalGeValue()) + " gp");
			totalHaValueLabel.setText(formatNumber(snapshot.getTotalHaValue()) + " gp");
			rebuildSourceSummaryList(snapshot);
			rebuildRecentDropsList(snapshot);
			revalidate();
			repaint();
		});
	}

	void syncRuleEditorsFromConfig()
	{
		runOnEdt(() ->
		{
			ignoredItemsEditor.syncFromConfig(config.getIgnoreList());
			highlightedItemsEditor.syncFromConfig(config.getHighlightList());
			ignoredMonstersEditor.syncFromConfig(config.getIgnoreMonster());
		});
	}

	private JPanel createRulesPanel()
	{
		final JPanel panel = createVerticalPanel();
		panel.add(ignoredItemsEditor);
		panel.add(Box.createVerticalStrut(sectionGap()));
		panel.add(highlightedItemsEditor);
		panel.add(Box.createVerticalStrut(sectionGap()));
		panel.add(ignoredMonstersEditor);
		return panel;
	}

	private JPanel createSummaryPanel(JButton resetButton)
	{
		final JPanel panel = createVerticalPanel();
		panel.add(createMetricRow("Logged Drops", totalDropsValueLabel));
		panel.add(createMetricRow("GE Total", totalGeValueLabel));
		panel.add(createMetricRow("HA Total", totalHaValueLabel));

		final JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, compactGap()));
		buttonRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttonRow.add(resetButton);
		panel.add(buttonRow);
		return panel;
	}

	private JPanel createSourceSectionContent()
	{
		final JPanel panel = createVerticalPanel();

		final JPanel filterRow = new JPanel(new BorderLayout(8, 0));
		filterRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		filterRow.setBorder(BorderFactory.createEmptyBorder(0, 0, compactGap(), 0));
		filterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		filterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		final JLabel sortLabel = new JLabel("Top items by");
		sortLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		sortLabel.setFont(LABEL_FONT);

		filterRow.add(sortLabel, BorderLayout.WEST);
		filterRow.add(topItemSortMode, BorderLayout.CENTER);

		panel.add(filterRow);
		panel.add(sourceSummaryList);
		return panel;
	}

	private JPanel createMetricRow(String labelText, JLabel valueLabel)
	{
		final JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

		final JLabel label = new JLabel(labelText);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(LABEL_FONT);

		row.add(label, BorderLayout.WEST);
		row.add(valueLabel, BorderLayout.EAST);
		return row;
	}

	private JPanel createVerticalPanel()
	{
		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		return panel;
	}

	private JPanel createListPanel()
	{
		return createVerticalPanel();
	}

	private JLabel createValueLabel(Color color)
	{
		final JLabel label = new JLabel();
		label.setForeground(color);
		label.setFont(VALUE_FONT);
		return label;
	}

	private void rebuildSourceSummaryList(LootSessionTracker.SessionSnapshot snapshot)
	{
		sourceSummaryList.removeAll();

		if (snapshot.getSources().isEmpty())
		{
			sourceSummaryList.add(createEmptyState("No logged drops yet."));
			return;
		}

		for (LootSessionTracker.SourceSnapshot source : snapshot.getSources())
		{
			final boolean collapsed = collapsedSources.contains(source.getSource());
			final JPanel card = createCard(
				source.getSource(),
				CARD_BORDER,
				CARD_TITLE,
				true,
				collapsed,
				e ->
				{
					toggleSourceCollapsed(source.getSource());
					refresh();
				},
				e ->
				{
					sessionTracker.removeSource(source.getSource());
					refresh();
				}
			);

			card.add(createMutedLabel(source.getDropCount() + " kills"), BorderLayout.CENTER);

			if (!collapsed)
			{
				final JPanel details = createCardBody();
				details.add(createValuePairLabel(source.getTotalGeValue(), source.getTotalHaValue()));

				if (!source.getTopItems().isEmpty())
				{
					details.add(createSubheadingLabel("Top items"));
					for (LootSessionTracker.ItemQuantitySnapshot item : getSortedTopItems(source))
					{
						details.add(createSummaryTopItemLabel(item));
					}
				}

				card.add(details, BorderLayout.SOUTH);
			}

			addCardToList(sourceSummaryList, card);
		}
	}

	private void rebuildRecentDropsList(LootSessionTracker.SessionSnapshot snapshot)
	{
		recentDropsList.removeAll();

		if (snapshot.getRecentDrops().isEmpty())
		{
			recentDropsList.add(createEmptyState("No logged drops yet."));
			return;
		}

		for (LootSessionTracker.RecentDropSnapshot drop : snapshot.getRecentDrops())
		{
			final Color accentColor = getRecentAccentColor(drop);
			final JPanel card = createCard(
				drop.getSource(),
				accentColor,
				accentColor,
				false,
				false,
				null,
				e ->
				{
					sessionTracker.removeRecentDrop(drop.getDropId());
					refresh();
				}
			);

			final JPanel details = createCardBody();
			details.add(createRecentDropLabel(drop.getItemsText(), drop.getHighlightedItemNames()));
			details.add(createValuePairLabel(drop.getGeValue(), drop.getHaValue()));

			card.add(details, BorderLayout.CENTER);
			addCardToList(recentDropsList, card);
		}
	}

	private void addCardToList(JPanel listPanel, JPanel card)
	{
		listPanel.add(card);
		listPanel.add(Box.createVerticalStrut(sectionGap()));
	}

	private JPanel createCard(
		String title,
		Color borderColor,
		Color titleColor,
		boolean showCollapse,
		boolean collapsed,
		java.awt.event.ActionListener collapseAction,
		java.awt.event.ActionListener deleteAction
	)
	{
		final JPanel card = new JPanel(new BorderLayout(0, compactGap()));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(borderColor),
			BorderFactory.createEmptyBorder(cardPadding(), cardPadding(), cardPadding(), cardPadding())
		));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

		final JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		final JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, compactGap(), 0));
		titleRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		if (showCollapse)
		{
			final JButton collapseButton = new ToggleGlyphButton(collapsed ? "\u25B8" : "\u25BE");
			styleIconButton(collapseButton);
			collapseButton.setToolTipText(collapsed ? "Expand" : "Collapse");
			collapseButton.addActionListener(collapseAction);
			titleRow.add(collapseButton);
		}

		final JLabel titleLabel = new JLabel(title);
		titleLabel.setForeground(titleColor);
		titleLabel.setFont(VALUE_FONT);
		titleRow.add(titleLabel);

		final JButton deleteButton = new TrashButton();
		styleIconButton(deleteButton);
		deleteButton.setToolTipText("Remove");
		deleteButton.addActionListener(deleteAction);

		header.add(titleRow, BorderLayout.WEST);
		header.add(wrapHeaderButton(deleteButton), BorderLayout.EAST);

		card.add(header, BorderLayout.NORTH);
		return card;
	}

	private JPanel wrapHeaderButton(JButton button)
	{
		final JPanel container = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		container.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		container.setBorder(
			BorderFactory.createEmptyBorder(0, 2, 1, 0)
		);
		container.add(button);
		return container;
	}

	private JPanel createCardBody()
	{
		final JPanel body = createVerticalPanel();
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		return body;
	}

	private JLabel createSubheadingLabel(String text)
	{
		final JLabel label = new JLabel(text);
		label.setForeground(MUTED_TEXT);
		label.setFont(LABEL_FONT);
		label.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
		return label;
	}

	private JLabel createMutedLabel(String text)
	{
		final JLabel label = new JLabel(text);
		label.setForeground(MUTED_TEXT);
		label.setFont(LABEL_FONT);
		return label;
	}

	private JLabel createValuePairLabel(long geValue, long haValue)
	{
		final String html =
			"<html><span style='color:#" + toHex(ColorScheme.PROGRESS_COMPLETE_COLOR) + ";font-weight:bold;'>GE "
				+ formatNumber(geValue)
				+ " gp</span>"
				+ "<span style='color:#" + toHex(MUTED_TEXT) + ";'> | </span>"
				+ "<span style='color:#" + toHex(HA_COLOR) + ";font-weight:bold;'>HA "
				+ formatNumber(haValue)
				+ " gp</span></html>";

		final JLabel label = new JLabel(html);
		label.setFont(VALUE_FONT);
		return label;
	}

	private JLabel createSummaryTopItemLabel(LootSessionTracker.ItemQuantitySnapshot item)
	{
		final String text = buildTopItemLabel(item);
		final boolean highlighted = item.isHighlighted();
		final String itemColor = highlighted ? toHex(config.getHighlightColor()) : toHex(ITEM_HIGHLIGHT);

		final JLabel label = new JLabel(
			"<html><span style='color:#" + itemColor + ";font-weight:bold;'>"
				+ escapeHtml(text)
				+ "</span></html>"
		);
		label.setForeground(BODY_TEXT);
		label.setFont(LABEL_FONT);
		return label;
	}

	private JLabel createRecentDropLabel(String text, List<String> highlightedItemNames)
	{
		final StringBuilder html = new StringBuilder("<html>");
		final String[] parts = text.split(" / ");
		final Set<String> highlightedItems = new HashSet<>(highlightedItemNames);
		final String defaultItemColor = toHex(ITEM_HIGHLIGHT);
		final String configuredHighlightColor = toHex(config.getHighlightColor());

		for (int i = 0; i < parts.length; i++)
		{
			if (i > 0)
			{
				html.append("<span style='color:#AEB7C2;'> / </span>");
			}

			final String part = escapeHtml(parts[i]);
			final int quantitySeparator = part.indexOf("x ");
			if (quantitySeparator >= 0)
			{
				final String itemName = part.substring(quantitySeparator + 2);
				final String itemColor = highlightedItems.contains(itemName) ? configuredHighlightColor : defaultItemColor;
				html.append("<span style='color:#D9DFE6;'>")
					.append(part.substring(0, quantitySeparator + 2))
					.append("</span><span style='color:#")
					.append(itemColor)
					.append(";font-weight:bold;'>")
					.append(itemName)
					.append("</span>");
			}
			else
			{
				final String itemColor = highlightedItems.contains(part) ? configuredHighlightColor : defaultItemColor;
				html.append("<span style='color:#")
					.append(itemColor)
					.append(";font-weight:bold;'>")
					.append(part)
					.append("</span>");
			}
		}

		html.append("</html>");
		final JLabel label = new JLabel(html.toString());
		label.setForeground(BODY_TEXT);
		label.setFont(LABEL_FONT);
		return label;
	}

	private JLabel createEmptyState(String text)
	{
		final JLabel label = createMutedLabel(text);
		label.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
		return label;
	}

	private void styleButton(JButton button)
	{
		button.setForeground(CONTROL_TEXT);
		button.setFont(VALUE_FONT);
		button.setBorder(new EmptyBorder(4, 6, 4, 6));
		button.setContentAreaFilled(false);
		button.setFocusPainted(false);
		button.setOpaque(false);
		button.setFocusable(false);
	}

	private void styleIconButton(JButton button)
	{
		styleButton(button);
		button.setHorizontalAlignment(SwingConstants.CENTER);
		button.setMargin(new Insets(0, 0, 0, 0));
	}

	private void styleComboBox(JComboBox<TopItemSortMode> comboBox)
	{
		comboBox.setBackground(CONTROL_BACKGROUND);
		comboBox.setForeground(CONTROL_TEXT);
		comboBox.setFont(LABEL_FONT);
		comboBox.setFocusable(false);
		comboBox.setBorder(new EmptyBorder(4, 8, 4, 8));
		comboBox.setRenderer(new ComboRenderer());
		comboBox.setUI(new StyledComboBoxUI());
	}

	private void styleTextField(JTextField textField, String toolTip)
	{
		textField.setBackground(CONTROL_BACKGROUND);
		textField.setForeground(CONTROL_TEXT);
		textField.setCaretColor(CONTROL_TEXT);
		textField.setFont(LABEL_FONT);
		textField.setToolTipText(toolTip);
		textField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(CONTROL_BORDER),
			new EmptyBorder(4, 8, 4, 8)
		));
		textField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
	}

	private List<LootSessionTracker.ItemQuantitySnapshot> getSortedTopItems(
		LootSessionTracker.SourceSnapshot source
	)
	{
		final List<LootSessionTracker.ItemQuantitySnapshot> items = new ArrayList<>(source.getTopItems());
		Collections.sort(items, getTopItemComparator());
		if (items.size() > TOP_ITEM_LIMIT)
		{
			items.subList(TOP_ITEM_LIMIT, items.size()).clear();
		}
		return items;
	}

	private Comparator<LootSessionTracker.ItemQuantitySnapshot> getTopItemComparator()
	{
		final TopItemSortMode sortMode = (TopItemSortMode) topItemSortMode.getSelectedItem();
		if (sortMode == TopItemSortMode.GE_VALUE)
		{
			return Comparator.comparingLong(LootSessionTracker.ItemQuantitySnapshot::getTotalGeValue)
				.reversed()
				.thenComparing(LootSessionTracker.ItemQuantitySnapshot::getItemName);
		}
		if (sortMode == TopItemSortMode.HA_VALUE)
		{
			return Comparator.comparingLong(LootSessionTracker.ItemQuantitySnapshot::getTotalHaValue)
				.reversed()
				.thenComparing(LootSessionTracker.ItemQuantitySnapshot::getItemName);
		}
		return Comparator.comparingInt(LootSessionTracker.ItemQuantitySnapshot::getQuantity)
			.reversed()
			.thenComparing(LootSessionTracker.ItemQuantitySnapshot::getItemName);
	}

	private String buildTopItemLabel(LootSessionTracker.ItemQuantitySnapshot item)
	{
		final String itemPrefix = item.getQuantity() + " x " + item.getItemName();
		final TopItemSortMode sortMode = (TopItemSortMode) topItemSortMode.getSelectedItem();
		if (sortMode == TopItemSortMode.GE_VALUE)
		{
			return itemPrefix + " - " + formatNumber(item.getTotalGeValue()) + " gp";
		}
		if (sortMode == TopItemSortMode.HA_VALUE)
		{
			return itemPrefix + " - " + formatNumber(item.getTotalHaValue()) + " gp";
		}
		return itemPrefix;
	}

	private int compactGap()
	{
		return BASE_GAP;
	}

	private int cardPadding()
	{
		return BASE_PADDING;
	}

	private int sectionGap()
	{
		return BASE_SECTION_GAP;
	}

	private void toggleSourceCollapsed(String source)
	{
		if (!collapsedSources.add(source))
		{
			collapsedSources.remove(source);
		}
	}

	private void runOnEdt(Runnable runnable)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			runnable.run();
		}
		else
		{
			SwingUtilities.invokeLater(runnable);
		}
	}

	private String formatNumber(long value)
	{
		return NUMBER_FORMAT.format(value);
	}

	private String normalize(String value)
	{
		if (value == null)
		{
			return "";
		}

		final String lowerCased = value.toLowerCase();
		final StringBuilder normalized = new StringBuilder(lowerCased.length());
		for (int i = 0; i < lowerCased.length(); i++)
		{
			final char currentCharacter = lowerCased.charAt(i);
			if (!Character.isWhitespace(currentCharacter))
			{
				normalized.append(currentCharacter);
			}
		}

		return normalized.toString();
	}

	private String escapeHtml(String value)
	{
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private String toHex(Color color)
	{
		return String.format("%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
	}

	private Color getRecentAccentColor(LootSessionTracker.RecentDropSnapshot drop)
	{
		return drop.getHighlightedItemNames().isEmpty() ? CARD_BORDER : config.getHighlightColor();
	}

	private static class ComboRenderer extends BasicComboBoxRenderer
	{
		@Override
		public Component getListCellRendererComponent(
			JList list,
			Object value,
			int index,
			boolean isSelected,
			boolean cellHasFocus
		)
		{
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			setBorder(new EmptyBorder(4, 8, 4, 8));
			setBackground(isSelected ? CONTROL_BACKGROUND_HOVER : CONTROL_BACKGROUND);
			setForeground(CONTROL_TEXT);
			return this;
		}
	}

	private static class StyledComboBoxUI extends BasicComboBoxUI
	{
		@Override
		protected JButton createArrowButton()
		{
			return new ArrowButton();
		}

		@Override
		public void paintCurrentValueBackground(Graphics g, java.awt.Rectangle bounds, boolean hasFocus)
		{
			g.setColor(CONTROL_BACKGROUND);
			g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
		}
	}

	private static class RoundedButton extends JButton
	{
		private RoundedButton(String text)
		{
			super(text);
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			final Graphics2D g2 = (Graphics2D) graphics.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(getModel().isRollover() ? CONTROL_BACKGROUND_HOVER : CONTROL_BACKGROUND);
			g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
			g2.setColor(CONTROL_BORDER);
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
			g2.dispose();
			super.paintComponent(graphics);
		}

		@Override
		protected void paintBorder(Graphics graphics)
		{
		}
	}

	private static class ToggleGlyphButton extends RoundedButton
	{
		private ToggleGlyphButton(String text)
		{
			super(text);
			setPreferredSize(new Dimension(20, 20));
			setMinimumSize(new Dimension(20, 20));
			setMaximumSize(new Dimension(20, 20));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			final Graphics2D g2 = (Graphics2D) graphics.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(CONTROL_TEXT);
			g2.setFont(getFont());
			final java.awt.FontMetrics metrics = g2.getFontMetrics();
			final String text = getText();
			final int textX = (getWidth() - metrics.stringWidth(text)) / 2;
			final int textY = ((getHeight() - metrics.getHeight()) / 2) + metrics.getAscent();
			g2.drawString(text, textX, textY);
			g2.dispose();
		}
	}

	private static class TrashButton extends RoundedButton
	{
		private TrashButton()
		{
			super("");
			setPreferredSize(new Dimension(24, 24));
			setMinimumSize(new Dimension(24, 24));
			setMaximumSize(new Dimension(24, 24));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			final Graphics2D g2 = (Graphics2D) graphics.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(CONTROL_TEXT);
			final int centerX = getWidth() / 2;
			final int top = 7;
			final int bodyWidth = 8;
			final int bodyHeight = 8;
			final int bodyLeft = centerX - (bodyWidth / 2);
			g2.drawRect(bodyLeft, top + 2, bodyWidth, bodyHeight);
			g2.drawLine(bodyLeft - 1, top + 2, bodyLeft + bodyWidth + 1, top + 2);
			g2.drawLine(bodyLeft + 2, top, bodyLeft + bodyWidth - 2, top);
			g2.drawLine(centerX - 1, top - 1, centerX + 1, top - 1);
			g2.drawLine(bodyLeft + 2, top + 4, bodyLeft + 2, top + bodyHeight);
			g2.drawLine(centerX, top + 4, centerX, top + bodyHeight);
			g2.drawLine(bodyLeft + bodyWidth - 2, top + 4, bodyLeft + bodyWidth - 2, top + bodyHeight);
			g2.dispose();
		}
	}

	private static class ArrowButton extends JButton
	{
		private ArrowButton()
		{
			setBorder(new EmptyBorder(0, 4, 0, 6));
			setContentAreaFilled(false);
			setFocusPainted(false);
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			final Graphics2D g2 = (Graphics2D) graphics.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			final int centerX = getWidth() / 2;
			final int centerY = getHeight() / 2 + 1;
			final Polygon arrow = new Polygon();
			arrow.addPoint(centerX - 4, centerY - 2);
			arrow.addPoint(centerX + 4, centerY - 2);
			arrow.addPoint(centerX, centerY + 3);
			g2.setColor(CONTROL_TEXT);
			g2.fillPolygon(arrow);
			g2.dispose();
		}
	}

	private class CollapsibleSection extends JPanel
	{
		private final JPanel content;
		private final JButton toggleButton = new ToggleGlyphButton("\u25BE");
		private boolean collapsed;

		private CollapsibleSection(String title, JPanel content)
		{
			this.content = content;
			setLayout(new BorderLayout(0, compactGap()));
			setBackground(ColorScheme.DARK_GRAY_COLOR);
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
				BorderFactory.createEmptyBorder(8, 8, 8, 8)
			));
			setAlignmentX(Component.LEFT_ALIGNMENT);
			setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
			styleIconButton(toggleButton);
			toggleButton.addActionListener(e -> setCollapsed(!collapsed));
			final JPanel header = new JPanel(new BorderLayout());
			header.setBackground(ColorScheme.DARK_GRAY_COLOR);
			final JLabel titleLabel = new JLabel(title);
			titleLabel.setForeground(ColorScheme.BRAND_ORANGE);
			titleLabel.setFont(TITLE_FONT);
			header.add(titleLabel, BorderLayout.WEST);
			header.add(wrapHeaderButton(toggleButton), BorderLayout.EAST);
			add(header, BorderLayout.NORTH);
			add(content, BorderLayout.CENTER);
		}

		private void setCollapsed(boolean collapsed)
		{
			this.collapsed = collapsed;
			toggleButton.setText(collapsed ? "\u25B8" : "\u25BE");
			content.setVisible(!collapsed);
			revalidate();
			repaint();
		}
	}

	private class TagEditorPanel extends JPanel
	{
		private final String configKey;
		private final boolean itemAutocomplete;
		private final List<String> tags = new ArrayList<>();
		private final JPanel chipsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 4));
		private final JTextField inputField = new JTextField();
		private final JButton addButton = new RoundedButton("+");
		private final DefaultListModel<String> suggestionModel = new DefaultListModel<>();
		private final JList<String> suggestionList = new JList<>(suggestionModel);
		private final JScrollPane suggestionPane = new JScrollPane(suggestionList);
		private String lastSyncedValue;

		private TagEditorPanel(String title, String subtitle, String configKey, String initialValue, boolean itemAutocomplete)
		{
			this.configKey = configKey;
			this.itemAutocomplete = itemAutocomplete;
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setAlignmentX(Component.LEFT_ALIGNMENT);
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(RULE_CARD_BORDER),
				BorderFactory.createEmptyBorder(cardPadding(), cardPadding(), cardPadding(), cardPadding())
			));
			final JLabel label = new JLabel(title);
			label.setForeground(CARD_TITLE);
			label.setFont(VALUE_FONT);
			label.setAlignmentX(Component.LEFT_ALIGNMENT);
			final JLabel subtitleLabel = new JLabel(subtitle);
			subtitleLabel.setForeground(MUTED_TEXT);
			subtitleLabel.setFont(LABEL_FONT);
			subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(label);
			add(subtitleLabel);
			add(Box.createVerticalStrut(compactGap()));
			chipsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			chipsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			chipsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
			add(chipsPanel);
			add(Box.createVerticalStrut(compactGap()));
			final JPanel inputRow = new JPanel(new BorderLayout(compactGap(), 0));
			inputRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			inputRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			inputRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
			styleTextField(inputField, itemAutocomplete ? "Type to search items, Enter to add" : "Type a monster name, Enter to add");
			styleButton(addButton);
			addButton.setToolTipText("Add tag");
			addButton.addActionListener(e -> addSelectedOrTypedTag());
			inputField.getDocument().addDocumentListener(new DocumentListener()
			{
				@Override public void insertUpdate(DocumentEvent e) { updateSuggestions(); }
				@Override public void removeUpdate(DocumentEvent e) { updateSuggestions(); }
				@Override public void changedUpdate(DocumentEvent e) { updateSuggestions(); }
			});
			inputField.addActionListener(e -> addSelectedOrTypedTag());
			inputField.addKeyListener(new java.awt.event.KeyAdapter()
			{
				@Override
				public void keyPressed(java.awt.event.KeyEvent event)
				{
					if (!suggestionPane.isVisible()) { return; }
					if (event.getKeyCode() == java.awt.event.KeyEvent.VK_DOWN) { moveSelection(1); event.consume(); }
					else if (event.getKeyCode() == java.awt.event.KeyEvent.VK_UP) { moveSelection(-1); event.consume(); }
					else if (event.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) { hideSuggestions(); }
				}
			});
			inputRow.add(inputField, BorderLayout.CENTER);
			inputRow.add(addButton, BorderLayout.EAST);
			add(inputRow);
			suggestionList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
			suggestionList.setBackground(CONTROL_BACKGROUND);
			suggestionList.setForeground(CONTROL_TEXT);
			suggestionList.setFont(LABEL_FONT);
			suggestionList.setBorder(new EmptyBorder(2, 2, 2, 2));
			suggestionList.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override public void mouseClicked(java.awt.event.MouseEvent event) { if (event.getClickCount() >= 1) { addSelectedOrTypedTag(); } }
			});
			suggestionPane.setBorder(BorderFactory.createLineBorder(CONTROL_BORDER));
			suggestionPane.setPreferredSize(new Dimension(180, 90));
			suggestionPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
			suggestionPane.setAlignmentX(Component.LEFT_ALIGNMENT);
			suggestionPane.setVisible(false);
			add(Box.createVerticalStrut(compactGap()));
			add(suggestionPane);
			syncFromConfig(initialValue);
		}

		private void syncFromConfig(String configValue)
		{
			final String sanitized = sanitizeConfigValue(configValue);
			if (sanitized.equals(lastSyncedValue)) { return; }
			lastSyncedValue = sanitized;
			tags.clear();
			tags.addAll(parseDisplayConfigList(sanitized));
			rebuildChips();
		}

		private void addSelectedOrTypedTag()
		{
			String value = suggestionPane.isVisible() ? suggestionList.getSelectedValue() : null;
			if (value == null || value.trim().isEmpty()) { value = inputField.getText(); }
			value = sanitizeTag(value);
			if (value.isEmpty() || containsNormalizedTag(value))
			{
				inputField.setText("");
				hideSuggestions();
				inputField.requestFocusInWindow();
				return;
			}
			tags.add(value);
			saveTags();
			inputField.setText("");
			hideSuggestions();
			rebuildChips();
			inputField.requestFocusInWindow();
		}

		private void removeTag(String tag)
		{
			for (int i = 0; i < tags.size(); i++)
			{
				if (normalize(tags.get(i)).equals(normalize(tag))) { tags.remove(i); break; }
			}
			saveTags();
			rebuildChips();
		}

		private void rebuildChips()
		{
			chipsPanel.removeAll();
			if (tags.isEmpty())
			{
				final JLabel hint = createMutedLabel("No tags set.");
				hint.setBorder(new EmptyBorder(2, 0, 2, 0));
				chipsPanel.add(hint);
			}
			else
			{
				for (String tag : tags) { chipsPanel.add(createChip(tag)); }
			}
			chipsPanel.revalidate();
			chipsPanel.repaint();
			revalidate();
			repaint();
		}

		private JPanel createChip(String tag)
		{
			final JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			chip.setBackground(CHIP_BACKGROUND);
			chip.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(CHIP_BORDER),
				new EmptyBorder(2, 6, 2, 4)
			));
			final JLabel label = new JLabel(tag);
			label.setForeground(CONTROL_TEXT);
			label.setFont(LABEL_FONT);
			final JButton removeButton = new JButton("x");
			removeButton.setForeground(CONTROL_TEXT);
			removeButton.setFont(LABEL_FONT);
			removeButton.setBorder(new EmptyBorder(0, 2, 0, 2));
			removeButton.setContentAreaFilled(false);
			removeButton.setFocusPainted(false);
			removeButton.setOpaque(false);
			removeButton.addActionListener(e -> removeTag(tag));
			chip.add(label);
			chip.add(removeButton);
			return chip;
		}

		private void saveTags()
		{
			lastSyncedValue = String.join(", ", tags);
			configManager.setConfiguration("LootConsoleLogger", configKey, lastSyncedValue);
		}

		private void updateSuggestions()
		{
			if (!itemAutocomplete) { hideSuggestions(); return; }
			final String query = sanitizeTag(inputField.getText());
			suggestionModel.clear();
			if (query.length() < 2) { hideSuggestions(); return; }
			final List<ItemPrice> results = itemManager.search(query);
			int added = 0;
			for (ItemPrice result : results)
			{
				final String name = sanitizeTag(result.getName());
				if (name.isEmpty() || containsNormalizedTag(name)) { continue; }
				suggestionModel.addElement(name);
				added++;
				if (added >= 8) { break; }
			}
			if (suggestionModel.isEmpty()) { hideSuggestions(); return; }
			suggestionList.setSelectedIndex(0);
			suggestionPane.setVisible(true);
			revalidate();
			repaint();
		}

		private void hideSuggestions()
		{
			suggestionPane.setVisible(false);
		}

		private void moveSelection(int delta)
		{
			if (suggestionModel.isEmpty()) { return; }
			int index = suggestionList.getSelectedIndex();
			index = Math.max(0, Math.min(suggestionModel.size() - 1, index + delta));
			suggestionList.setSelectedIndex(index);
			suggestionList.ensureIndexIsVisible(index);
		}

		private boolean containsNormalizedTag(String value)
		{
			final String normalizedValue = normalize(value);
			for (String tag : tags)
			{
				if (normalize(tag).equals(normalizedValue)) { return true; }
			}
			return false;
		}

		private String sanitizeConfigValue(String value) { return value == null ? "" : value.trim(); }
		private String sanitizeTag(String value) { return value == null ? "" : value.trim(); }

		private List<String> parseDisplayConfigList(String configValue)
		{
			final List<String> values = new ArrayList<>();
			if (configValue == null || configValue.trim().isEmpty()) { return values; }
			for (String entry : configValue.split(","))
			{
				final String trimmed = sanitizeTag(entry);
				if (!trimmed.isEmpty() && !containsDisplayValue(values, trimmed)) { values.add(trimmed); }
			}
			return values;
		}

		private boolean containsDisplayValue(List<String> values, String candidate)
		{
			final String normalizedCandidate = normalize(candidate);
			for (String value : values)
			{
				if (normalize(value).equals(normalizedCandidate)) { return true; }
			}
			return false;
		}
	}

	private static class WrapLayout extends FlowLayout
	{
		private WrapLayout(int align, int hgap, int vgap)
		{
			super(align, hgap, vgap);
		}

		@Override
		public Dimension preferredLayoutSize(Container target)
		{
			return layoutSize(target, true);
		}

		@Override
		public Dimension minimumLayoutSize(Container target)
		{
			final Dimension minimum = layoutSize(target, false);
			minimum.width -= getHgap() + 1;
			return minimum;
		}

		private Dimension layoutSize(Container target, boolean preferred)
		{
			synchronized (target.getTreeLock())
			{
				final int targetWidth = getTargetWidth(target);
				final int horizontalGap = getHgap();
				final int verticalGap = getVgap();
				final Insets insets = target.getInsets();
				final int maxWidth = targetWidth - (insets.left + insets.right + horizontalGap * 2);

				int rowWidth = 0;
				int rowHeight = 0;
				int requiredWidth = 0;
				int requiredHeight = insets.top + insets.bottom + verticalGap * 2;

				for (Component component : target.getComponents())
				{
					if (!component.isVisible())
					{
						continue;
					}

					final Dimension size = preferred
						? component.getPreferredSize()
						: component.getMinimumSize();

					if (rowWidth > 0 && rowWidth + horizontalGap + size.width > maxWidth)
					{
						requiredWidth = Math.max(requiredWidth, rowWidth);
						requiredHeight += rowHeight + verticalGap;
						rowWidth = 0;
						rowHeight = 0;
					}

					if (rowWidth > 0)
					{
						rowWidth += horizontalGap;
					}

					rowWidth += size.width;
					rowHeight = Math.max(rowHeight, size.height);
				}

				requiredWidth = Math.max(requiredWidth, rowWidth);
				requiredHeight += rowHeight;

				return new Dimension(
					requiredWidth + insets.left + insets.right + horizontalGap * 2,
					requiredHeight
				);
			}
		}

		private int getTargetWidth(Container target)
		{
			final Container container = target.getParent();
			if (container != null)
			{
				final int width = container.getWidth();
				if (width > 0)
				{
					return width;
				}
			}

			if (target.getWidth() > 0)
			{
				return target.getWidth();
			}

			// First render can happen before Swing assigns a real width.
			// Use the plugin panel width so wrapped chip rows get a stable height
			// without requiring a manual add/remove interaction.
			return PluginPanel.PANEL_WIDTH - 24;
		}
	}
}
