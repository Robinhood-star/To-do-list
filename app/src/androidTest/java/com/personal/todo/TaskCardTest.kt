package com.personal.todo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personal.todo.data.TaskEntity
import com.personal.todo.domain.TaskRow
import com.personal.todo.ui.TaskCard
import com.personal.todo.ui.TodoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TaskCardTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsTitleCategoryAndTogglesViaCheckbox() {
        var toggled = false
        rule.setContent {
            TodoTheme(dark = false) {
                TaskCard(
                    row = TaskRow(TaskEntity(id = 1, title = "Buy milk"), "Shopping", 1, 3),
                    today = LocalDate.now(),
                    onToggle = { toggled = true }, onOpen = {}, onSwipeComplete = {}, onSwipeDelete = {},
                )
            }
        }
        rule.onNodeWithText("Buy milk").assertIsDisplayed()
        rule.onNodeWithText("#Shopping").assertIsDisplayed()
        rule.onNodeWithText("1/3").assertIsDisplayed()
        rule.onNodeWithContentDescription("Mark Buy milk as done").performClick()
        assertTrue(toggled)
    }
}
