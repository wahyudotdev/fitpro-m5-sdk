package com.wahyudotdev.fitprom5

import android.app.Activity
import android.content.Context
import android.widget.Toast

fun Context.tos(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}