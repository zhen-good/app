package com.example.thelastone.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.thelastone.data.model.StartInfo

@Composable
fun StartPreviewDialog(
    info: StartInfo,
    onDismiss: () -> Unit,
    onConfirmDepart: () -> Unit,
    onChangePlan: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("出發前資訊") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 天氣
                Text("天氣：${info.weather.summary}，${info.weather.temperatureC}°C" +
                        (info.weather.rainProbability?.let { "，降雨機率 $it%" } ?: ""))

                // 營業資訊（優先 openStatusText）
                val status = info.openStatusText ?: "營業資訊：${if (info.openNow == true) "營業中" else if (info.openNow == false) "未營業" else "—"}"
                Text(status)

                // 營業時間（擇要）
                if (info.openingHours.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("營業時間：")
                    info.openingHours.take(3).forEach { Text("• $it") } // 節省空間
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirmDepart) { Text("確定出發") }
        },
        dismissButton = {
            TextButton(onClick = onChangePlan) { Text("更換行程") }
        }
    )
}