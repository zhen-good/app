package com.example.thelastone.ui.screens.comp.placedetaildialog.comp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.thelastone.data.model.PlaceLite

@Composable
fun ActionButtonsRow(
    place: PlaceLite,
    rightButtonLabel: String,
    onRightButtonClick: () -> Unit,
    onLeftCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onLeftCancel,
            modifier = Modifier.weight(1f)
        ) { Text("取消") }

        Button(
            onClick = onRightButtonClick,
            modifier = Modifier.weight(1f)
        ) { Text(rightButtonLabel) }
    }
}
enum class PlaceActionMode {
    ADD_TO_ITINERARY, ADD_TO_FAVORITE, REMOVE_FROM_FAVORITE, REPLACE_IN_ITINERARY
}