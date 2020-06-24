/*
 * Copyright (c) 2020, Hydrox6 <ikada@protonmail.ch>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.hydrox.transmog;

import io.hydrox.transmog.ui.CustomSprites;
import io.hydrox.transmog.ui.UIManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Varbits;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuShouldLeftClick;
import net.runelite.api.events.ResizeableChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import javax.inject.Inject;
import java.util.Arrays;

@PluginDescriptor(
	name = "Transmogrification",
	description = "Wear the armour you want, no matter what you're doing.",
	tags = {"fashion", "equipment"}
)
@Slf4j
public class TransmogrificationPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private TransmogrificationConfigManager config;

	@Inject
	private TransmogrificationManager transmogManager;

	@Inject
	private UIManager uiManager;

	private int lastWorld = 0;
	private int old384 = 0;

	@Getter
	private boolean inPvpSituation;

	@Override
	public void startUp()
	{
		spriteManager.addSpriteOverrides(CustomSprites.values());

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			lastWorld = client.getWorld();
			transmogManager.loadData();
			transmogManager.updateTransmog();
			clientThread.invoke(() ->
				{
					uiManager.createInitialUI();
					updatePvpState();
				});
		}
	}

	@Override
	public void shutDown()
	{
		spriteManager.removeSpriteOverrides(CustomSprites.values());
		transmogManager.shutDown();
		uiManager.shutDown();
		lastWorld = 0;
		old384 = 0;
	}

	private void updatePvpState()
	{
		final boolean newState = client.getVar(Varbits.PVP_SPEC_ORB) == 1;

		if (newState != inPvpSituation)
		{
			inPvpSituation = newState;
			transmogManager.onPvpChanged(newState);
			uiManager.onPvpChanged(newState);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGGED_IN)
		{
			if (client.getWorld() != lastWorld)
			{
				lastWorld = client.getWorld();
				transmogManager.loadData();
			}
		} else if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING)
		{
			lastWorld = 0;
			uiManager.setUiCreated(false);
			transmogManager.clearUserStates();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		if (e.getContainerId() != InventoryID.EQUIPMENT.getId() || !config.transmogActive())
		{
			return;
		}

		transmogManager.reapplyTransmog();
	}

	private boolean forceRightClickFlag;

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded e)
	{
		if (e.getTarget().equals("<col=ff981f><col=004356>"))
		{
			forceRightClickFlag = true;
		}
	}

	@Subscribe
	public void onMenuShouldLeftClick(MenuShouldLeftClick e)
	{
		if (!forceRightClickFlag)
		{
			return;
		}

		forceRightClickFlag = false;
		MenuEntry[] menuEntries = client.getMenuEntries();
		for (MenuEntry entry : menuEntries)
		{
			if (entry.getTarget().equals("<col=ff981f><col=004356>"))
			{
				e.setForceRightClick(true);
				return;
			}
		}
	}

	@Subscribe
	public void onResizeableChanged(ResizeableChanged e)
	{
		uiManager.onResizeableChanged();
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired e)
	{
		if (e.getScriptId() == 914 && !uiManager.isUiCreated())
		{
			uiManager.createInitialUI();
			uiManager.setUiCreated(true);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged e)
	{
		updatePvpState();
	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged e)
	{
		// idk what VarCInt 384 is for, but it only changes when the player gets past the splash screen
		if (e.getIndex() == 384)
		{
			int new384 = client.getVarcIntValue(384);
			if (new384 != old384)
			{
				old384 = new384;
				transmogManager.updateTransmog();
			}
		}
	}

	private int lastHash;
	private boolean forceChangedFlag;

	@Subscribe
	public void onGameTick(GameTick e)
	{
		if (!config.transmogActive())
		{
			return;
		}
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}
		final int currentHash = Arrays.hashCode(local.getPlayerComposition().getEquipmentIds());
		if (currentHash != lastHash)
		{
			forceChangedFlag = true;
			lastHash = currentHash;
		}
	}

	@Subscribe
	public void onClientTick(ClientTick e)
	{
		if (forceChangedFlag)
		{
			transmogManager.updateTransmog();
			forceChangedFlag = false;
		}

		if (uiManager.getPlayerPreview() != null)
		{
			uiManager.getPlayerPreview().tickRotation();
		}
	}
}