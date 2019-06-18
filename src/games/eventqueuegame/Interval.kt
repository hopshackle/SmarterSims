package games.eventqueuegame

import java.lang.AssertionError
import kotlin.random.Random

interface Interval {
    fun sampleFrom(): Number
    fun sampleFrom(rnd: Random): Number
}

fun intervalFrom(from: Number, to: Number): Interval  {
    return when (from) {
        is Int -> IntegerInterval(from, to.toInt())
        is Double -> DoubleInterval(from, to.toDouble())
        else -> throw AssertionError("Type not yet supported for Interval: " + from::class)
    }
}

fun intervalFrom(singleValue: Number): Interval {
    return when (singleValue) {
        is Int -> IntegerInterval(singleValue)
        is Double -> DoubleInterval(singleValue)
        else -> throw AssertionError("Type not yet supported for Interval: " + singleValue::class)
    }
}

private val rnd = Random(1)

class IntegerInterval(val startPoint: Int, val endPoint: Int) : Interval {
    constructor(singlePoint: Int) : this(singlePoint, singlePoint)

    override fun sampleFrom(rnd: Random): Int = rnd.nextInt(startPoint, endPoint + 1)
    override fun sampleFrom(): Int = sampleFrom(rnd)
}

data class DoubleInterval(val startPoint: Double, val endPoint: Double): Interval {
    constructor(singlePoint: Double) : this(singlePoint, singlePoint)

    override fun sampleFrom(rnd: Random): Double = rnd.nextDouble(startPoint, endPoint)
    override fun sampleFrom(): Double = sampleFrom(rnd)
}