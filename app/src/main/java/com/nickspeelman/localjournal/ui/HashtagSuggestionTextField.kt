package com.nickspeelman.localjournal.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.nickspeelman.localjournal.data.HashtagUtils

@Composable
fun HashtagSuggestionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    knownHashtags: List<String>,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "Optional note or #tags",
    minLines: Int = 2,
    maxLines: Int = 5,
    focusRequester: FocusRequester? = null
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }

    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    val activeHashtag = if (fieldValue.selection.collapsed) {
        HashtagUtils.activeHashtag(fieldValue.text, fieldValue.selection.start)
    } else {
        null
    }
    val suggestions = HashtagUtils.suggestions(knownHashtags, activeHashtag)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                onValueChange(it.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester)
                    else Modifier
                ),
            label = label?.let { { Text(it) } },
            placeholder = { Text(placeholder) },
            minLines = minLines,
            maxLines = maxLines
        )

        if (suggestions.isNotEmpty() && activeHashtag != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { suggestion ->
                    AssistChip(
                        onClick = {
                            val addSpace = activeHashtag.end == fieldValue.text.length
                            val replacement = suggestion + if (addSpace) " " else ""
                            val updated = fieldValue.text.replaceRange(
                                activeHashtag.start,
                                activeHashtag.end,
                                replacement
                            )
                            val cursor = activeHashtag.start + replacement.length
                            fieldValue = TextFieldValue(updated, TextRange(cursor))
                            onValueChange(updated)
                        },
                        label = { Text(suggestion) }
                    )
                }
            }
        }
    }
}
