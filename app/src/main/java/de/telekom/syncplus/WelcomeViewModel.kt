package de.telekom.syncplus

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class WelcomeViewModel(app: Application) : AndroidViewModel(app) {

    var wasPermissionsRequested = false

}