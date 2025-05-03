package dev.xorkevin.multitool

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

private val LocalScopedViewModelStoreOwner: ProvidableCompositionLocal<ViewModelStoreOwner> =
    staticCompositionLocalOf { throw IllegalStateException("No scoped view model store") }

@Composable
fun ViewModelScope(content: @Composable (() -> Unit)) {
    val activity = getContextComponentActivity(LocalContext.current)
    val store: ViewModelScopeViewModel = viewModel(viewModelStoreOwner = activity)
    val owner = store.getStoreOwner(currentCompositeKeyHash)
    CompositionLocalProvider(LocalScopedViewModelStoreOwner provides owner, content)
}

class ViewModelScopeViewModel : ViewModel() {
    private val map = mutableMapOf<Int, ViewModelStoreOwner>()

    override fun onCleared() {
        map.values.forEach {
            it.viewModelStore.clear()
        }
        map.clear()
        super.onCleared()
    }

    fun getStoreOwner(key: Int): ViewModelStoreOwner =
        map.getOrPut(key) { ScopedViewModelStoreOwner() }
}

private class ScopedViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

private fun getContextComponentActivity(context: Context): ComponentActivity {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) {
            return ctx
        }
        ctx = ctx.baseContext
    }
    throw IllegalStateException("Context is not Activity")
}
