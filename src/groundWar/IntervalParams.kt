package groundWar

import agents.*
import agents.MCTS.*
import agents.RHEA.RHCAAgent
import agents.RHEA.SimpleActionEvoAgent
import agents.RHEA.SimpleEvoAgent
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
            fogMemory = paramMap.getOrDefault("fogMemory", intervalList("100 : 100")),
            speed = paramMap.getOrDefault("speed", intervalList("10.0 : 10.0")),
            fortAttackerDivisor = paramMap.getOrDefault("fortAttackerDivisor", listOf(interval("2.0")))[0],
            fortDefenderExpBonus = paramMap.getOrDefault("fortDefenderExpBonus", listOf(interval("0.10")))[0],
            lanchesterCoeff = paramMap.getOrDefault("lanchesterCoeff", intervalList("0.2 : 0.2")),
            lanchesterExp = paramMap.getOrDefault("lanchesterExp", intervalList("0.5 : 0.5")),
            fatigueRate = paramMap.getOrDefault("fatigueRate", intervalList("0.0 : 0.0")),
            OODALoop = paramMap.getOrDefault("OODALoop", intervalList("10 : 10")),
            orderDelay = paramMap.getOrDefault("orderDelay", intervalList("10 : 10")),
            controlLimit = paramMap.getOrDefault("controlLimit", intervalList("0 : 0")),
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
        val fogMemory: List<Interval>,
        val speed: List<Interval>,
        val fortAttackerDivisor: Interval,
        val fortDefenderExpBonus: Interval,
        val lanchesterCoeff: List<Interval>,
        val lanchesterExp: List<Interval>,
        val fatigueRate: List<Interval>,
        val OODALoop: List<Interval>,
        val orderDelay: List<Interval>,
        val controlLimit: List<Interval>,
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
                    fogMemory = fogMemory.map { it.sampleFrom().toInt() }.toIntArray(),
                    speed = speed.map { it.sampleFrom().toDouble() }.toDoubleArray(),
                    fortAttackerDivisor = fortAttackerDivisor.sampleFrom().toDouble(),
                    fortDefenderExpBonus = fortDefenderExpBonus.sampleFrom().toDouble(),
                    lanchesterCoeff = lanchesterCoeff.map { it.sampleFrom().toDouble() }.toDoubleArray(),
                    lanchesterExp = lanchesterExp.map { it.sampleFrom().toDouble() }.toDoubleArray(),
                    fatigueRate = fatigueRate.map { it.sampleFrom().toDouble() }.toDoubleArray(),
                    OODALoop = OODALoop.map { it.sampleFrom().toInt() }.toIntArray(),
                    orderDelay = orderDelay.map { it.sampleFrom().toInt() }.toIntArray(),
                    controlLimit = controlLimit.map { it.sampleFrom().toInt() }.toIntArray(),
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
        val fogMemory: IntArray = intArrayOf(100, 100),
        val fogStrengthAssumption: DoubleArray = doubleArrayOf(1.0, 1.0),
        // force and combat attributes
        val speed: DoubleArray = doubleArrayOf(10.0, 10.0),
        val fortAttackerDivisor: Double = 3.0,
        val fortDefenderExpBonus: Double = 0.5,
        val lanchesterCoeff: DoubleArray = doubleArrayOf(0.05, 0.05),
        val lanchesterExp: DoubleArray = doubleArrayOf(0.5, 0.5),    // should be between 0.0 and 1.0
        val fatigueRate: DoubleArray = doubleArrayOf(0.0, 0.0),
        // agent behaviour
        val OODALoop: IntArray = intArrayOf(10, 10),
        val orderDelay: IntArray = intArrayOf(0, 0),
        val controlLimit: IntArray = intArrayOf(0, 0),
        val minAssaultFactor: DoubleArray = doubleArrayOf(0.1, 0.1)
)

data class ScoreParams(
        val ownCity: Double = 5.0,
        val theirCity: Double = -5.0,
        val ownForce: Double = 1.0,
        val theirForce: Double = 1.0,
        val arcVisibility: Double = 0.0,
        val nodeVisibility: Double = 0.0,
        val fortressValue: Double = 0.0,
        val forceEntropy: Double = 0.0,
        val localAdvantage: Double = 0.0,
        val reserveForce: Double = 0.0
)

