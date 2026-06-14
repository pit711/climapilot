package com.climapilot.free.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.climapilot.free.R

/**
 * EN: Dialog to add a device by hand (IP, port, device id, optional name) when broadcast discovery
 *     isn't possible. The Add button stays disabled until IP, port and id are valid.
 * DE: Dialog zum Hinzufügen eines Geräts von Hand (IP, Port, Geräte-ID, optionaler Name), wenn die
 *     Broadcast-Suche nicht möglich ist. Der Hinzufügen-Knopf bleibt deaktiviert, bis IP, Port und ID gültig sind.
 */
@Composable
fun ManualDeviceDialog(
    onDismiss: () -> Unit,
    onAdd: (ip: String, port: Int, id: Long, name: String) -> Unit,
) {
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("6444") }   // EN: default Midea V3 control port / DE: Standard-Steuerport für Midea V3
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    // EN: Lightweight client-side validation before enabling "Add". / DE: Leichte client-seitige Prüfung, bevor „Hinzufügen" aktiv wird.
    val ipOk = ip.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))
    val idOk = id.toLongOrNull() != null
    val portOk = port.toIntOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_title)) },
        text = {
            Column {
                Text(stringResource(R.string.manual_hint), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = ip, onValueChange = { ip = it.trim() },
                    label = { Text(stringResource(R.string.field_ip)) }, singleLine = true,
                    placeholder = { Text("192.168.1.50") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port, onValueChange = { port = it.trim() },
                    label = { Text(stringResource(R.string.field_port)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = id, onValueChange = { id = it.trim() },
                    label = { Text(stringResource(R.string.field_id)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ipOk && idOk && portOk,
                onClick = { onAdd(ip, port.toInt(), id.toLong(), name) },
            ) { Text(stringResource(R.string.action_add), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
