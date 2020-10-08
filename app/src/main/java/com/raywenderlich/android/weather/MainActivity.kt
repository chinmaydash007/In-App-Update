package com.raywenderlich.android.weather

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.tasks.Task
import com.raywenderlich.android.weather.api.IWeatherDataAvailable
import com.raywenderlich.android.weather.api.OpenWeatherAPI
import com.raywenderlich.android.weather.model.WeatherData
import com.raywenderlich.android.weather.utils.Utils
import kotlinx.android.synthetic.main.activity_main.*

private const val TAG = "MainActivity"
private const val REQUEST_UPDATE = 100
private const val REQUEST_PERMISSIONS = 200
private const val APP_UPDATE_TYPE_SUPPORTED = AppUpdateType.IMMEDIATE

class MainActivity : AppCompatActivity(), IWeatherDataAvailable {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var updateListener: InstallStateUpdatedListener

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        btn_request.setOnClickListener {
            requestLocationPermission()
        }

        btn_retry.setOnClickListener {
            getLastKnownLocation()
        }

        checkForUpdates()
    }

    private fun checkForUpdates() {
        val appUpdateManager: AppUpdateManager
        if (BuildConfig.DEBUG) {
            appUpdateManager = FakeAppUpdateManager(baseContext)
            appUpdateManager.setUpdateAvailable(2)
        } else {
            appUpdateManager = AppUpdateManagerFactory.create(baseContext)
        }

        val appUpdateInfo = appUpdateManager.appUpdateInfo

        appUpdateInfo.addOnSuccessListener {
            handleUpdate(appUpdateManager, appUpdateInfo)
        }.addOnFailureListener { exception ->
            Log.d("mytag", exception.message.toString())
        }
    }


    override fun onResume() {
        super.onResume()

        if (Utils.hasLocationPermission(baseContext)) {
            getLastKnownLocation()
        } else {
            requestLocationPermission()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perm: Array<out String>, result: IntArray) {
        if (code == REQUEST_PERMISSIONS && result.count() > 0) {
            if (result[0] == PackageManager.PERMISSION_DENIED) {
                btn_request.visibility = View.VISIBLE
            } else {
                btn_request.visibility = View.GONE
                getLastKnownLocation()
            }
        }
        super.onRequestPermissionsResult(code, perm, result)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (REQUEST_UPDATE == requestCode) {
            Log.d(TAG, "Updated ended. Result code=${resultCode}")

            when (resultCode) {
                Activity.RESULT_OK -> {
                    if (APP_UPDATE_TYPE_SUPPORTED == AppUpdateType.IMMEDIATE) {
                        Toast.makeText(baseContext, R.string.toast_updated, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(baseContext, R.string.toast_started, Toast.LENGTH_SHORT).show()
                    }
                }
                Activity.RESULT_CANCELED -> {
                    Toast.makeText(baseContext, R.string.toast_cancelled, Toast.LENGTH_SHORT).show()
                }
                ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> {
                    Toast.makeText(baseContext, R.string.toast_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleUpdate(manager: AppUpdateManager, info: Task<AppUpdateInfo>) {
        if (APP_UPDATE_TYPE_SUPPORTED == AppUpdateType.IMMEDIATE) {
            handleImmediateUpdate(manager, info)
        } else if (APP_UPDATE_TYPE_SUPPORTED == AppUpdateType.FLEXIBLE) {
            handleFlexibleUpdate(manager, info)
        }
    }

    private fun handleImmediateUpdate(manager: AppUpdateManager, info: Task<AppUpdateInfo>) {
        if ((info.result.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                        info.result.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS)
                && info.result.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {

            manager.startUpdateFlowForResult(info.result,
                    AppUpdateType.IMMEDIATE, this, REQUEST_UPDATE)
        }

//         Simulates an immediate update
        if (BuildConfig.DEBUG) {
            val fakeAppUpdate = manager as FakeAppUpdateManager
            if (fakeAppUpdate.isImmediateFlowVisible) {
                fakeAppUpdate.userAcceptsUpdate()
                fakeAppUpdate.downloadStarts()
                fakeAppUpdate.downloadCompletes()
                launchRestartDialog(manager)
            }
        }
    }

    private fun handleFlexibleUpdate(manager: AppUpdateManager, info: Task<AppUpdateInfo>) {
        if (info.result.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                && info.result.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            handleImmediateUpdate(manager, info)
            return
        }

        if ((info.result.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                        info.result.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS)
                && info.result.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {

            btn_update.visibility = View.VISIBLE
            setUpdateAction(manager, info)
        }
    }

    private fun setUpdateAction(manager: AppUpdateManager, info: Task<AppUpdateInfo>) {
        btn_update.setOnClickListener {

            updateListener = InstallStateUpdatedListener {
                btn_update.visibility = View.GONE
                tv_status.visibility = View.VISIBLE

                when (it.installStatus()) {
                    InstallStatus.FAILED, InstallStatus.UNKNOWN -> {
                        tv_status.text = getString(R.string.info_failed)
                        btn_update.visibility = View.VISIBLE
                    }
                    InstallStatus.PENDING -> {
                        tv_status.text = getString(R.string.info_pending)
                    }
                    InstallStatus.CANCELED -> {
                        tv_status.text = getString(R.string.info_canceled)
                    }
                    InstallStatus.DOWNLOADING -> {
                        tv_status.text = getString(R.string.info_downloading)
                    }
                    InstallStatus.DOWNLOADED -> {
                        tv_status.text = getString(R.string.info_installing)
                        launchRestartDialog(manager)
                    }
                    InstallStatus.INSTALLING -> {
                        tv_status.text = getString(R.string.info_installing)
                    }
                    InstallStatus.INSTALLED -> {
                        tv_status.text = getString(R.string.info_installed)
                        manager.unregisterListener(updateListener)
                    }
                    else -> {
                        tv_status.text = getString(R.string.info_restart)
                    }
                }
            }

            manager.registerListener(updateListener)
            manager.startUpdateFlowForResult(info.result, AppUpdateType.FLEXIBLE, this,
                    REQUEST_UPDATE)

            // Simulates a flexible update
            if (BuildConfig.DEBUG) {
                val fakeAppUpdate = manager as FakeAppUpdateManager
                if (fakeAppUpdate.isConfirmationDialogVisible) {
                    fakeAppUpdate.userAcceptsUpdate()
                    fakeAppUpdate.downloadStarts()
                    fakeAppUpdate.downloadCompletes()
                    fakeAppUpdate.completeUpdate()
                    fakeAppUpdate.installCompletes()
                }
            }
        }
    }

    private fun launchRestartDialog(manager: AppUpdateManager) {
        AlertDialog.Builder(this)
                .setTitle(getString(R.string.update_title))
                .setMessage(getString(R.string.update_message))
                .setPositiveButton(getString(R.string.action_restart)) { _, _ ->
                    manager.completeUpdate()
                }
                .create().show()
    }


    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_PERMISSIONS)
    }

    private fun getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    Log.d(TAG, "Last known location=$location")

                    if (location == null) {
                        if (Utils.isLocationServiceEnabled(baseContext)) {
                            tv_description.text = getString(R.string.no_location)
                        } else {
                            tv_description.text = getString(R.string.not_available)
                        }

                        btn_retry.visibility = View.VISIBLE
                        return@addOnSuccessListener
                    }
                    btn_retry.visibility = View.GONE
                    val weather = OpenWeatherAPI()
                    weather.getForecastInformation(location.latitude, location.longitude, this)
                }
                .addOnFailureListener { exception ->
                    Log.w(TAG, "Unable to get last known location")

                    when (exception) {
                        is SecurityException -> {
                            tv_description.text = getString(R.string.no_permission)
                        }
                        else -> {
                            tv_description.text = getString(R.string.not_available)
                        }
                    }
                }
    }

    //region IWeatherDataAvailable

    override fun onNewWeatherDataUnavailable() {
        Log.w(TAG, "No data available for current location")
        tv_temperature.text = getString(R.string.default_temp)
        tv_description.text = getString(R.string.no_connection)
    }

    override fun onNewWeatherDataAvailable(data: WeatherData) {
        Log.w(TAG, "On new data available")

        tv_location.text = data.name
        tv_date.text = Utils.getCurrentDate()
        tv_temperature.text = getString(R.string.temp_celsius, Utils.formatTemp(data.main.temp))
        tv_description.text = data.weather[0].description

        val minTemp = Utils.formatTemp(data.main.tempMin)
        val maxTemp = Utils.formatTemp(data.main.tempMax)
        tv_delta.text = getString(R.string.temp_delta, minTemp, maxTemp)
        tv_wind.text = getString(R.string.temp_wind, Utils.getWindInKmh(data.wind.speed))
        tv_humidity.text = getString(R.string.temp_humidity, data.main.humidity)
    }

    //endregion
}
