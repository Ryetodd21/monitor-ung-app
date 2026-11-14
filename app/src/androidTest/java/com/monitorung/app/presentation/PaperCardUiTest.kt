package com.monitorung.app.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monitorung.app.presentation.ui.components.*
import com.monitorung.app.presentation.ui.theme.MonitorUNGTheme
import com.monitorung.app.presentation.ui.theme.ThemeVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaperCardUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun smallCardPaddingIsApplied() {
        rule.setContent {
            MonitorUNGTheme {
                PaperCard(Modifier.size(100.dp).testTag("card"), padding = 8.dp) {
                    Box(Modifier.size(20.dp).testTag("child"))
                }
            }
        }
        val card = rule.onNodeWithTag("card").getUnclippedBoundsInRoot()
        val child = rule.onNodeWithTag("child", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(8.dp, child.left - card.left)
        assertEquals(8.dp, child.top - card.top)
    }

    @Test fun entranceReservesLayoutBeforeAnimationFinishes() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Column {
                StaggeredEntrance(index = 4) { Box(Modifier.size(120.dp)) }
                Box(Modifier.size(20.dp).testTag("following"))
            }
        }
        val before = rule.onNodeWithTag("following").getUnclippedBoundsInRoot()
        rule.mainClock.advanceTimeBy(600)
        assertEquals(before, rule.onNodeWithTag("following").getUnclippedBoundsInRoot())
    }

    @Test fun decorativeGradientCardWrapsItsContentWithoutAClickAction() {
        rule.setContent {
            MonitorUNGTheme {
                Column(Modifier.size(300.dp)) {
                    GradientCard(
                        modifier = Modifier.testTag("gradient"),
                        gradient = Brush.horizontalGradient(listOf(Color.Blue, Color.Cyan)),
                    ) { Box(Modifier.size(20.dp)) }
                }
            }
        }
        rule.onNodeWithTag("gradient").assertHasNoClickAction().assertHeightIsEqualTo(52.dp)
    }

    @Test fun counterStartsWithTheActualMeasurement() {
        rule.mainClock.autoAdvance = false
        rule.setContent { MonitorUNGTheme { AnimatedCounter(42) } }
        rule.onNodeWithText("42").assertExists()
    }

    @Test fun disabledButtonKeepsItsNativeSemanticsAndTouchTarget() {
        rule.setContent {
            MonitorUNGTheme { SpringPrimaryButton("Sinkronkan", {}, enabled = false) }
        }
        rule.onNodeWithText("Sinkronkan").assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
    }

    @Test fun navigationExposesSelectionAndChangesTabs() {
        val selected = mutableStateOf("home")
        rule.setContent {
            MonitorUNGTheme {
                BubbleNavBar(
                    items = listOf(
                        NavBarItem("home", "Beranda", Icons.Default.Home, Icons.Default.Home),
                        NavBarItem("schedule", "Jadwal", Icons.Default.DateRange, Icons.Default.DateRange),
                    ),
                    currentRoute = selected.value,
                    onItemSelected = { selected.value = it },
                )
            }
        }
        rule.onNodeWithText("Beranda").assertIsSelected().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Jadwal").performClick().assertIsSelected()
        rule.onNodeWithText("Beranda").assertIsNotSelected()
    }

    @Test fun paperTextAndPrimaryControlsHaveContrastInEveryTheme() {
        val variant = mutableStateOf(ThemeVariant.LIGHT)
        var contrast = emptyList<Double>()
        rule.setContent {
            MonitorUNGTheme(themeVariant = variant.value) {
                val c = MaterialTheme.colorScheme
                contrast = listOf(
                    c.onSurface to c.surface,
                    c.onSurfaceVariant to c.surface,
                    c.onPrimary to c.primary,
                    c.onPrimaryContainer to c.primaryContainer,
                ).map { (foreground, background) ->
                    ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb())
                }
            }
        }
        ThemeVariant.entries.forEach { theme ->
            rule.runOnIdle { variant.value = theme }
            rule.runOnIdle { assertTrue("$theme contrast: $contrast", contrast.all { it >= 4.5 }) }
        }
    }
}
