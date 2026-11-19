package com.example.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.prayer.PrayerTimeCalculator
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun QiblaCompassDialog(
    qiblaBearingDegrees: Double,
    cityName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var azimuthDegrees by remember { mutableFloatStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnet = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (rotationSensor == null && (accel == null || magnet == null)) {
            hasSensor = false
            onDispose {}
        } else {
            val gravity = FloatArray(3)
            val geomagnetic = FloatArray(3)
            val rotMatrix = FloatArray(9)
            val orientation = FloatArray(3)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                        SensorManager.getOrientation(rotMatrix, orientation)
                        val deg = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
                        azimuthDegrees = deg
                    } else {
                        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                            System.arraycopy(event.values, 0, gravity, 0, 3)
                        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                            System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        }
                        if (SensorManager.getRotationMatrix(rotMatrix, null, gravity, geomagnetic)) {
                            SensorManager.getOrientation(rotMatrix, orientation)
                            val deg = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
                            azimuthDegrees = deg
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            if (rotationSensor != null) {
                sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
            } else {
                sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
                sensorManager.registerListener(listener, magnet, SensorManager.SENSOR_DELAY_UI)
            }
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    val targetRotation = (qiblaBearingDegrees.toFloat() - azimuthDegrees + 360f) % 360f
    val animatedRotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = spring(stiffness = 300f),
        label = "qibla_needle"
    )
    val isAligned = abs(((targetRotation + 180f) % 360f) - 180f) < 8f

    val primaryColor = MaterialTheme.colorScheme.primary
    val goldColor = MaterialTheme.colorScheme.secondary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "اتجاه القبلة • $cityName",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Canvas(modifier = Modifier.size(210.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension / 2f - 10f

                    drawCircle(
                        color = surfaceVariant,
                        radius = radius,
                        center = center
                    )
                    drawCircle(
                        color = if (isAligned) goldColor else primaryColor,
                        radius = radius,
                        center = center,
                        style = Stroke(width = if (isAligned) 8f else 4f)
                    )

                    rotate(degrees = animatedRotation, pivot = center) {
                        val needlePath = Path().apply {
                            moveTo(center.x, center.y - radius * 0.78f)
                            lineTo(center.x - 18f, center.y + 12f)
                            lineTo(center.x, center.y - 4f)
                            lineTo(center.x + 18f, center.y + 12f)
                            close()
                        }
                        drawPath(
                            path = needlePath,
                            color = if (isAligned) goldColor else primaryColor
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 10f,
                            center = center
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "زاوية القبلة: ${PrayerTimeCalculator.toArabicDigits(qiblaBearingDegrees.roundToInt())}° من الشمال",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = when {
                        !hasSensor -> "مستشعر البوصلة غير متوفر على هذا الجهاز؛ يمكنك الاسترشاد بالزاوية ${PrayerTimeCalculator.toArabicDigits(qiblaBearingDegrees.roundToInt())}° من الشمال."
                        isAligned -> "أنت الآن باتجاه القبلة الشريفة"
                        else -> "أدر الهاتف أفقيًا حتى يتطابق السهم الذهبي مع الأعلى"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isAligned) goldColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}
