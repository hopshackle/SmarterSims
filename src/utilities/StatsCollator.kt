package utilities

object StatsCollator {

    private var statistics: MutableMap<String, Double> = HashMap()
    private var squares: MutableMap<String, Double> = HashMap()
    private var count: MutableMap<String, Int> = HashMap()

    fun clear() {
        statistics = HashMap()
        count = HashMap()
        squares = HashMap()
    }

    fun addStatistics(newStats: Map<String, Number>) {
        newStats.forEach { (k, v) -> addStatistics(k, v.toDouble()) }
    }

    fun addStatistics(key: String, value: Double) {
        val oldV = statistics.getOrDefault(key, 0.00)
        val oldSq = squares.getOrDefault(key, 0.00)
        val oldCount = count.getOrDefault(key, 0)
        statistics[key] = oldV + value
        squares[key] = oldSq + value * value
        count[key] = oldCount + 1
    }
    fun addStatistics(key: String, value: Int) = addStatistics(key, value.toDouble())
    fun addStatistics(key: String, value: Long) = addStatistics(key, value.toDouble())

    fun summaryString(): String {
        return statistics.entries
                .map { (k, v) -> String.format("%-20s = %.4g, SE = %.2g\n", k, v / (count[k]!!),
                        Math.sqrt(((squares[k]!! / count[k]!!) - Math.pow(v / count[k]!!, 2.0)) / (count[k]!! - 1).toDouble())) }
                .sorted()
                .joinToString(separator = "")
    }
}

