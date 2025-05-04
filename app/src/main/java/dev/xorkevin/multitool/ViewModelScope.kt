@file:OptIn(ExperimentalUuidApi::class)

package dev.xorkevin.multitool

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf
import kotlin.uuid.ExperimentalUuidApi

val LocalViewModelScopeContext: ProvidableCompositionLocal<ViewModelScopeContext<*>?> =
    staticCompositionLocalOf { null }

@Composable
inline fun <reified T : ViewModel> scopedViewModel(): T {
    val ctx =
        LocalViewModelScopeContext.current ?: throw IllegalStateException("No scoped view model")
    val key =
        ctx.getStoreOwnerKey(typeOf<T>()) ?: throw IllegalStateException("No scoped view model")
    val storeOwnerViewModel: StoreOwnerViewModel = viewModel()
    val storeOwner = storeOwnerViewModel.getStoreOwner(key)
    return viewModel(viewModelStoreOwner = storeOwner)
}

@Composable
fun <T : ViewModel> ViewModelScope(vmClasses: Array<KClass<T>>, content: @Composable (() -> Unit)) {
    val activity = LocalActivity.current ?: throw IllegalStateException("No activity")
    val lifecycleOwner = LocalLifecycleOwner.current
    val storeOwnerViewModel: StoreOwnerViewModel = viewModel()
    val id = ViewModelStoreOwnerKey(currentCompositeKeyHashCode)
    val currentContext = LocalViewModelScopeContext.current
    val scopeContext = remember(currentContext, *vmClasses) {
        vmClasses.fold(currentContext) { acc, vmClass -> ViewModelScopeContext(vmClass, id, acc) }
    }
    val observer = remember { CompositionObserver(storeOwnerViewModel, id, activity) }
    DisposableEffect(lifecycleOwner, observer) {
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    CompositionLocalProvider(LocalViewModelScopeContext provides scopeContext, content)
}

private class CompositionObserver(
    private val storeOwnerViewModel: StoreOwnerViewModel,
    private val scopeKey: ViewModelStoreOwnerKey,
    private val activity: Activity,
) : RememberObserver, DefaultLifecycleObserver {
    private var isChangingConfigurations = false

    override fun onRemembered() {
        // Nothing to do
    }

    override fun onForgotten() {
        if (!isChangingConfigurations) {
            storeOwnerViewModel.disposeStoreOwner(scopeKey)
        }
    }

    override fun onAbandoned() {
        if (!isChangingConfigurations) {
            storeOwnerViewModel.disposeStoreOwner(scopeKey)
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        isChangingConfigurations = false
    }

    override fun onStart(owner: LifecycleOwner) {
        isChangingConfigurations = false
    }

    override fun onStop(owner: LifecycleOwner) {
        isChangingConfigurations = activity.isChangingConfigurations
    }

    override fun onDestroy(owner: LifecycleOwner) {
        isChangingConfigurations = activity.isChangingConfigurations
    }
}

data class ViewModelStoreOwnerKey(private val v: Long)

class ViewModelScopeContext<T : ViewModel>(
    vmClass: KClass<T>,
    private val key: ViewModelStoreOwnerKey,
    private val parent: ViewModelScopeContext<*>? = null,
) {
    private val kType = vmClass.createType()

    fun getStoreOwnerKey(type: KType): ViewModelStoreOwnerKey? = getStoreOwnerKey(this, type)

    private companion object {
        private tailrec fun getStoreOwnerKey(
            ctx: ViewModelScopeContext<*>,
            type: KType
        ): ViewModelStoreOwnerKey? =
            if (ctx.kType.isSubtypeOf(type)) {
                ctx.key
            } else {
                val parent = ctx.parent ?: return null
                getStoreOwnerKey(parent, type)
            }
    }
}

class StoreOwnerViewModel : ViewModel() {
    private val map = mutableMapOf<ViewModelStoreOwnerKey, ViewModelStoreOwner>()

    override fun onCleared() {
        map.values.forEach {
            it.viewModelStore.clear()
        }
        map.clear()
        super.onCleared()
    }

    fun getStoreOwner(key: ViewModelStoreOwnerKey): ViewModelStoreOwner =
        map.getOrPut(key) { ScopedViewModelStoreOwner() }

    fun disposeStoreOwner(key: ViewModelStoreOwnerKey) {
        map.remove(key)?.also { it.viewModelStore.clear() }
    }
}

private class ScopedViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}