data class AgentParams(
        val algorithm: String = "RHEA",
        val timeBudget: Int = 50,
        val evalBudget: Int = 50000,
        val tickBudget: Int = 0,
        val sequenceLength: Int = 40,
        val planningHorizon: Int = 100,
        val algoParams: String = "useShiftBuffer, probMutation:0.25",
        val opponentModel: String = ""
) {

    val params = algoParams.split(",")
    fun getParam(name: String, default: String): String {
        return params.firstOrNull { it.contains(name) }?.let { it.split(":")[1] } ?: default
    }

    fun checkConsistency(allParams: List<String>): Boolean {
        val unknownParams = params.map { it.split(":")[0] }.filterNot {
            it in listOf("timeBudget", "evalBudget", "tickBudget", "sequenceLength", "horizon")
        }.filter {
            !allParams.contains(it)
        }
        if (unknownParams.isEmpty())
            return true
        throw AssertionError("Unknown Algorithm Parameters : " + unknownParams.joinToString())
    }

    fun createSimpleAgent(colour: String): SimplePlayerInterface {
        return when (algorithm) {
            "RHEASimple" -> SimpleEvoAgent(
                    nEvals = evalBudget,
                    resample = getParam("resample", "1").toInt(),
                    timeLimit = timeBudget,
                    tickBudget = tickBudget,
                    sequenceLength = sequenceLength,
                    useMutationTransducer = params.contains("useMutationTransducer"),
                    repeatProb = getParam("repeatProb", "0.00").toDouble(),
                    useShiftBuffer = params.contains("useShiftBuffer"),
                    flipAtLeastOneValue = params.contains("flipAtLeastOneValue"),
                    probMutation = getParam("probMutation", "0.01").toDouble(),
                    discountFactor = getParam("discountFactor", "1.0").toDouble()
            )
            else -> throw AssertionError("Invalid Algorithm " + algorithm)
        }
    }

    fun createAgent(colour: String): SimpleActionPlayerInterface {
        val opponentModel = getOpponentModel()
        return when (algorithm) {
            "Random" -> SimpleActionRandom
            "Heuristic" -> {
                val options = getParam("options", "WITHDRAW|ATTACK").split("|").map(HeuristicOptions::valueOf)
                HeuristicAgent(getParam("attack", "3.0").toDouble(), getParam("defence", "1.2").toDouble(), options)
            }
            "RHEA" -> SimpleActionEvoAgent(
                    underlyingAgent = SimpleEvoAgent(
                            nEvals = evalBudget,
                            timeLimit = timeBudget,
                            sequenceLength = sequenceLength,
                            horizon = planningHorizon,
                            useMutationTransducer = params.contains("useMutationTransducer"),
                            repeatProb = getParam("repeatProb", "0.00").toDouble(),
                            useShiftBuffer = params.contains("useShiftBuffer"),
                            flipAtLeastOneValue = params.contains("flipAtLeastOneValue"),
                            probMutation = getParam("probMutation", "0.01").toDouble(),
                            discountFactor = getParam("discountFactor", "1.0").toDouble(),
                            name = colour + "_RHEA"),
                    opponentModel = opponentModel ?: SimpleActionDoNothing(1000))
            "RHCA" -> RHCAAgent(
                    flipAtLeastOneValue = params.contains("flipAtLeastOneValue"),
                    probMutation = getParam("probMutation", "0.01").toDouble(),
                    sequenceLength = sequenceLength,
                    evalsPerGeneration = getParam("evalsPerGeneration", "10").toInt(),
                    populationSize = getParam("populationSize", "10").toInt(),
                    timeLimit = timeBudget,
                    parentSize = getParam("parentSize", "1").toInt(),
                    useShiftBuffer = params.contains("useShiftBuffer"),
                    horizon = planningHorizon,
                    discountFactor = getParam("discountFactor", "1.0").toDouble(),
                    name = colour + "_RHCA")
            "MCTS" -> MCTSTranspositionTableAgentMaster(
                    MCTSParameters(
                            C = getParam("C", "1.0").toDouble(),
                            maxPlayouts = evalBudget,
                            timeLimit = timeBudget,
                            maxActions = getParam("maxActions", "20").toInt(),
                            actionFilter = getParam("actionFilter", "none"),
                            maxDepth = sequenceLength,
                            horizon = planningHorizon,
                            pruneTree = params.contains("pruneTree"),
                            discountRate = getParam("discountFactor", "1.0").toDouble(),
                            selectionMethod = MCTSSelectionMethod.valueOf(getParam("selectionPolicy", "SIMPLE"))),
                    stateFunction = LandCombatStateFunction,
                    rolloutPolicy = when (getParam("rolloutPolicy", "DoNothing")) {
                        "Heuristic" -> HeuristicAgent(3.0, 1.0, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK))
                        "DoNothing" -> SimpleActionDoNothing(1000)
                        "Random", "" -> SimpleActionRandom
                        else -> throw AssertionError("Unknown rollout policy " + getParam("rolloutPolicy", "None"))
                    },
                    opponentModel = opponentModel,
                    name = colour + "_MCTS")
            else -> throw AssertionError("Unknown agent type: " + algorithm)
        }
    }

    fun getOpponentModel(settingsMap: Map<String, Any> = emptyMap()): SimpleActionPlayerInterface? {
        val oppParams = (opponentModel.split(":") + (opponentModel.split(":").size..2).map{""}).toMutableList()
        // We always oppAttack. oppWithdraw and oppReinforce are then switched off if they have a value 0
        // Otherwise they go at that priority (high numbers before lower ones), with Attack at 10.
        // in the case of a tie,
        if (settingsMap.contains("oppAttack")) {
            oppParams[1] = settingsMap["oppAttack"].toString()
        }
        if (settingsMap.contains("oppDefense")) {
            oppParams[2] = settingsMap["oppDefense"].toString()
        }
        return when (oppParams[0]) {
            "Heuristic" -> {
                val options = (settingsMap + mapOf("baseAttack" to 10))
                        .filter { it.key in listOf("oppWithdraw", "oppReinforce") && it.value != 0 }
                        .map { it.key to it.value as Int }
                        .sortedBy { it.second }
                        .map { (k, v) ->
                            when (k) {
                                "oppWithdraw" -> HeuristicOptions.WITHDRAW
                                "oppReinforce" -> HeuristicOptions.REINFORCE
                                "baseAttack" -> HeuristicOptions.ATTACK
                                else -> throw AssertionError("Unknown key " + k)
                            }
                        }
                HeuristicAgent(oppParams[1].toDouble(), oppParams[2].toDouble(), options)
            }
            "DoNothing" -> SimpleActionDoNothing(1000)
            "Random" -> SimpleActionRandom
            "MCTS" -> null
            else -> SimpleActionDoNothing(1000)
        }
    }

}


