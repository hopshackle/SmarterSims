package groundWar

import agents.*
import agents.MCTS.*
import ggi.*
import intervals.*

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
            nAttempts = paramMap.getOrDefault("nAttempts", listOf(interval("20")))[0],
            width = paramMap.getOrDefault("width", listOf(interval("1000")))[0],
            height = paramMap.getOrDefault("height", listOf(interval("600")))[0],
            citySeparation = paramMap.getOrDefault("citySeparation", listOf(interval("30")))[0],
            seed = paramMap.getOrDefault("seed", listOf(interval("1, 1000000")))[0],
            autoConnect = paramMap.getOrDefault("autoConnect", listOf(interval("300")))[0],
            minConnections = paramMap.getOrDefault("minConnections", listOf(interval("2")))[0],
            maxDistance = paramMap.getOrDefault("maxDistance", listOf(interval("1000")))[0],
            percentFort = paramMap.getOrDefault("percentFort", listOf(interval("0.2")))[0],
            fogOfWar = paramMap.getOrDefault("fogOfWar", listOf(interval("1")))[0] == IntegerInterval(1)
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
        val nAttempts: Interval,
        val width: Interval,
        val height: Interval,
        val citySeparation: Interval,
        val seed: Interval,
        val autoConnect: Interval,
        val minConnections: Interval,
        val maxDistance: Interval,
        val percentFort: Interval,
        val fogOfWar: Boolean = true
) {
    fun sampleParams(): EventGameParams =
            EventGameParams(
                    // world set up
                    nAttempts = nAttempts.sampleFrom().toInt(),
                    width = width.sampleFrom().toInt(),
                    height = height.sampleFrom().toInt(),
                    radius = 25,
                    citySeparation = citySeparation.sampleFrom().toInt(),
                    fogOfWar = fogOfWar,
                    seed = seed.sampleFrom().toLong(),
                    autoConnect = autoConnect.sampleFrom().toInt(),
                    minConnections = minConnections.sampleFrom().toInt(),
                    maxDistance = maxDistance.sampleFrom().toInt(),
                    percentFort = percentFort.sampleFrom().toDouble(),
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
        val minAssaultFactor: DoubleArray = doubleArrayOf(0.1, 0.1)
)

data class AgentParams(
        val blueAgent: String = "MCTS",
        val redAgent: String = "RHEA",
        val timeBudget: Int = 50,
        val evalBudget: Int = 500,
        val sequenceLength: Int = 40,
        val planningHorizon: Int = 100,
        val blueParams: String = "pruneTree, C:1.0, maxActions:20",
        val redParams: String = "useShiftBuffer, probMutation:0.25",
        val blueOpponentModel: String = "",
        val redOpponentModel: String = ""
) {
    fun createAgent(colour: String): SimpleActionPlayerInterface {
        val (type, params, opponent) = when (colour.toUpperCase()) {
            "BLUE" -> Triple(blueAgent, blueParams.split(","), blueOpponentModel)
            "RED" -> Triple(redAgent, redParams.split(","), redOpponentModel)
            else -> throw AssertionError("Unknown colour " + colour)
        }
        fun getParam(name: String): String {
            return params.firstOrNull { it.contains(name) }?.let { it.split(":")[1] } ?: "0"
        }

        val oppParams = opponent.split(":")
        val opponentModel = when (oppParams[0]) {
            "Heuristic" -> {
                val options = oppParams.subList(3, oppParams.size).map(HeuristicOptions::valueOf)
                HeuristicAgent(oppParams[1].toDouble(), oppParams[2].toDouble(), options)
            }
            else -> SimpleActionDoNothing
        }
        return when (type) {
            "Heuristic" -> {
                val options = getParam("options").split("|").map(HeuristicOptions::valueOf)
                HeuristicAgent(getParam("attack").toDouble(), getParam("defence").toDouble(), options)
            }
            "RHEA" -> SimpleActionEvoAgent(
                    underlyingAgent = SimpleEvoAgent(nEvals = evalBudget, timeLimit = timeBudget, sequenceLength = sequenceLength, horizon = planningHorizon,
                            useMutationTransducer = params.contains("useMutationTransducer"), useShiftBuffer = params.contains("useShiftBuffer"),
                            flipAtLeastOneValue = params.contains("flipAtLeastOneValue"),
                            probMutation = getParam("probMutation").toDouble(), name = colour + "_RHEA"),
                    opponentModel = opponentModel)
            "MCTS" -> MCTSTranspositionTableAgentMaster(MCTSParameters(C = getParam("C").toDouble(), maxPlayouts = evalBudget, timeLimit = timeBudget,
                    maxDepth = sequenceLength, horizon = planningHorizon, pruneTree = params.contains("pruneTree")),
                    stateFunction = LandCombatStateFunction,
                    rolloutPolicy = when (getParam("rolloutPolicy")) {
                        "Heuristic" -> HeuristicAgent(3.0, 1.0, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK))
                        "DoNothing" -> SimpleActionDoNothing
                        "random", "" -> SimpleActionRandom
                        else -> throw AssertionError("Unknown rollout policy " + getParam("rolloutPolicy"))
                    },
                    opponentModel = opponentModel,
                    name = colour + "_MCTS")
            else -> throw AssertionError("Unknown agent type: " + type)
        }
    }

}


fun createAgentParamsFromString(details: List<String>): AgentParams {
    // details needs to lines of the form:
    //      minConnections = 2              - a single parameter setting
    //      OODALoop = 10, [5, 50]          - a single parameter setting for BLUE, and an Interval for RED

    // firstly we create a map from parameter name to value
    val paramMap: Map<String, String> = details.map {
        val temp = it.split("=")
        temp[0].trim() to temp[1].trim()
    }.toMap()

    return AgentParams(
            blueAgent = paramMap.getOrDefault("blueAgent", "RHEA"),
            redAgent = paramMap.getOrDefault("redAgent", "RHEA"),
            timeBudget = paramMap.getOrDefault("timeBudget", "50").toInt(),
            evalBudget = paramMap.getOrDefault("evalBudget", "500").toInt(),
            sequenceLength = paramMap.getOrDefault("sequenceLength", "40").toInt(),
            planningHorizon = paramMap.getOrDefault("planningHorizon", "100").toInt(),
            blueParams = paramMap.getOrDefault("blueParams", ""),
            redParams = paramMap.getOrDefault("redParams", ""),
            blueOpponentModel = paramMap.getOrDefault("blueOpponentModel", ""),
            redOpponentModel = paramMap.getOrDefault("redOpponentModel", "")
    )
}


