@file:OptIn(
    ExperimentalMaterial3Api::class, ExperimentalStdlibApi::class,
    ExperimentalCoroutinesApi::class
)

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun HashTool() = ViewModelScope(arrayOf(HashViewModel::class)) {
    val scrollState = rememberScrollState()
    val hashViewModel: HashViewModel = scopedViewModel()

    val input by hashViewModel.input.collectAsStateWithLifecycle()
    val hashes by hashViewModel.hashes.collectAsStateWithLifecycle(emptyList())

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        TextField(
            value = input,
            onValueChange = { hashViewModel.updateInput(it) },
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

data class HashResult(val name: String, val value: String)

class HashViewModel : ViewModel() {
    private val _input = MutableStateFlow("")
    val input = _input.asStateFlow()
    fun updateInput(value: String) {
        _input.update { value }
    }

    val hashes = input.mapLatest {
        delay(250.milliseconds)
        computeHashes(it)
    }


    private companion object {
        private val hashAlgs = listOf("SHA-256", "SHA-512")

        private fun computeHashes(value: String): List<HashResult> {
            val valueBytes = value.toByteArray()
            return hashAlgs.map {
                HashResult(
                    name = it,
                    value = MessageDigest.getInstance(it).digest(valueBytes).toHexString()
                )
            }
        }
    }
}
