package pl.kakebo.ui.splash

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pl.kakebo.ui.theme.KakeboTheme
import pl.kakebo.SplashStyle

@RunWith(AndroidJUnit4::class)
class KakeboSplashTest {
    @get:Rule val compose = createComposeRule()

    @Test fun animationOpensAppWithoutUserInteraction() {
        assertAnimationCompletes(SplashStyle.SUN, 2400)
    }

    @Test fun inkAnimationOpensAppWithoutUserInteraction() {
        assertAnimationCompletes(SplashStyle.INK, 3400)
    }

    private fun assertAnimationCompletes(style: SplashStyle, duration: Long) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            var finished by remember { mutableStateOf(false) }
            KakeboTheme {
                if (finished) Text("App content")
                else KakeboSplash(style) { finished = true }
            }
        }
        compose.onNodeWithTag("startup_splash").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithTag("startup_splash").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(duration - 1_200 + 300)
        compose.onNodeWithText("App content").assertIsDisplayed()
        compose.onNodeWithTag("startup_splash").assertDoesNotExist()
    }
}
