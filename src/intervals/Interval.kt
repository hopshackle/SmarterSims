package intervals

import java.lang.AssertionError
import kotlin.random.Random
import kotlin.reflect.KFunction

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

fun interval(intervals: Array<Interval>) {

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
    // [a, b], [c, d] ...
    val funToApply: (String) -> Number = when {
        stringRepresentation.contains("true") || stringRepresentation.contains("false") -> { s -> if (s == "true") 1 else 0 }
        stringRepresentation.contains(".") -> { s -> s.toDouble() }
        else -> String::toInt
    }

    fun convertToInterval(stringRep: List<String>): Interval {
        return when (stringRep.size) {
            1 -> interval(funToApply(stringRep[0].trim()))
            2 -> interval(funToApply(stringRep[0].trim()), funToApply(stringRep[1].trim()))
            else -> {
                val splitByBrackets = stringRepresentation.filterNot(Character::isWhitespace).split("],").map { it.filterNot { c -> c in "[]" } }
                CompositeInterval(splitByBrackets.filter(String::isNotEmpty).map {
                    val intervalE = it.split(",")
                    convertToInterval(intervalE)
                })
            }
        }
    }

    val intervalEnds = stringRepresentation.filterNot { c -> c in "[]" }.split(",")
    return convertToInterval(intervalEnds)
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

data class CompositeInterval(val components: List<Interval>) : Interval {
    override fun sampleFrom(rnd: Random): Double {
        val componentToUse = rnd.nextInt(components.size)
        return components[componentToUse].sampleFrom(rnd).toDouble()
    }

    override fun sampleFrom(): Double = sampleFrom(rnd)
}