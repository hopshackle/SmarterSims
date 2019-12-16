package groundWar

import agents.MCTS.MCTSParameters
import agents.MCTS.MCTSTranspositionTableAgentMaster
import agents.RHEA.RHCAAgent
import agents.RHEA.SimpleActionEvoAgent
import agents.RHEA.SimpleEvoAgent
import agents.SimpleActionDoNothing
import evodef.SearchSpace
import ggi.SimpleActionPlayerInterface
import ggi.SimplePlayerInterface
import java.io.FileReader
import java.lang.AssertionError
import kotlin.reflect.KClass

abstract class HopshackleSearchSpace<T>(fileName: String) : SearchSpace {

    val searchDimensions: List<String> = if (fileName != "") FileReader(fileName).readLines() else emptyList()
    val searchKeys: List<String> = searchDimensions.map { it.split("=").first() }
    val searchTypes: List<KClass<*>> = searchKeys.map {
        types[it] ?: throw AssertionError("Unknown search variable $it")
    }
    val searchValues: List<List<Any>> = searchDimensions.zip(searchTypes)
            .map { (allV, cl) ->
                allV.split("=")[1]      // get the stuff after the colon, which should be values to be searched
                        .split(",")
                        .map(String::trim).map {
                            when (cl) {
                                Int::class -> it.toInt()
                                Double::class -> it.toDouble()
                                Boolean::class -> it.toBoolean()
                                else -> throw AssertionError("Currently unsupported class $cl")
                            }
                        }
            }
    abstract val types: Map<String, KClass<*>>
    fun convertSettings(settings: IntArray): DoubleArray {
        return settings.zip(searchValues).map { (i, values) ->
            val v = values[i]
            when (v) {
                is Int -> v.toDouble()
                is Double -> values[i]
                is Boolean -> if (v) 1.0 else 0.0
                else -> settings[i].toDouble()      // if not numeric, default to the category
            } as Double
        }.toDoubleArray()
    }

    fun settingsToMap(settings: DoubleArray): Map<String, Any> {
        return settings.withIndex().map { (i, v) ->
            searchKeys[i] to when (searchTypes[i]) {
                Int::class -> (v + 0.5).toInt()
                Double::class -> v
                Boolean::class -> v > 0.5
                else -> throw AssertionError("Unsupported class ${searchTypes[i]}")
            }
        }.toMap()
    }

    abstract fun getAgent(settings: DoubleArray): T
    override fun nValues(i: Int) = searchValues[i].size
    override fun nDims() = searchValues.size
    override fun name(i: Int) = searchKeys[i]
    override fun value(d: Int, i: Int) = searchValues[d][i]
}

class HeuristicSearchSpace(val defaultParams: AgentParams, fileName: String) : HopshackleSearchSpace<SimpleActionPlayerInterface>(fileName) {

    override val types: Map<String, KClass<*>>
        get() = mapOf(
                "oppWithdraw" to Int::class, "oppReinforce" to Int::class, "oppRedeploy" to Int::class,
                "oppAttack" to Double::class, "oppDefense" to Double::class)
//    init {defaultParams.checkConsistency(types.keys.toList())}

    override fun getAgent(settings: DoubleArray): SimpleActionPlayerInterface {
        val settingsMap = settingsToMap(settings)
        return defaultParams.copy(opponentModel = "Heuristic").getOpponentModel(settingsMap) ?: SimpleActionDoNothing(1000)
    }
}

class RHEASearchSpace(val defaultParams: AgentParams, fileName: String) : HopshackleSearchSpace<SimpleActionPlayerInterface>(fileName) {

    override val types: Map<String, KClass<*>>
        get() = mapOf("useShiftBuffer" to Boolean::class, "probMutation" to Double::class,
                "flipAtLeastOneValue" to Boolean::class, "discountFactor" to Double::class,
                "sequenceLength" to Int::class,
                "horizon" to Int::class, "timeBudget" to Int::class,
                "oppWithdraw" to Int::class, "oppReinforce" to Int::class, "oppAttack" to Double::class, "oppDefense" to Double::class)
    init {defaultParams.checkConsistency(types.keys.toList())}
    override fun getAgent(settings: DoubleArray): SimpleActionPlayerInterface {
        val settingsMap = settingsToMap(settings)
        return SimpleActionEvoAgent(
                underlyingAgent = SimpleEvoAgent(
                        nEvals = settingsMap.getOrDefault("evalBudget", defaultParams.evalBudget) as Int,
                        timeLimit = settingsMap.getOrDefault("timeBudget", defaultParams.timeBudget) as Int,
                        useMutationTransducer = false,
                        sequenceLength = settingsMap.getOrDefault("sequenceLength", defaultParams.sequenceLength) as Int,
                        horizon = settingsMap.getOrDefault("horizon", defaultParams.planningHorizon) as Int,
                        useShiftBuffer = settingsMap.getOrDefault("useShiftBuffer", defaultParams.params.contains("useShiftBuffer")) as Boolean,
                        probMutation = settingsMap.getOrDefault("probMutation", defaultParams.getParam("probMutation", "0.1").toDouble()) as Double,
                        flipAtLeastOneValue = settingsMap.getOrDefault("flipAtLeastOneValue", defaultParams.params.contains("flipAtLeastOneValue")) as Boolean,
                        discountFactor = settingsMap.getOrDefault("discountFactor", defaultParams.getParam("discountFactor", "1.0").toDouble()) as Double
                ),
                opponentModel = defaultParams.getOpponentModel(settingsMap) ?: SimpleActionDoNothing(1000)
        )
    }
}

