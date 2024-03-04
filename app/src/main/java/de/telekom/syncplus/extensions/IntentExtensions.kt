import android.content.Intent
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Parcelable
import java.io.Serializable

inline fun <reified T : Serializable> Intent.serializable(key: String): T? =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            getSerializableExtra(
                key,
                T::class.java,
            )
        else ->
            @Suppress("DEPRECATION")
            getSerializableExtra(key)
                as? T
    }

inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            getParcelableExtra(
                key,
                T::class.java,
            )
        else ->
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
                as? T
    }

inline fun <reified T : Parcelable> Intent.parcelableArrayList(key: String): ArrayList<T>? =
    when {
        SDK_INT >= 33 -> getParcelableArrayListExtra(key, T::class.java)
        else ->
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra(key)
    }
