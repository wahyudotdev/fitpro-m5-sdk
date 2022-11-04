package com.wahyudotdev.fitprom5

import android.app.Activity
import android.widget.Toast

fun Activity.tos(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}