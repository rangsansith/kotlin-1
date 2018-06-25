/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

// Copyright 2009 The Closure Library Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS-IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

package kotlin

class Long(private val low: Int,
           private val high: Int) : Number() {

    operator fun plus(other: Long): Long = add(other)

    fun toNumber() = high * TWO_PWR_32_DBL_ + getLowBitsUnsigned()

    override fun equals(other: Any?)= other is Long && equalsLong(other)

    override fun hashCode() = low xor high

    override fun toString(): String {
        val radix = 10

        if (radix < 2 || 36 < radix) {
            throw Exception("radix out of range: $radix")
        }

        if (isZero()) {
            return "0"
        }

        if (isNegative()) {
            if (equalsLong(MIN_VALUE)) {
                // We need to change the Long value before it can be negated, so we remove
                // the bottom-most digit in this base and then recurse to do the rest.
                val radixLong = fromInt(radix)
                val div = div(radixLong)
                val rem = div.multiply(radixLong).subtract(this).toInt();
                return js("div.toString(radix) + rem.toString(radix)")
            } else {
                return "-${negate().toString()}"
            }
        }

        // Do several (6) digits each time through the loop, so as to
        // minimize the calls to the very expensive emulated div.
        val radixToPower = fromNumber(js("Math.pow(radix, 6)").unsafeCast<Double>())

        var rem = this
        var result = ""
        while (true) {
            val remDiv = rem.div(radixToPower)
            val intval = rem.subtract(remDiv.multiply(radixToPower)).toInt()
            var digits = js("intval.toString(radix)").unsafeCast<String>()

            rem = remDiv
            if (rem.isZero()) {
                return digits + result
            } else {
                while (digits.length < 6) {
                    digits = "0" + digits
                }
                result = digits + result
            }
        }
    }

    fun getHighBits() = high

    fun getLowBits() = low

    fun getLowBitsUnsigned() = if (low >= 0) low.toDouble() else TWO_PWR_32_DBL_ + low

    fun getNumBitsAbs(): Int {
        if (isNegative()) {
            return if (equalsLong(MIN_VALUE)) {
                64
            } else {
                negate().getNumBitsAbs()
            }
        } else {
            val v = if (high != 0) high else low
            var bit = 31
            while (bit > 0) {
                if (v and (1 shl bit) != 0) {
                    break
                }
                bit--
            }
            return if (high != 0) bit + 33 else bit + 1
        }
    }

    fun isZero() = high == 0 && low == 0

    fun isNegative() = high < 0

    fun isOdd() = low and 1 == 1

    fun equalsLong(other: Long) = high == other.high && low == other.low

    fun notEqualsLong(other: Long) = high != other.high || low != other.low

    fun lessThan(other: Long) = compare(other) < 0

    fun lessThanOrEqual(other: Long) = compare(other) <= 0

    fun greaterThan(other: Long) = compare(other) > 0

    fun greaterThanOrEqual(other: Long) = compare(other) >= 0

    fun compare(other: Long): Int {
        if (equalsLong(other)) {
            return 0;
        }

        val thisNeg = isNegative();
        val otherNeg = other.isNegative();

        return when {
            thisNeg && !otherNeg -> -1
            !thisNeg && otherNeg -> 1
        // at this point, the signs are the same, so subtraction will not overflow
            subtract(other).isNegative() -> -1
            else -> 1
        }
    }

    fun negate() = if (equalsLong(MIN_VALUE)) MIN_VALUE else not().add(ONE)

    fun add(other: Long): Long {
        // Divide each number into 4 chunks of 16 bits, and then sum the chunks.

        val a48 = high ushr 16
        val a32 = high and 0xFFFF
        val a16 = high ushr 16
        val a00 = low and 0xFFFF

        val b48 = other.high ushr 16
        val b32 = other.high and 0xFFFF
        val b16 = other.low ushr 16
        val b00 = other.low and 0xFFFF

        var c48 = 0
        var c32 = 0
        var c16 = 0
        var c00 = 0
        c00 += a00 + b00
        c16 += c00 ushr 16
        c00 = c00 and 0xFFFF
        c16 += a16 + b16
        c32 += c16 ushr 16
        c16 = c16 and 0xFFFF
        c32 += a32 + b32
        c48 += c32 ushr 16
        c32 = c32 and 0xFFFF
        c48 += a48 + b48
        c48 = c48 and 0xFFFF
        return Long((c16 shl 16) or c00, (c48 shl 16) or c32)
    }

    fun subtract(other: Long) = add(other.negate())

    fun multiply(other: Long): Long {
        if (isZero()) {
            return ZERO
        } else if (other.isZero()) {
            return ZERO
        }

        if (equalsLong(MIN_VALUE)) {
            return if (other.isOdd()) MIN_VALUE else ZERO
        } else if (other.equalsLong(MIN_VALUE)) {
            return if (isOdd()) MIN_VALUE else ZERO
        }

        if (isNegative()) {
            return if (other.isNegative()) {
                negate().multiply(other.negate())
            } else {
                negate().multiply(other).negate()
            }
        } else if (other.isNegative()) {
            return multiply(other.negate()).negate()
        }

        // If both longs are small, use float multiplication
        if (lessThan(TWO_PWR_24_) && other.lessThan(TWO_PWR_24_)) {
            return fromNumber(toNumber() * other.toNumber())
        }

        // Divide each long into 4 chunks of 16 bits, and then add up 4x4 products.
        // We can skip products that would overflow.

        val a48 = high ushr 16
        val a32 = high and 0xFFFF
        val a16 = low ushr 16
        val a00 = low and 0xFFFF

        val b48 = other.high ushr 16
        val b32 = other.high and 0xFFFF
        val b16 = other.low ushr 16
        val b00 = other.low and 0xFFFF

        var c48 = 0
        var c32 = 0
        var c16 = 0
        var c00 = 0
        c00 += a00 * b00
        c16 += c00 ushr 16
        c00 = c00 and 0xFFFF
        c16 += a16 * b00
        c32 += c16 ushr 16
        c16 = c16 and 0xFFFF
        c16 += a00 * b16
        c32 += c16 ushr 16
        c16 = c16 and 0xFFFF
        c32 += a32 * b00
        c48 += c32 ushr 16
        c32 = c32 and 0xFFFF
        c32 += a16 * b16
        c48 += c32 ushr 16
        c32 = c32 and 0xFFFF
        c32 += a00 * b32
        c48 += c32 ushr 16
        c32 = c32 and 0xFFFF
        c48 += a48 * b00 + a32 * b16 + a16 * b32 + a00 * b48
        c48 = c48 and 0xFFFF
        return Long(c16 shl 16 or c00, c48 shl 16 or c32)
    }

    fun div(other: Long): Long {
        if (other.isZero()) {
            throw Exception("division by zero")
        } else if (isZero()) {
            return ZERO
        }

        if (equalsLong(MIN_VALUE)) {
            if (other.equalsLong(ONE) || other.equalsLong(NEG_ONE)) {
                return MIN_VALUE  // recall that -MIN_VALUE == MIN_VALUE
            } else if (other.equalsLong(MIN_VALUE)) {
                return ONE
            } else {
                // At this point, we have |other| >= 2, so |this/other| < |MIN_VALUE|.
                val halfThis = shiftRight(1)
                val approx = halfThis.div(other).shiftLeft(1)
                if (approx.equalsLong(ZERO)) {
                    return if (other.isNegative()) ONE else NEG_ONE
                } else {
                    val rem = subtract(other.multiply(approx))
                    return approx.add(rem.div(other))
                }
            }
        } else if (other.equalsLong(MIN_VALUE)) {
            return ZERO
        }

        if (isNegative()) {
            return if (other.isNegative()) {
                negate().div(other.negate())
            } else {
                negate().div(other).negate()
            }
        } else if (other.isNegative()) {
            return div(other.negate()).negate()
        }

        // Repeat the following until the remainder is less than other:  find a
        // floating-point that approximates remainder / other *from below*, add this
        // into the result, and subtract it from the remainder.  It is critical that
        // the approximate value is less than or equal to the real value so that the
        // remainder never becomes negative.
        var res = ZERO
        var rem = this
        while (rem.greaterThanOrEqual(other)) {
            // Approximate the result of division. This may be a little greater or
            // smaller than the actual value.
            val approxDouble = rem.toNumber() / other.toNumber()
            var approx = js("Math.max(1, Math.floor(approxDouble))").unsafeCast<Double>()

            // We will tweak the approximate result by changing it in the 48-th digit or
            // the smallest non-fractional digit, whichever is larger.
            val log2 = js("Math.ceil(Math.log(approx) / Math.LN2)").unsafeCast<Int>()
            val delta = if (log2 <= 48) 1.0 else js("Math.pow(2, log2 - 48)").unsafeCast<Double>()

            // Decrease the approximation until it is smaller than the remainder.  Note
            // that if it is too large, the product overflows and is negative.
            var approxRes = fromNumber(approx)
            var approxRem = approxRes.multiply(other)
            while (approxRem.isNegative() || approxRem.greaterThan(rem)) {
                approx -= delta
                approxRes = fromNumber(approx)
                approxRem = approxRes.multiply(other)
            }

            // We know the answer can't be zero... and actually, zero would cause
            // infinite recursion since we would make no progress.
            if (approxRes.isZero()) {
                approxRes = ONE
            }

            res = res.add(approxRes)
            rem = rem.subtract(approxRem)
        }
        return res
    }

    fun modulo(other: Long) = subtract(div(other).multiply(other))

    fun not() = Long(low.inv(), high.inv())

    fun and(other: Long) = Long(low and other.low, high and other.high)

    fun or(other: Long) = Long(low or other.low, high or other.high)

    fun xor(other: Long) = Long(low xor other.low, high xor other.high)

    fun shiftLeft(numBits: Int): Long {
        val numBits = numBits and 63
        if (numBits == 0) {
            return this
        } else {
            if (numBits < 32) {
                return Long(low shl numBits, (high shl numBits) or (low ushr (32 - numBits)))
            } else {
                return Long(0, low shl (numBits - 32))
            }
        }
    }

    fun shiftRight(numBits: Int): Long {
        val numBits = numBits and 63
        if (numBits == 0) {
            return this
        } else {
            if (numBits < 32) {
                return Long((low ushr numBits) or (high shl (32 - numBits)), high shr numBits)
            } else {
                return Long(high shr (numBits - 32), if (high >= 0) 0 else -1)
            }
        }
    }

    fun shiftRightUnsigned(numBits: Int): Long {
        val numBits = numBits and 63
        if (numBits == 0) {
            return this
        } else {
            if (numBits < 32) {
                return Long((low ushr numBits) or (high shl (32 - numBits)), high ushr numBits)
            } else return if (numBits == 32) {
                Long(high, 0)
            } else {
                Long(high ushr (numBits - 32), 0)
            }
        }
    }

    fun inc() = add(ONE)

    fun dec() = add(NEG_ONE)

    fun valueOf() = toNumber()

    fun unaryPlus() = this

    fun unaryMinus() = negate()

    fun inv() = not()

    // TODO
//    fun rangeTo(other: Long): Nothing = TODO()

    override fun toByte() = low.toByte()

    override fun toChar() = low.toChar()

    override fun toDouble() = toNumber()

    override fun toFloat() = toDouble().toFloat()

    override fun toInt(): Int = low

    override fun toLong(): Long = this

    override fun toShort() = low.toShort()

    companion object {
//        private val intCache: dynamic = js("{}")

        /**
         * Returns a Long representing the given (32-bit) integer value.
         * @param {number} value The 32-bit integer in question.
         * @return {!Kotlin.Long} The corresponding Long value.
         */
        fun fromInt(value: Int): Long {
//            if (-128 <= value && value < 128) {
//                val cachedObj = js("intCache[value]").unsafeCast<Long?>()
//                if (cachedObj != null) {
//                    return cachedObj;
//                }
//            }

            val obj = Long(value, if (value < 0) -1 else 0);
//            if (-128 <= value && value < 128) {
//                js("intCache[value] = obj")
//            }
            return obj;
        }

        /**
         * Returns a Long representing the given value, provided that it is a finite
         * number.  Otherwise, zero is returned.
         * @param {number} value The number in question.
         * @return {!Kotlin.Long} The corresponding Long value.
         */
        fun fromNumber(value: Double): Long {
            if (js("isNaN(value)").unsafeCast<Boolean>() || !js("isFinite(value)").unsafeCast<Boolean>()) {
                return ZERO;
            } else if (value <= -TWO_PWR_63_DBL_) {
                return MIN_VALUE;
            } else if (value + 1 >= TWO_PWR_63_DBL_) {
                return MAX_VALUE;
            } else if (value < 0) {
                return fromNumber(-value).negate();
            } else {
                val twoPwr32 = Long.TWO_PWR_32_DBL_
                return Long(
                    js("(value % twoPwr32) | 0").unsafeCast<Int>(),
                    js("(value / twoPwr32) | 0").unsafeCast<Int>())
            }
        }

// TODO
//        /**
//         * Returns a Long representation of the given string, written using the given
//         * radix.
//         * @param {string} str The textual representation of the Long.
//         * @param {number=} opt_radix The radix in which the text is written.
//         * @return {!Kotlin.Long} The corresponding Long value.
//         */
//        fun fromString(str: String, radix: Int = 10): Long {
//            if (str.length == 0) {
//                throw Exception("number format error: empty string")
//            }
//
//            if (radix < 2 || 36 < radix) {
//                throw Exception("radix out of range: $radix");
//            }
//
//            if (str[0] == '-') {
//                return fromString(js("str.substring(1)").unsafeCast<String>(), radix).negate();
//            } else if (js("str.indexOf('-')").unsafeCast<Int>() >= 0) {
//                throw Exception("number format error: interior \"-\" character: $str")
//            }
//
//            // Do several (8) digits each time through the loop, so as to
//            // minimize the calls to the very expensive emulated div.
//            val radixToPower = fromNumber(js("Math.pow(radix, 8)").unsafeCast<Double>())
//
//            var result = ZERO
//
//            var i = 0
//            while (i < str.length) {
//                val size = js("Math.min(8, str.length - i)").unsafeCast<Int>()
//                val value = js("parseInt(str.substring(i, i + size), radix)").unsafeCast<Double>()
//
//                if (size < 8) {
//                    val power = fromNumber(js("Math.pow(radix, size)").unsafeCast<Double>())
//                    result = result.multiply(power).add(fromNumber(value))
//                } else {
//                    result = result.multiply(radixToPower)
//                    result = result.add(fromNumber(value))
//                }
//
//                i += 8
//            }
//
//            return result
//        }

        private val TWO_PWR_16_DBL_ = (1 shl 16).toDouble()

        private val TWO_PWR_24_DBL_ = (1 shl 24).toDouble()

        private val TWO_PWR_32_DBL_ = TWO_PWR_16_DBL_ * TWO_PWR_16_DBL_

        private val TWO_PWR_31_DBL_ = TWO_PWR_32_DBL_ / 2

        private val TWO_PWR_48_DBL_ = TWO_PWR_32_DBL_ * TWO_PWR_16_DBL_

        private val TWO_PWR_64_DBL_ = TWO_PWR_32_DBL_ * TWO_PWR_32_DBL_

        private val TWO_PWR_63_DBL_ = TWO_PWR_64_DBL_ / 2

        private val ZERO = fromInt(0)

        private val ONE = fromInt(1)

        private val NEG_ONE = fromInt(-1)

        private val MAX_VALUE = Long(-1, -1 ushr 1)

        private val MIN_VALUE = Long(0, 1 shl 31)

        private val TWO_PWR_24_ = fromInt(1 shl 24)
    }
}