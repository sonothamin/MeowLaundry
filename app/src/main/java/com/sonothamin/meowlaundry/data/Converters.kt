package com.sonothamin.meowlaundry.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromClothingType(value: ClothingType): String = value.name

    @TypeConverter
    fun toClothingType(value: String): ClothingType = ClothingType.valueOf(value)

    @TypeConverter
    fun fromClothingStatus(value: ClothingStatus): String = value.name

    @TypeConverter
    fun toClothingStatus(value: String): ClothingStatus = ClothingStatus.valueOf(value)

    @TypeConverter
    fun fromServiceType(value: ServiceType): String = value.name

    @TypeConverter
    fun toServiceType(value: String): ServiceType = ServiceType.valueOf(value)

    @TypeConverter
    fun fromTicketStatus(value: TicketStatus): String = value.name

    @TypeConverter
    fun toTicketStatus(value: String): TicketStatus = TicketStatus.valueOf(value)

    @TypeConverter
    fun fromArchiveReason(value: ArchiveReason?): String? = value?.name

    @TypeConverter
    fun toArchiveReason(value: String?): ArchiveReason? =
        value?.let { runCatching { ArchiveReason.valueOf(it) }.getOrNull() }
}