class RHEASimpleSearchSpace(val defaultParams: AgentParams, fileName: String) : HopshackleSearchSpace<SimplePlayerInterface>(fileName) {

    override val types: Map<String, KClass<*>>
        get() = mapOf("useShiftBuffer" to Boolean::class, "mutatedPoints" to Double::class,
                "flipAtLeastOneValue" to Boolean::class, "discountFactor" to Double::class,
                "sequenceLength" to Int::class, "useMutationTransducer" to Boolean::class,
                "horizon" to Int::class, "timeBudget" to Int::class,
                "resample" to Int::class, "repeatProb" to Double::class)
    init {
        defaultParams.checkConsistency(types.keys.toList())
    }
    override fun getAgent(settings: DoubleArray): SimplePlayerInterface {
        val settingsMap = settingsToMap(settings)
        val mutatedPoints = settingsMap.getOrDefault("mutatedPoints", defaultParams.getParam("mutatedPoints", "1.0").toDouble()) as Double
        val sequenceLength = settingsMap.getOrDefault("sequenceLength", defaultParams.sequenceLength) as Int
        val probMutation = mutatedPoints / sequenceLength
        return SimpleEvoAgent(
                nEvals = settingsMap.getOrDefault("evalBudget", defaultParams.evalBudget) as Int,
                resample = settingsMap.getOrDefault("resample", defaultParams.getParam("resample", "1").toInt()) as Int,
                timeLimit = settingsMap.getOrDefault("timeBudget", defaultParams.timeBudget) as Int,
                tickBudget = settingsMap.getOrDefault("tickBudget", defaultParams.tickBudget) as Int,
                useMutationTransducer = settingsMap.getOrDefault("mutationTransducer", defaultParams.params.contains("mutationTransducer")) as Boolean,
                repeatProb = settingsMap.getOrDefault("repeatProb", defaultParams.getParam("repeatProb", "0.0").toDouble()) as Double,
                sequenceLength = sequenceLength,
                horizon = settingsMap.getOrDefault("horizon", defaultParams.planningHorizon) as Int,
                useShiftBuffer = settingsMap.getOrDefault("useShiftBuffer", defaultParams.params.contains("useShiftBuffer")) as Boolean,
                probMutation = probMutation,
                flipAtLeastOneValue = settingsMap.getOrDefault("flipAtLeastOneValue", defaultParams.params.contains("flipAtLeastOneValue")) as Boolean,
                discountFactor = settingsMap.getOrDefault("discountFactor", defaultParams.getParam("discountFactor", "1.0").toDouble()) as Double
        )
    }
}

class RHCASearchSpace(val defaultParams: AgentParams, fileName: String) : HopshackleSearchSpace<SimpleActionPlayerInterface>(fileName) {

    override val types: Map<String, KClass<*>>
        get() = mapOf("useShiftBuffer" to Boolean::class, "probMutation" to Double::class,
                "flipAtLeastOneValue" to Boolean::class, "discountFactor" to Double::class,
                "sequenceLength" to Int::class,
                "horizon" to Int::class, "populationSize" to Int::class, "parentSize" to Int::class,
                "evalsPerGeneration" to Int::class)
    init {defaultParams.checkConsistency(types.keys.toList())}
    override fun getAgent(settings: DoubleArray): SimpleActionPlayerInterface {
        val settingsMap = settingsToMap(settings)

        return RHCAAgent(
                timeLimit = settingsMap.getOrDefault("timeBudget", defaultParams.timeBudget) as Int,
                sequenceLength = settingsMap.getOrDefault("sequenceLength", defaultParams.sequenceLength) as Int,
                horizon = settingsMap.getOrDefault("horizon", defaultParams.planningHorizon) as Int,
                useShiftBuffer = settingsMap.getOrDefault("useShiftBuffer", defaultParams.params.contains("useShiftBuffer")) as Boolean,
                probMutation = settingsMap.getOrDefault("probMutation", defaultParams.getParam("probMutation", "0.1").toDouble()) as Double,
                flipAtLeastOneValue = settingsMap.getOrDefault("flipAtLeastOneValue", defaultParams.params.contains("flipAtLeastOneValue")) as Boolean,
                populationSize = settingsMap.getOrDefault("populationSize", defaultParams.getParam("populationSize", "10").toInt()) as Int,
                parentSize = settingsMap.getOrDefault("parentSize", defaultParams.getParam("parentSize", "1").toInt()) as Int,
                evalsPerGeneration = settingsMap.getOrDefault("evalsPerGeneration", defaultParams.getParam("evalsPerGeneration", "10").toInt()) as Int,
                discountFactor = settingsMap.getOrDefault("discountFactor", defaultParams.getParam("discountFactor", "1.0").toDouble()) as Double
        )
    }
}

