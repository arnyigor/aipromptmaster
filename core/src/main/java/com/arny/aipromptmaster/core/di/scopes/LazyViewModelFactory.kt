package com.arny.aipromptmaster.core.di.scopes

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import kotlin.reflect.KClass

class LazyViewModelFactory<VM : ViewModel>(
    private val clazz: KClass<VM>,
    private val factory: () -> VM
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass == clazz.java) {
            @Suppress("UNCHECKED_CAST")
            return factory() as T
        }

        error("Unknown ViewModel type $modelClass")
    }

    companion object {
        inline fun <reified VM : ViewModel> create(
            noinline factory: () -> VM
        ): LazyViewModelFactory<VM> = LazyViewModelFactory(VM::class, factory)
    }
}

inline fun <reified VM : ViewModel> Fragment.viewModelFactory(
    noinline ownerProducer: () -> ViewModelStoreOwner = { this },
    noinline factory: () -> VM,
): Lazy<VM> = viewModels(
    ownerProducer = ownerProducer,
    factoryProducer = { LazyViewModelFactory.create(factory) }
)

inline fun <reified VM : ViewModel> ComponentActivity.viewModelFactory(
    noinline factory: () -> VM,
): Lazy<VM> = viewModels(
    factoryProducer = { LazyViewModelFactory.create(factory) }
)

