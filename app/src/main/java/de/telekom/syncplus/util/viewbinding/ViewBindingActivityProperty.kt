package de.telekom.syncplus.util.viewbinding

import android.view.View
import androidx.annotation.IdRes
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.viewbinding.ViewBinding

class ViewBindingActivityProperty<T : ViewBinding>(
    factory: () -> T,
) : ViewBindingProperty<FragmentActivity, T>(factory) {
    override fun getLifecycle(ref: FragmentActivity): Lifecycle {
        return ref.lifecycle
    }
}

inline fun <T : ViewBinding> FragmentActivity.viewBinding(
    @IdRes rootViewId: Int,
    crossinline creator: (View) -> T,
) = ViewBindingActivityProperty {
    val view =
        requireNotNull(window.peekDecorView()) {
            "Binding cannot be used outside of view lifecycle"
        }
    val rootView = view.findViewById<View>(rootViewId)
    creator(rootView)
}
