package com.example.thelastone.utils

import com.example.thelastone.data.model.Trip
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

fun findDayIndexByDate(trip: Trip, dateStr: String): Int? =
    trip.days.indexOfFirst { it.date == dateStr }.takeIf { it >= 0 }

fun millisToDateString(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FMT)
