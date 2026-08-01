package space.liushenme.markdownreader.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import space.liushenme.markdownreader.R

/** 书架 / 笔记等页共用的圆角搜索框（BasicTextField + 收紧内边距，避免 M3 SearchBar 裁切文字） */
@Composable
fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    clearContentDescription: String = stringResource(R.string.search_clear_cd),
    /** 为 true 时进入页面自动聚焦并弹出键盘。 */
    autoFocus: Boolean = false,
) {
    val shape = RoundedCornerShape(24.dp)
    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val hintColor = onSurface.copy(alpha = 0.5f)
    val textStyle = MaterialTheme.typography.bodyMedium.copy(color = onSurface)
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = bg,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = hintColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = textStyle,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { keyboardController?.hide() },
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = hintColor,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = clearContentDescription,
                        modifier = Modifier.size(18.dp),
                        tint = hintColor,
                    )
                }
            }
        }
    }
}
