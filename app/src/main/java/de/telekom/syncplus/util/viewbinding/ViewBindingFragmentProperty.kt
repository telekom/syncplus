package de.telekom.syncplus.util.viewbinding

import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.viewbinding.ViewBinding

class ViewBindingFragmentProperty<T : ViewBinding>(
    factory: () -> T,
) : ViewBindingProperty<Fragment, T>(factory) {
    override fun getLifecycle(ref: Fragment): Lifecycle {
        return ref.viewLifecycleOwner.lifecycle
    }
}

inline fun <T : ViewBinding> Fragment.viewBinding(crossinline factory: (View) -> T) =
    ViewBindingFragmentProperty {
        val view = requireNotNull(view) { "Binding cannot be used outside of view lifecycle" }
        factory(view)
    }