class MCTSSearchSpace(val defaultParams: AgentParams, fileName: String) : HopshackleSearchSpace<SimpleActionPlayerInterface>(fileName) {

    override val types: Map<String, KClass<*>>
        get() = mapOf("sequenceLength" to Int::class, "horizon" to Int::class, "pruneTree" to Boolean::class,
                "C" to Double::class, "maxActions" to Int::class, "rolloutPolicy" to SimpleActionPlayerInterface::class,
                "discountFactor" to Double::class, "oppMCTS" to Boolean::class, "timeBudget" to Int::class,
                "oppWithdraw" to Int::class, "oppReinforce" to Int::class, "oppAttack" to Double::class, "oppDefense" to Double::class)
    init {defaultParams.checkConsistency(types.keys.toList())}
    override fun getAgent(settings: DoubleArray): SimpleActionPlayerInterface {
        val settingsMap = settingsToMap(settings)
        return MCTSTranspositionTableAgentMaster(MCTSParameters(
                C = settingsMap.getOrDefault("C", defaultParams.getParam("C", "0.1").toDouble()) as Double,
                maxPlayouts = settingsMap.getOrDefault("evalBudget", defaultParams.evalBudget) as Int,
                timeLimit = settingsMap.getOrDefault("timeBudget", defaultParams.timeBudget) as Int,
                horizon = settingsMap.getOrDefault("horizon", defaultParams.planningHorizon) as Int,
                pruneTree = settingsMap.getOrDefault("pruneTree", defaultParams.params.contains("pruneTree")) as Boolean,
                maxDepth = settingsMap.getOrDefault("sequenceLength", defaultParams.getParam("sequenceLength", "10").toInt()) as Int,
                maxActions = settingsMap.getOrDefault("maxActions", defaultParams.getParam("maxActions", "10").toInt()) as Int,
                actionFilter = settingsMap.getOrDefault("actionFilter", defaultParams.getParam("actionFilter", "none")) as String,
                discountRate = settingsMap.getOrDefault("discountFactor", defaultParams.getParam("discountFactor", "1.0").toDouble()) as Double
        ),
                stateFunction = LandCombatStateFunction,
                rolloutPolicy = settingsMap.getOrDefault("rolloutPolicy", SimpleActionDoNothing(1000)) as SimpleActionPlayerInterface,
                opponentModel = when {
                    (settingsMap.getOrDefault("oppMCTS", false) as Boolean) -> null
                    defaultParams.opponentModel == "MCTS" -> null
                    else -> defaultParams.getOpponentModel(settingsMap)
                }
        )
    }
}

class UtilitySearchSpace(val agentParams: AgentParams, val defaultScore: ScoreParams, fileName: String) : HopshackleSearchSpace<SimpleActionPlayerInterface>(fileName) {
    override val types: Map<String, KClass<*>>
        get() = mapOf("visibilityNode" to Double::class, "visibilityArc" to Double::class, "theirCity" to Double::class,
                "ownForce" to Double::class, "theirForce" to Double::class, "fortressValue" to Double::class,
                "forceEntropy" to Double::class, "localAdvantage" to Double::class)

    override fun getAgent(settings: DoubleArray): SimpleActionPlayerInterface {
        return agentParams.createAgent("STD")
    }

    fun getScoreFunction(settings: DoubleArray): (LandCombatGame, Int) -> Double {
        val settingsMap = settingsToMap(settings)
        return compositeScoreFunction(listOf(
                visibilityScore(
                        settingsMap.getOrDefault("visibilityNode", defaultScore.nodeVisibility) as Double,
                        settingsMap.getOrDefault("visibilityArc", defaultScore.arcVisibility) as Double),
                simpleScoreFunction(
                        5.0,
                        settingsMap.getOrDefault("ownForce", defaultScore.ownForce) as Double,
                        settingsMap.getOrDefault("theirCity", defaultScore.theirCity) as Double,
                        settingsMap.getOrDefault("theirForce", defaultScore.theirForce) as Double),
                fortressScore(
                        settingsMap.getOrDefault("fortressValue", defaultScore.fortressValue) as Double),
                entropyScoreFunction(settingsMap.getOrDefault("forceEntropy", defaultScore.forceEntropy) as Double),
                localAdvantageScoreFunction(settingsMap.getOrDefault("localAdvantage", defaultScore.localAdvantage) as Double))
        )
    }
}
