package com.dj.dot.ui

/** "1 song", "2 songs": a count with the word that fits it. */
fun countOf(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"
