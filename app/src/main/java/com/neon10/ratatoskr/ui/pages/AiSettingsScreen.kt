package com.neon10.ratatoskr.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AiSettingsScreen(modifier: Modifier = Modifier) {
    var token by remember { mutableStateOf("") }
    var timeout by remember { mutableStateOf("1500") }
    var limit by remember { mutableStateOf("3") }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("AI 设置", style = MaterialTheme.typography.headlineSmall)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = limit, onValueChange = { limit = it }, label = { Text("候选条数(3–5)") })
                OutlinedTextField(value = timeout, onValueChange = { timeout = it }, label = { Text("超时(ms)") })
                OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("鉴权 Token") })
                Button(onClick = { /* 保存设置 */ }) { Text("保存") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("风格权重", style = MaterialTheme.typography.titleMedium)
                Text("稳妥/推进/幽默：后续版本提供权重滑杆。")
            }
        }
    }
}
