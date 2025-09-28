@file:OptIn(ExperimentalUuidApi::class)

package dev.xorkevin.multitool

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.State
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf
import kotlin.uuid.ExperimentalUuidApi

val LocalViewModelScopeContext: ProvidableCompositionLocal<ViewModelScopeContext<*>?> =
    staticCompositionLocalOf { null }

@Composable
inline fun <reified T : ViewModel> scopedViewModel(factory: ViewModelProvider.Factory? = null): T {
    val ctx =
        LocalViewModelScopeContext.current ?: throw IllegalStateException("No scoped view model")
    val key =
        ctx.getStoreOwnerKey(typeOf<T>()) ?: throw IllegalStateException("No scoped view model")
    val storeOwnerViewModel: StoreOwnerViewModel = viewModel()
    val storeOwner = storeOwnerViewModel.getStoreOwner(key)
    return viewModel(viewModelStoreOwner = storeOwner, factory = factory)
}

@Composable
fun <T : ViewModel> ViewModelScope(
    vararg vmClasses: KClass<out T>,
    content: @Composable (() -> Unit),
) {
    val activity = LocalActivity.current ?: throw IllegalStateException("No activity")
    val storeOwnerViewModel: StoreOwnerViewModel = viewModel()
    val key = ViewModelStoreOwnerKey(currentCompositeKeyHashCode)
    val currentContext = LocalViewModelScopeContext.current
    val scopeContext = remember(currentContext, *vmClasses) {
        vmClasses.fold(currentContext) { acc, vmClass -> ViewModelScopeContext(vmClass, key, acc) }
    }
    remember { CompositionObserver(storeOwnerViewModel, key, activity) }
    CompositionLocalProvider(LocalViewModelScopeContext provides scopeContext, content)
}

private class CompositionObserver(
    private val storeOwnerViewModel: StoreOwnerViewModel,
    private val scopeKey: ViewModelStoreOwnerKey,
    private val activity: Activity,
) : RememberObserver {
    override fun onRemembered() {
        // Nothing to do
    }

    override fun onForgotten() {
        if (!activity.isChangingConfigurations) {
            storeOwnerViewModel.disposeStoreOwner(scopeKey)
        }
    }

    override fun onAbandoned() {
        if (!activity.isChangingConfigurations) {
            storeOwnerViewModel.disposeStoreOwner(scopeKey)
        }
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
            ctx: ViewModelScopeContext<*>, type: KType
        ): ViewModelStoreOwnerKey? = if (ctx.kType.isSubtypeOf(type)) {
            ctx.key
        } else {
            val parent = ctx.parent ?: return null
            getStoreOwnerKey(parent, type)
        }
    }
}

class StoreOwnerViewModel : ViewModel() {
    private val map = mutableMapOf<ViewModelStoreOwnerKey, ScopedViewModelStoreOwner>()

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

class MutableViewModelState<T>(private val state: State<T>, private val flow: MutableStateFlow<T>) {
    var value
        get(): T = state.value
        set(v) {
            flow.value = v
        }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value
    operator fun setValue(thisRef: Any?, property: KProperty<*>, v: T) {
        value = v
    }
}

class MutableViewModelStateFlow<T>(initValue: T) {
    private val _flow = MutableStateFlow(initValue)
    val flow = _flow.asStateFlow()

    fun update(f: (T) -> T) {
        _flow.update(f)
    }

    val value
        get(): T = _flow.value

    @Composable
    fun collectAsStateWithLifecycle(): MutableViewModelState<T> =
        MutableViewModelState(flow.collectAsStateWithLifecycle(), _flow)
}
