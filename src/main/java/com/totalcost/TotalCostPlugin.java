package com.totalcost;

import com.totalcost.models.ItemModel;
import com.google.inject.Provides;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(name = "Total Cost")
public class TotalCostPlugin extends Plugin
{
	private static final int SHOP_WIDGETID = WidgetID.SHOP_GROUP_ID;
	private static final Pattern TAG_REGEXP = Pattern.compile("<[^>]*>");
	private static final Pattern NUM_REGEXP = Pattern.compile("[^0-9]+");
	private static ItemModel item = new ItemModel(0, null, 0, 0, 0, 0);
	@Inject
	private Client client;
	@Inject
	private ItemManager itemManager;
	@Inject
	private TotalCostConfig config;

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked)
	{
		if (menuOptionClicked.getMenuOption().equals("Value"))
		{
			item.setName(removeTags(menuOptionClicked.getMenuTarget(), TAG_REGEXP));
			try
			{
				if (client.getWidget(SHOP_WIDGETID, 16) != null)
				{
					Widget shopWidget = client.getWidget(SHOP_WIDGETID, 16);
					Widget[] shopItems = shopWidget.getChildren();
					for (Widget shopItem : shopItems)
					{
						if (shopItem.getName().contains(item.getName()))
						{
							int id = itemManager.canonicalize(shopItem.getItemId());
							item.setId(shopItem.getItemId());
							item.setQuantity(shopItem.getItemQuantity());
							item.setHaPrice(getHighAlchPrice(id));
							item.setValue(convertHighAlchPriceToValue(item.getHaPrice()));
							break;
						}
					}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (chatMessage.getMessage().contains("coins") && chatMessage.getType() == ChatMessageType.GAMEMESSAGE && item.getName() != null)
		{
			try
			{
				if (chatMessage.getMessage().contains(item.getName()))
				{
					float buyTenCost = 0;
					float buyFiftyCost = 0;
					float buyAllCost = 0;

					int price = Integer.parseInt(removeTags(chatMessage.getMessage(), NUM_REGEXP));
					item.setCurrentPrice(price);

					for (int i = 1; i <= item.getQuantity(); i++)
					{
						double priceFromQuantity = price + Math.floor(price * i * 0.001);
						if (i <= 10)
						{
							buyTenCost += priceFromQuantity;
						}
						if (i <= 50)
						{
							buyFiftyCost += priceFromQuantity;
						}
						buyAllCost += priceFromQuantity;
					}

					String itemString = addColorTags(item.getName());
					String quantityString = addColorTags(String.valueOf(item.getQuantity()));
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Total cost for " + itemString + " with " + quantityString + " in stock:", null);

					if (config.buyTen() && item.getQuantity() >= 10)
					{
						generateChatMessage(10, (int) buyTenCost);
					}

					if (config.buyFifty() && item.getQuantity() >= 50)
					{
						generateChatMessage(50, (int) buyFiftyCost);
					}

					if (config.buyAll())
					{
						generateChatMessage(item.getQuantity(), (int) buyAllCost);
					}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	@Provides
	TotalCostConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TotalCostConfig.class);
	}

	private static String removeTags(String str, Pattern pattern)
	{
		StringBuffer stringBuffer = new StringBuffer();
		Matcher matcher = pattern.matcher(str);
		while (matcher.find())
		{
			matcher.appendReplacement(stringBuffer, "");
			String match = matcher.group(0);
			switch (match)
			{
				case "<lt>":
				case "<gt>":
					stringBuffer.append(match);
					break;
			}
		}
		matcher.appendTail(stringBuffer);
		return stringBuffer.toString();
	}

	private int getHighAlchPrice(int itemId)
	{
		ItemComposition itemDef = itemManager.getItemComposition(itemId);
		return itemDef.getHaPrice();
	}

	private static int convertHighAlchPriceToValue(int highAlchPrice)
	{
		// Inverse of (Price * 0.6)
		return (highAlchPrice * 5) / 3;
	}

	private void generateChatMessage(int quantity, int cost)
	{
		if (item.getQuantity() > 0)
		{
			String coloredAmount = addColorTags(String.valueOf(quantity));
			String coloredCost = addColorTags(String.valueOf(cost));
			String average = String.valueOf(cost / quantity);
			average = addColorTags(average);
			String message = "Buy " + coloredAmount + " price: ~" + coloredCost + " coins.";
			if (config.showAvg())
			{
				message += " (Avg: " + average + ")";
			}

			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
		}
	}

	private String addColorTags(String string)
	{
		String hexColor = Integer.toHexString(config.highlightColor().getRGB()).substring(2);
		return "<col=" + hexColor + ">" + string + "</col>";
	}
}
