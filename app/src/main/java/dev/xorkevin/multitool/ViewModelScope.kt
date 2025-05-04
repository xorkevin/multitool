@file:OptIn(ExperimentalUuidApi::class)

package dev.xorkevin.multitool

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val LocalViewModelScopeContext: ProvidableCompositionLocal<ViewModelScopeContext?> =
    staticCompositionLocalOf { null }

@Composable
inline fun <reified T : ViewModel> scopedViewModel(): T {
    val ctx =
        LocalViewModelScopeContext.current ?: throw IllegalStateException("No scoped view model")
    val id = ctx.getStoreOwnerId(typeOf<T>()) ?: throw IllegalStateException("No scoped view model")
    val activity = getContextComponentActivity(LocalContext.current)
    val viewModelScopeViewModel: StoreOwnerViewModel = viewModel(viewModelStoreOwner = activity)
    val storeOwner = viewModelScopeViewModel.getStoreOwner(id)
    return viewModel(viewModelStoreOwner = storeOwner)
}

@Composable
fun ViewModelScope(vmClasses: List<KClass<ViewModel>>, content: @Composable (() -> Unit)) {
    val uid = rememberSaveable { Uuid.random().toULongs { high, low -> U128(high, low) } }
    CompositionLocalProvider(
        LocalViewModelScopeContext provides vmClasses.fold(
            LocalViewModelScopeContext.current
        ) { acc, vmClass -> ViewModelScopeContext(vmClass, uid, acc) },
        content
    )
}

data class U128(private val high: ULong, private val low: ULong)

class ViewModelScopeContext(
    vmClass: KClass<ViewModel>,
    private val id: U128,
    private val parent: ViewModelScopeContext? = null,
) {
    private val kType = vmClass.createType()

    fun getStoreOwnerId(type: KType): U128? = getStoreOwnerId(this, type)

    companion object {
        private tailrec fun getStoreOwnerId(ctx: ViewModelScopeContext, type: KType): U128? =
            if (ctx.kType.isSubtypeOf(type)) {
                ctx.id
            } else {
                val parent = ctx.parent ?: return null
                getStoreOwnerId(parent, type)
            }
    }
}

class StoreOwnerViewModel : ViewModel() {
    private val map = mutableMapOf<U128, ViewModelStoreOwner>()

    override fun onCleared() {
        map.values.forEach {
            it.viewModelStore.clear()
        }
        map.clear()
        super.onCleared()
    }

    fun getStoreOwner(key: U128): ViewModelStoreOwner =
        map.getOrPut(key) { ScopedViewModelStoreOwner() }
}

private class ScopedViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

tailrec fun getContextComponentActivity(context: Context): ComponentActivity =
    when (context) {
        is ComponentActivity -> context
        is ContextWrapper -> getContextComponentActivity(context.baseContext)
        else -> throw IllegalStateException("Context is not Activity")
    }
