package com.futo.polycentric.core

fun combineHashCodes(values: List<Int?>): Int =
    values.fold(1) { result, value -> 31 * result + (value ?: 0) }
