package com.futo.polycentric.core

class AggregateException(
    val childExceptions: List<Throwable>,
    message: String = "Multiple exceptions occurred.",
) : Throwable(message, childExceptions.firstOrNull())