package de.telekom.syncplus.util.viewbinding

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

abstract class ViewBindingProperty<T : LifecycleOwner, VB : ViewBinding>(
    private val factory: () -> VB,
) : ReadOnlyProperty<T, VB> {
    private var _binding: VB? = null

    private val observer =
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                _binding = null
            }
        }

    abstract fun getLifecycle(ref: T): Lifecycle

    override fun getValue(
        thisRef: T,
        property: KProperty<*>,
    ): VB {
        if (_binding == null) {
            _binding = factory()
            getLifecycle(thisRef).addObserver(observer)
        }
        return _binding!!
    }
}
