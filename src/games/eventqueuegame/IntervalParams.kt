package games.eventqueuegame

fun createIntervalParamsFromString(details: List<String>): IntervalParams {
    // details needs to lines of the form:
    //      minConnections = 2              - a single parameter setting
    //      OODALoop = 10, [5, 50]          - a single parameter setting for BLUE, and an Interval for RED

    // firstly we create a map from parameter name to value
    val paramMap: Map<String, List<Interval>> = details.map {
        val temp = it.split("=")
        temp[0].trim() to intervalList(temp[1].trim())
    }.toMap()

    return IntervalParams(
            startingForce = paramMap.getOrDefault("startingForce", intervalList("100 : 100")),
            fogStrengthAssumption = paramMap.getOrDefault("fogStrengthAssumption", intervalList("0 : 0")),
            speed = paramMap.getOrDefault("speed", intervalList("10.0 : 10.0")),
            fortAttackerDivisor = paramMap.getOrDefault("fortAttackerDivisor", listOf(interval("2.0")))[0],
            fortDefenderExpBonus = paramMap.getOrDefault("fortDefenderExpBonus", listOf(interval("0.10")))[0],
            lanchesterCoeff = paramMap.getOrDefault("lanchesterCoeff", intervalList("0.2 : 0.2")),
            lanchesterExp = paramMap.getOrDefault("lanchesterExp", intervalList("0.5 : 0.5")),
            OODALoop = paramMap.getOrDefault("OODALoop", intervalList("10 : 10")),
            minAssaultFactor = paramMap.getOrDefault("minAssaultFactor", intervalList("1.0 : 1.0")),
            planningHorizon = paramMap.getOrDefault("planningHorizon", intervalList("100 : 100"))
    )
}

data class IntervalParams(
        val startingForce: List<Interval>,
        val fogStrengthAssumption: List<Interval>,
        val speed: List<Interval>,
        val fortAttackerDivisor: Interval,
        val fortDefenderExpBonus: Interval,
        val lanchesterCoeff: List<Interval>,
        val lanchesterExp: List<Interval>,
        val OODALoop: List<Interval>,
        val minAssaultFactor: List<Interval>,
        val planningHorizon: List<Interval>
) {
    fun sampleParams(
            nAttempts: Int = 10,
            width: Int = 1000,
            height: Int = 600,
            radius: Int = 25,
            citySeparation: Int = 30,
            seed: Long = 10,
            autoConnect: Int = 300,
            minConnections: Int = 2,
            maxDistance: Int = 1000,
            percentFort: Double = 0.25,
            fogOfWar: Boolean = false,
            maxActionsPerState: Int = 7
    ): EventGameParams =
            EventGameParams(
                    // world set up
                    nAttempts = 10, width = 1000, height = 600, radius = 25, citySeparation = 30, fogOfWar = true,
                    seed = 10, autoConnect = 300, minConnections = 2, maxDistance = 1000, percentFort = 0.25,
                    maxActionsPerState = 7,
                    startingForce = startingForce.map { it.sampleFrom().toInt() }.toIntArray(),
                    fogStrengthAssumption = fogStrengthAssumption.map { it.sampleFrom().toDouble() }.toDoubleArray(),
                    speed = speed.map { it.sampleFrom().toDouble() }.toDoubleArray(),
                    fortAttackerDivisor = fortAttackerDivisor.sampleFrom().toDouble(),
                    fortDefenderExpBonus = fortDefenderExpBonus.sampleFrom().toDouble(),
                    lanchesterCoeff = lanchesterCoeff.map { it.sampleFrom().toDouble() }.toDoubleArray(),
                    lanchesterExp = lanchesterExp.map { it.sampleFrom().toDouble() }.toDoubleArray(),
                    OODALoop = OODALoop.map { it.sampleFrom().toInt() }.toIntArray(),
                    minAssaultFactor = minAssaultFactor.map { it.sampleFrom().toDouble() }.toDoubleArray()
            )
}

data class EventGameParams(
        // world set up
        val nAttempts: Int = 10,
        val width: Int = 1000,
        val height: Int = 600,
        val radius: Int = 25,
        val startingForce: IntArray = intArrayOf(100, 100),
        val citySeparation: Int = 30,
        val seed: Long = 10,
        val autoConnect: Int = 300,
        val minConnections: Int = 2,
        val maxDistance: Int = 1000,
        val percentFort: Double = 0.25,
        val fogOfWar: Boolean = false,
        val fogStrengthAssumption: DoubleArray = doubleArrayOf(1.0, 1.0),
        // force and combat attributes
        val speed: DoubleArray = doubleArrayOf(10.0, 10.0),
        val fortAttackerDivisor: Double = 3.0,
        val fortDefenderExpBonus: Double = 0.5,
        val lanchesterCoeff: DoubleArray = doubleArrayOf(0.05, 0.05),
        val lanchesterExp: DoubleArray = doubleArrayOf(1.0, 1.0),    // should be between 0.0 and 1.0
        // agent behaviour
        val OODALoop: IntArray = intArrayOf(10, 10),
        val minAssaultFactor: DoubleArray = doubleArrayOf(0.1, 0.1),
        val planningHorizon: IntArray = intArrayOf(100, 100),
        val maxActionsPerState: Int = 7
)


