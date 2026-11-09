package com.dj.dot.ui

/**
 * Whether dot ([col], [row]) is lit in the [n]x[n] pattern drawn for a song without a cover.
 * The pattern comes from [seed] and mirrors left to right, so every song gets its own steady,
 * balanced mark, the way an identicon does.
 */
fun patternLit(seed: Int, col: Int, row: Int, n: Int): Boolean {
    val half = (n + 1) / 2
    val bit = row * half + minOf(col, n - 1 - col)
    return (scramble(seed) ushr (bit % 32)) and 1 == 1
}

/** Spreads similar seeds, like titles one letter apart, into very different patterns. */
private fun scramble(seed: Int): Int {
    var x = seed * -0x61c88647
    x = x xor (x ushr 15)
    x *= 0x2c1b3c6d
    return x xor (x ushr 12)
}
