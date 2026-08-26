package com.nickspeelman.localjournal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun CustomTimePicker(
    initialHour: Int,
    initialMinute: Int,
    is24Hour: Boolean,
    onTimeChange: (Int, Int) -> Unit
) {
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }
    var isPickingHour by remember { mutableStateOf(true) }
    var isAm by remember { mutableStateOf(initialHour < 12) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            TimeValueBox(
                value = if (is24Hour) hour else {
                    when {
                        hour == 0 -> 12
                        hour > 12 -> hour - 12
                        else -> hour
                    }
                },
                isSelected = isPickingHour,
                onClick = { isPickingHour = true }
            )
            Text(
                text = ":",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            TimeValueBox(
                value = minute,
                isSelected = !isPickingHour,
                onClick = { isPickingHour = false }
            )

            if (!is24Hour) {
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    AmPmToggle(isAm = isAm, onToggle = {
                        isAm = it
                        val newHour = if (it) {
                            if (hour >= 12) hour - 12 else hour
                        } else {
                            if (hour < 12) hour + 12 else hour
                        }
                        hour = newHour
                        onTimeChange(hour, minute)
                    })
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(280.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isPickingHour) {
                HourDial(
                    selectedHour = hour,
                    is24Hour = is24Hour,
                    onHourSelected = { pickedHour ->
                        hour = if (is24Hour) {
                            pickedHour
                        } else {
                            if (isAm) {
                                if (pickedHour == 0) 0 else pickedHour
                            } else {
                                if (pickedHour == 0) 12 else pickedHour + 12
                            }
                        }
                        onTimeChange(hour, minute)
                        isPickingHour = false
                    }
                )
            } else {
                MinuteDial(
                    selectedMinute = minute,
                    onMinuteSelected = {
                        minute = it
                        onTimeChange(hour, minute)
                    }
                )
            }
        }
    }
}

@Composable
fun TimeValueBox(value: Int, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .width(96.dp)
            .height(80.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = value.toString().padStart(2, '0'),
                style = MaterialTheme.typography.displayMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AmPmToggle(isAm: Boolean, onToggle: (Boolean) -> Unit) {
    Column {
        AmPmButton(label = "AM", isSelected = isAm, onClick = { onToggle(true) })
        Spacer(modifier = Modifier.height(8.dp))
        AmPmButton(label = "PM", isSelected = !isAm, onClick = { onToggle(false) })
    }
}

@Composable
fun AmPmButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        modifier = Modifier
            .width(52.dp)
            .height(36.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun HourDial(selectedHour: Int, is24Hour: Boolean, onHourSelected: (Int) -> Unit) {
    val totalHours = if (is24Hour) 24 else 12
    // Internally use 0-indexed for the top position
    val currentDisplayHour = if (is24Hour) selectedHour % 24 else selectedHour % 12

    Dial(
        selectedValue = currentDisplayHour,
        maxValue = totalHours,
        onValueSelected = { value ->
            onHourSelected(value)
        },
        labelProvider = { 
            if (is24Hour) it.toString() 
            else if (it == 0) "12" 
            else it.toString() 
        }
    )
}

@Composable
fun MinuteDial(selectedMinute: Int, onMinuteSelected: (Int) -> Unit) {
    Dial(
        selectedValue = selectedMinute,
        maxValue = 60,
        onValueSelected = { onMinuteSelected(it % 60) },
        labelProvider = { if (it % 5 == 0) it.toString() else "" }
    )
}

@Composable
fun Dial(
    selectedValue: Int,
    maxValue: Int,
    onValueSelected: (Int) -> Unit,
    labelProvider: (Int) -> String
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current.density
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val center = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)
        val radius = constraints.maxWidth * 0.4f
        
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onValueSelected(calculateValueFromOffset(offset, center, maxValue))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onValueSelected(calculateValueFromOffset(change.position, center, maxValue))
                }
            }
        ) {
            val angle = (selectedValue.toFloat() / maxValue) * 2 * PI - PI / 2
            val lineEnd = Offset(
                center.x + cos(angle).toFloat() * radius,
                center.y + sin(angle).toFloat() * radius
            )
            
            drawLine(
                color = primaryColor,
                start = center,
                end = lineEnd,
                strokeWidth = 2.dp.toPx()
            )
            drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = center)
            drawCircle(color = primaryColor, radius = 20.dp.toPx(), center = lineEnd)
        }

        for (i in 0 until maxValue) {
            val label = labelProvider(i)
            if (label.isNotEmpty()) {
                val angle = (i.toFloat() / maxValue) * 2 * PI - PI / 2
                val x = center.x + cos(angle).toFloat() * radius
                val y = center.y + sin(angle).toFloat() * radius
                
                Box(
                    modifier = Modifier.offset(
                        x = (x / density).dp - 15.dp,
                        y = (y / density).dp - 15.dp
                    ).size(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val isPicked = i == selectedValue
                    Text(
                        text = label,
                        color = if (isPicked) MaterialTheme.colorScheme.onPrimary else onSurfaceColor,
                        fontSize = if (maxValue > 12) 11.sp else 16.sp,
                        fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

private fun calculateValueFromOffset(offset: Offset, center: Offset, maxValue: Int): Int {
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    var angle = atan2(dy, dx) + PI / 2
    if (angle < 0) angle += 2 * PI
    
    val value = ((angle / (2 * PI)) * maxValue).roundToInt()
    return if (value >= maxValue) 0 else value
}
