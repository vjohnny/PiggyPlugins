package com.piggyplugins.krakenprayerflicker;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.kraken.api.Context;
import com.kraken.api.core.interaction.InteractionManager;
import com.kraken.api.service.prayer.PrayerService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.HotkeyListener;

/**
 * Quick-prayer flicker for the Kraken client.
 *
 * <p>Behavior follows Ethan Vann's Prayer Flicker, maintained by Piggy Plugins
 * ({@code com.example.PrayerFlicker.EthanPrayerFlickerPlugin}). Clicks go through
 * Kraken API 5.4.1 ({@link PrayerService} and {@link InteractionManager}) instead
 * of PacketUtils. Kraken API is GPL-3.0: https://github.com/Kraken-Plugins/kraken-api
 */
@PluginDescriptor(
        name = "<html><font color=\"#FF9DF9\">[PP]</font> Kraken Prayer Flicker</html>",
        description = "Quick-prayer flicker by Ethan Vann (PiggyPlugins), using the Kraken API",
        enabledByDefault = false,
        tags = {"ethan", "kraken", "prayer"}
)
@Slf4j
public class KrakenPrayerFlickerPlugin extends Plugin {
    @Inject
    private Context ctx;
    @Inject
    private PrayerService prayerService;
    @Inject
    private InteractionManager interactionManager;
    @Inject
    private KeyManager keyManager;
    @Inject
    private KrakenPrayerFlickerConfig config;

    private boolean toggle;

    @Provides
    public KrakenPrayerFlickerConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(KrakenPrayerFlickerConfig.class);
    }

    @Override
    public void startUp() {
        keyManager.registerKeyListener(prayerToggle);
    }

    @Override
    public void shutDown() {
        log.info("Shutdown");
        keyManager.unregisterKeyListener(prayerToggle);
        toggle = false;
        if (!loggedIn()) {
            return;
        }
        // Matches EthanPrayerFlickerPlugin.shutDown: turn quick prayers off if they are on.
        prayerService.disableQuickPrayers();
    }

    /**
     * Click one quick-prayer slot, then toggle the quick-prayer orb twice so the
     * on/off state is unchanged. Same sequence as
     * {@code EthanPrayerFlickerPlugin.switchAndUpdatePrayers}.
     */
    public void switchAndUpdatePrayers(int quickPrayerIndex) {
        interactionManager.interact(InterfaceID.Quickprayer.BUTTONS, quickPrayerIndex, -1, 1);
        prayerService.toggleQuickPrayers();
        prayerService.toggleQuickPrayers();
    }

    /**
     * Two orb toggles, so quick prayers end in the state they started in.
     * {@link PrayerService#flickQuickPrayers()} is not used here: when quick prayers
     * are off it only enables them once.
     */
    public void updatePrayers() {
        prayerService.toggleQuickPrayers();
        prayerService.toggleQuickPrayers();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (toggle) {
            if (event.getParam1() == InterfaceID.Quickprayer.BUTTONS) {
                if ("Quick Prayer Update".equals(event.getMenuOption())) {
                    updatePrayers();
                    event.consume();
                    return;
                }
                event.consume();
                switchAndUpdatePrayers(event.getParam0());
            }
        }
        if (config.minimapToggle() && event.getId() == 1 && event.getParam1() == InterfaceID.Orbs.PRAYERBUTTON) {
            toggleFlicker();
            event.consume();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!loggedIn() || !toggle) {
            return;
        }
        if (prayerService.isOutOfPrayer()) {
            return;
        }
        // Same tick sequence as EthanPrayerFlickerPlugin.onGameTick:
        // off then on when already active, otherwise just turn them on.
        prayerService.flickQuickPrayers();
    }

    private final HotkeyListener prayerToggle = new HotkeyListener(() -> config.toggle()) {
        @Override
        public void hotkeyPressed() {
            toggleFlicker();
        }
    };

    public void toggleFlicker() {
        toggle = !toggle;
        if (!loggedIn()) {
            return;
        }
        if (!toggle) {
            prayerService.disableQuickPrayers();
        }
    }

    public void toggleFlicker(boolean on) {
        toggle = on;
        if (!loggedIn()) {
            return;
        }
        if (!toggle) {
            prayerService.disableQuickPrayers();
        }
    }

    private boolean loggedIn() {
        return ctx.getClient().getGameState() == GameState.LOGGED_IN;
    }
}
