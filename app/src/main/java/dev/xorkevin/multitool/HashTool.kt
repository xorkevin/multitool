@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalStdlibApi::class)

package dev.xorkevin.multitool

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds

data class HashResult(val name: String, val value: String)

val hashAlgs = listOf("SHA-256", "SHA-512")

@Composable
fun HashTool() {
    var inp by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    var hashes by remember { mutableStateOf(emptyList<HashResult>()) }
    LaunchedEffect(inp) {
        delay(250.milliseconds)
        val inpBytes = inp.toByteArray()
        hashes = hashAlgs.map {
            HashResult(
                name = it,
                value = MessageDigest.getInstance(it).digest(inpBytes).toHexString()
            )
        }
    }

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        TextField(
            value = inp,
            onValueChange = { inp = it },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        )
        hashes.forEach {
            Text(
                text = it.name, modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .fillMaxWidth()
            )
            Text(
                text = it.value, fontFamily = FontFamily.Monospace, modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .fillMaxWidth()
            )
        }
    }
}
