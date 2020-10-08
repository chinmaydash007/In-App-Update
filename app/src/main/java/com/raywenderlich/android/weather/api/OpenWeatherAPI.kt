/*
 * Copyright (c) 2020 Razeware LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 * distribute, sublicense, create a derivative work, and/or sell copies of the
 * Software in any work that is designed, intended, or marketed for pedagogical or
 * instructional purposes related to programming, coding, application development,
 * or information technology.  Permission for such use, copying, modification,
 * merger, publication, distribution, sublicensing, creation of derivative works,
 * or sale is expressly withheld.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.raywenderlich.android.weather.api

import android.util.Log
import com.google.gson.GsonBuilder
import com.raywenderlich.android.weather.model.WeatherData
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * In order to retrieve weather information we connect to OpenWeather, available at:
 * https://openweathermap.org/
 *
 * You can create your API key: https://openweathermap.org/appid
 */

private const val TAG = "RestAPI"
private const val API_KEY = "39e97d892de9a36e5a9d30764b4d1759" //Your API key from OpenWeatherMap
private const val URL = "http://api.openweathermap.org/"
private const val CNT = "10"
private const val UNITS = "metric"
class OpenWeatherAPI {

  fun getForecastInformation(lat: Double, lon: Double, listener: IWeatherDataAvailable) {
    val retrofit = Retrofit.Builder()
        .client(OkHttpClient.Builder().build())
        .baseUrl(URL)
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
        .build()

    val weatherAPI = retrofit.create(IWeatherAPI::class.java)
    weatherAPI.getWeatherInfo(lat, lon, CNT, UNITS,
        API_KEY).enqueue(object : Callback<WeatherData?> {

      override fun onResponse(call: Call<WeatherData?>, response: Response<WeatherData?>) {
        Log.d(TAG, "response=${response.message()} | code=${response.code()}")
        if (response.body() == null) {
          listener.onNewWeatherDataUnavailable()
        } else {
          listener.onNewWeatherDataAvailable(response.body()!!)
        }
      }

      override fun onFailure(call: Call<WeatherData?>, t: Throwable) {
        Log.d(TAG, "failure=${t.localizedMessage}")
        listener.onNewWeatherDataUnavailable()
      }
    })
  }
}