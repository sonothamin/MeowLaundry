package com.sonothamin.meowlaundry.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DryCleaning
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.ui.graphics.vector.ImageVector
import com.sonothamin.meowlaundry.data.ServiceType

fun serviceLabel(type: ServiceType): String = when (type) {
    ServiceType.WASH -> "Wash"
    ServiceType.PRESS -> "Press"
    ServiceType.WASH_AND_PRESS -> "Wash & press"
    ServiceType.DRY_CLEAN -> "Dry clean"
}

fun serviceIcon(type: ServiceType): ImageVector = when (type) {
    ServiceType.WASH, ServiceType.WASH_AND_PRESS -> Icons.Default.LocalLaundryService
    ServiceType.PRESS -> Icons.Default.AutoAwesome
    ServiceType.DRY_CLEAN -> Icons.Default.DryCleaning
}
