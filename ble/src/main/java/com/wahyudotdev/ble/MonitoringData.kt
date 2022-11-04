package com.wahyudotdev.ble

sealed class MonitoringData {
    class HeartRate(val spo2: Int, val systolic: Int, val diastolic: Int, val bpm: Int) :
        MonitoringData()

    class FootSteps(val steps: Int, val meters: Int, val cal: Int) : MonitoringData()
}
