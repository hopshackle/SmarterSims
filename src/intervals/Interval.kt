package intervals

import java.lang.AssertionError
import kotlin.random.Random

interface Interval {
    fun sampleFrom(): Number
    fun sampleFrom(rnd: Random): Number
}

fun interval(from: Number, to: Number): Interval {
    return when (from) {
        is Int -> IntegerInterval(from, to.toInt())
        is Double -> DoubleInterval(from, to.toDouble())
        else -> throw AssertionError("Type not yet supported for Interval: " + from::class)
    }
}

fun interval(singleValue: Number): Interval {
    return when (singleValue) {
        is Int -> IntegerInterval(singleValue)
        is Double -> DoubleInterval(singleValue)
        else -> throw AssertionError("Type not yet supported for Interval: " + singleValue::class)
    }
}

fun intervalList(stringRepresentation: String): List<Interval> {
    // format is one of:
    // a
    // [a, b]
    // a : b
    // a : [b, c]
    // [a, b] : c
    // [a, b] : [c, d]

    return stringRepresentation.split(":").map { s: String -> interval(s) }
}

fun interval(stringRepresentation: String): Interval {
    // format is one of:
    // a
    // [a, b]

    val intervalEnds = stringRepresentation.filterNot { c -> c in "[]" }.split(",")
    val funToApply: (String) -> Number = if (stringRepresentation.contains(".")) String::toDouble else String::toInt
    return when (intervalEnds.size) {
        1 -> interval(funToApply(intervalEnds[0].trim()))
        2 -> interval(funToApply(intervalEnds[0].trim()), funToApply(intervalEnds[1].trim()))
        else -> throw AssertionError("Should only have length 1 or 2, not " + intervalEnds.size)
    }

}

private val rnd = Random(System.currentTimeMillis())

data class IntegerInterval(val startPoint: Int, val endPoint: Int) : Interval {
    constructor(singlePoint: Int) : this(singlePoint, singlePoint)

    override fun sampleFrom(rnd: Random): Int = rnd.nextInt(startPoint, endPoint + 1)
    override fun sampleFrom(): Int = sampleFrom(rnd)
}

data class DoubleInterval(val startPoint: Double, val endPoint: Double) : Interval {
    constructor(singlePoint: Double) : this(singlePoint, singlePoint + 1e-6)

    override fun sampleFrom(rnd: Random): Double = rnd.nextDouble(startPoint, endPoint)
    override fun sampleFrom(): Double = sampleFrom(rnd)
}