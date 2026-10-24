package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pages a signed-out reader lands on: the robot with its padlock, what the page holds, and Sign
 * in. They were six one-off drawings of solid shapes; now they are the state every other empty
 * page already is, so there is one way the app says "this needs an account".
 */
@RunWith(AndroidJUnit4::class)
class SignedOutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun the_page_says_what_it_holds_and_offers_sign_in() {
        compose.setContent {
            TestTrackTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RobotState(
                        Prop.Lock,
                        "Sign in to see your apps",
                        "Your apps, and how each one is getting on, belong to your account.",
                        action = "Sign in",
                    )
                }
            }
        }
        compose.onNodeWithText("Sign in to see your apps").assertIsDisplayed()
        compose.onNodeWithText("Sign in").assertIsDisplayed()
    }

    /** The button is the only thing on any of these pages that does anything. */
    @Test
    fun sign_in_is_the_way_out() {
        var asked = 0
        compose.setContent {
            TestTrackTheme {
                RobotState(Prop.Lock, "Sign in to see your apps", "A line.", action = "Sign in", onAction = { asked++ })
            }
        }

        compose.onNodeWithText("Sign in").performClick()

        assertEquals(1, asked)
    }
}
