package com.elitedarkkaiser.redmagic.ui

import org.junit.Assert.*
import org.junit.Test

class GamePickerTest {
    private val apps = listOf(
        GamePickerEntry("com.roblox.client", "Roblox", true, true),
        GamePickerEntry("example.game", "Space game", true, false),
        GamePickerEntry("moe.rukamori.archivetune", "ArchiveTune", false, true),
        GamePickerEntry("example.clock", "Clock", false, false))

    @Test fun filtersKeepUnclassifiedAppsAvailableAndShowExistingProfiles() {
        assertEquals(apps.take(2), GamePickerUi.filter(apps, GamePickerFilter.GAMES, ""))
        assertEquals(apps, GamePickerUi.filter(apps, GamePickerFilter.ALL, ""))
        assertEquals(listOf(apps[0], apps[2]), GamePickerUi.filter(apps, GamePickerFilter.ADDED, ""))
    }
    @Test fun searchMatchesNamesAndPackagesWithoutIgnoringTheSelectedFilter() {
        assertEquals(listOf(apps[0]), GamePickerUi.filter(apps, GamePickerFilter.GAMES, " CLIENT "))
        assertEquals(listOf(apps[2]), GamePickerUi.filter(apps, GamePickerFilter.ALL, "ARCHIVETUNE"))
        assertTrue(GamePickerUi.filter(apps, GamePickerFilter.GAMES, "ARCHIVETUNE").isEmpty())
        assertTrue(GamePickerUi.filter(apps, GamePickerFilter.ADDED, "Space").isEmpty())
    }
    @Test fun clearingSearchRestoresResultsAndUnknownQueriesHaveAnEmptyState() {
        assertTrue(GamePickerUi.filter(apps, GamePickerFilter.ALL, "uninstalled").isEmpty())
        assertEquals(apps, GamePickerUi.filter(apps, GamePickerFilter.ALL, "   "))
    }
}