fun createAgentParamsFromString(details: List<String>): AgentParams {
    // details needs to lines of the form:
    //      minConnections = 2              - a single parameter setting
    //      OODALoop = 10, [5, 50]          - a single parameter setting for BLUE, and an Interval for RED

    // firstly we create a map from parameter name to value
    val paramMap: Map<String, String> = details.filter { it.trim() != "" }.map {
        val temp = it.split("=")
        temp[0].trim() to temp[1].trim()
    }.toMap()

    if (!paramMap.contains("algoParams"))
        throw AssertionError("Agent description must contain algoParams")

    return AgentParams(
            algorithm = paramMap.getOrDefault("algorithm", "RHEA"),
            timeBudget = paramMap.getOrDefault("timeBudget", "50").toInt(),
            evalBudget = paramMap.getOrDefault("evalBudget", "500").toInt(),
            tickBudget = paramMap.getOrDefault("tickBudget", "0").toInt(),
            sequenceLength = paramMap.getOrDefault("sequenceLength", "40").toInt(),
            planningHorizon = paramMap.getOrDefault("planningHorizon", "100").toInt(),
            algoParams = paramMap.getOrDefault("algoParams", ""),
            opponentModel = paramMap.getOrDefault("opponentModel", "")
    )
}

fun createScoreParamsFromString(details: List<String>): ScoreParams {

    val default = ScoreParams()

    val paramMap: Map<String, Double> = details.map {
        val temp = it.split("=")
        temp[0].trim() to temp[1].trim().toDouble()
    }.toMap()

    return ScoreParams(
            ownCity = paramMap.getOrDefault("ownCity", default.ownCity),
            ownForce = paramMap.getOrDefault("ownForce", default.ownCity),
            theirCity = paramMap.getOrDefault("theirCity", default.theirCity),
            theirForce = paramMap.getOrDefault("theirForce", default.theirForce),
            fortressValue = paramMap.getOrDefault("fortressValue", default.fortressValue),
            forceEntropy = paramMap.getOrDefault("forceEntropy", default.forceEntropy),
            localAdvantage = paramMap.getOrDefault("localAdvantage", default.localAdvantage),
            reserveForce = paramMap.getOrDefault("reserveForce", default.reserveForce)
    )
}


