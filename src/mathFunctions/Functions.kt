package mathFunctions

import kotlin.math.*

interface NTBEAFunction {
    fun functionValue(x: DoubleArray): Double

    val dimension: Int
}

public val Hartmann3 = Hartmann(
        a = arrayOf(
                doubleArrayOf(3.0, 10.0, 30.0),
                doubleArrayOf(0.1, 10.0, 35.0),
                doubleArrayOf(3.0, 10.0, 30.0),
                doubleArrayOf(0.1, 10.0, 35.0)),
        c = doubleArrayOf(1.0, 1.2, 3.0, 3.2),
        p = arrayOf(
                doubleArrayOf(0.3689, 0.1170, 0.2673),
                doubleArrayOf(0.4699, 0.4387, 0.7470),
                doubleArrayOf(0.1091, 0.8732, 0.5547),
                doubleArrayOf(0.03815, 0.5743, 0.8828)
        )
)

public val Hartmann6 = Hartmann(
        a = arrayOf(
                doubleArrayOf(10.0, 3.0, 17.0, 3.5, 1.7, 8.0),
                doubleArrayOf(0.05, 10.0, 17.0, 0.1, 8.0, 14.0),
                doubleArrayOf(3.0, 3.5, 1.7, 10.0, 17.0, 8.0),
                doubleArrayOf(17.0, 8.0, 0.05, 10.0, 0.1, 14.0)
        ),
        c = doubleArrayOf(1.0, 1.2, 3.0, 3.2),
        p = arrayOf(
                doubleArrayOf(0.1312, 0.1696, 0.5569, 0.0124, 0.8283, 0.5886),
                doubleArrayOf(0.2329, 0.4135, 0.8307, 0.3736, 0.1004, 0.9991),
                doubleArrayOf(0.2548, 0.1451, 0.3522, 0.2883, 0.3047, 0.665),
                doubleArrayOf(0.4047, 0.8828, 0.8732, 0.5743, 0.1091, 0.0381)
        )
)

class Hartmann(val a: Array<DoubleArray>,
               val c: DoubleArray,
               val p: Array<DoubleArray>) : NTBEAFunction {

    override val dimension: Int = a[0].size

    override fun functionValue(x: DoubleArray): Double {
        return (a.indices).map { i ->
            c[i] * exp(-(a[0].indices).map { j -> a[i][j] * (x[j] - p[i][j]).pow(2) }.sum())
        }.sum() / 4.0
    }
}

object Branin : NTBEAFunction {
    override val dimension: Int = 2

    override fun functionValue(x: DoubleArray): Double {
        val x1 = x[0] * 15.0 - 5.0
        val x2 = x[1] * 15.0
        val a = 1.0
        val b = 5.1 / (4 * PI * PI)
        val c = 5.0 / PI
        val d = 6.0
        val e = 10.0
        val f = 1.0 / 8.0 / PI
        return (-(a * (x2 - b * x1 * x1 + c * x1 - d).pow(2) + e * (1.0 - f) * cos(x1) + e) + 10.0) / 12.0
    }
}

object GoldsteinPrice : NTBEAFunction {
    override val dimension: Int = 2

    override fun functionValue(x: DoubleArray): Double {
        val x1 = x[0] * 4.0 - 2.0
        val x2 = x[1] * 4.0 - 2.0
        return (400.0 -(1.0 + (x1 + x2 + 1).pow(2) * (19.0 - 14 * x1 + 3 * x1.pow(2) - 14 * x2 + 6 * x1 * x2 + 3 * x2.pow(2))) *
                (30.0 + (2 * x1 - 3 * x2).pow(2) * (18.0 - 32 * x1 + 12 * x1.pow(2) + 48 * x2 - 36 * x1 * x2 + 27 * x2.pow(2)))) / 500.0
    }
}
