package games.eventqueuegame

import agents.*
import java.io.FileWriter
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties

fun main(args: Array<String>) {
    // the args input needs to be the Intervals that we wish to vary
    // or, better, a file that contains all the parameters and intervals
    // this cna have lines of the form:
    //      minConnections = 2              - a single parameter setting
    //      OODALoop = 10, [5, 50]          - a single parameter setting for BLUE, and an Interval for RED

    // initially however I will hard-code this here
    val intervalParams = IntervalParams(
            startingForce = listOf(interval(100), interval(100)),
            fogStrengthAssumption = listOf(interval(10), interval(10)),
            speed = listOf(interval(10.0), interval(10.0)),
            fortAttackerDivisor = interval(3.0),
            fortDefenderExpBonus = interval(0.10),
            lanchesterCoeff = listOf(interval(0.20), interval(0.10, 0.30)),
            lanchesterExp = listOf(interval(0.5), interval(0.5)),
            OODALoop = listOf(interval(10), interval(10)),
            minAssaultFactor = listOf(interval(2.0), interval(2.0)),
            planningHorizon = listOf(interval(100), interval(100))
    )
    val numberFormatter: (Any?) -> String = { it ->
        when (it) {
            null -> "null"
            is Int -> String.format("%d", it)
            is Double -> String.format("%.4g", it)
            else -> it.toString()
        }
    }

    val statisticsToKeep: Map<String, (LandCombatGame) -> Number> = mapOf(
            "BLUE_WINS" to { g: LandCombatGame -> if (g.score(0) - g.score(1) > 0.0) 1 else 0 },
            "RED_WINS" to { g: LandCombatGame -> if (g.score(0) - g.score(1) < 0.0) 1 else 0 },
            "BLUE_FORCE" to { g: LandCombatGame -> simpleScoreFunction(0.0, 1.0, 0.0, 0.0).invoke(g, 0) },
            "RED_FORCE" to { g: LandCombatGame -> simpleScoreFunction(0.0, 1.0, 0.0, 0.0).invoke(g, 1) },
            "BLUE_CITIES" to { g: LandCombatGame -> simpleScoreFunction(1.0, 0.0, 0.0, 0.0).invoke(g, 0).toInt() },
            "RED_CITIES" to { g: LandCombatGame -> simpleScoreFunction(1.0, 0.0, 0.0, 0.0).invoke(g, 1).toInt() },
            "DRAW" to { g: LandCombatGame -> if (g.score(0) == g.score(1)) 1 else 0 },
            "BLUE_SCORE" to { g: LandCombatGame -> g.score(0) },
            "RED_SCORE" to { g: LandCombatGame -> g.score(1) },
            "GAME_LENGTH" to { g: LandCombatGame -> g.nTicks() },
            "COMPLETE_VICTORY" to { g: LandCombatGame -> if (g.nTicks() < 1000) 1 else 0 }
    )
    val statisticKeys = statisticsToKeep.keys.toList()
    val paramKeys: List<String> = IntervalParams::class.memberProperties.flatMap {
        when {
            it.returnType.toString().startsWith("kotlin.collections.List") -> listOf("BLUE_" + it.name, "RED_" + it.name)
            else -> listOf(it.name)
        }
    }

    val fileName = if (args.size > 0) args[0] else "output.txt"
    val rnd = Random(1)
    val fileWriter = FileWriter(fileName)
    fileWriter.write(paramKeys.joinToString(separator = "\t", postfix = "\t"))
    fileWriter.write(statisticKeys.joinToString(separator = "\t", postfix = "\n"))
    StatsCollator.clear()
    repeat(100) {

        val params = intervalParams.sampleParams(seed = rnd.nextLong())
        val planningHorizon = intervalParams.planningHorizon.map { it.sampleFrom().toInt() }.toIntArray()
        val game = LandCombatGame(World(params = params))
        game.scoreFunction[PlayerId.Blue] = compositeScoreFunction(
                simpleScoreFunction(5.0, 1.0, 0.0, -0.5),
                visibilityScore(0.0, 0.0)
                //   game.scoreFunction = specificTargetScoreFunction(50.0)
        )
        game.scoreFunction[PlayerId.Red] = compositeScoreFunction(
                simpleScoreFunction(5.0, 1.0, 0.0, -0.5),
                visibilityScore(0.0, 0.0)
                //   game.scoreFunction = specificTargetScoreFunction(50.0)
        )
        val blueOpponentModel =
                DoNothingAgent()
        //HeuristicAgent(2.0, 1.1)
        //        SimpleActionEvoAgent(SimpleEvoAgent(name = "OppEA", nEvals = 10, sequenceLength = 40, useMutationTransducer = false, probMutation = 0.1, horizon = params.planningHorizon))
        val blueAgent = SimpleActionEvoAgent(SimpleEvoAgent(nEvals = 200, timeLimit = 100, sequenceLength = 40,
                useMutationTransducer = false, probMutation = 0.1, useShiftBuffer = true,
                horizon = planningHorizon[0], opponentModel = blueOpponentModel)
        )
        game.registerAgent(0, blueAgent)
        val redAgent =
                SimpleActionEvoAgent(SimpleEvoAgent(nEvals = 200, timeLimit = 100, sequenceLength = 40,
                        useMutationTransducer = false, probMutation = 0.1, useShiftBuffer = true,
                        horizon = planningHorizon[1]))
        //      MCTSTranspositionTableAgentMaster(MCTSParameters(timeLimit = 100, maxPlayouts = 1000, horizon = params.planningHorizon[1]), LandCombatStateFunction)
        //       HeuristicAgent(params.minAssaultFactor[1], 1.1)
        game.registerAgent(1, redAgent)

        game.next(1000)


        val propertyMap = EventGameParams::class.memberProperties.map { it.name to it.get(params) }.toMap()
        fun propertyValue(key: String, position: Int): Number {
            return when (val array = (propertyMap[key])) {
                is IntArray -> array[position]
                is DoubleArray -> array[position]
                else -> throw AssertionError("Expecting Array")
            }
        }

        fileWriter.write(paramKeys.map { key ->
            when {
                key.startsWith("BLUE_") -> propertyValue(key.removePrefix("BLUE_"), 0)
                key.startsWith("RED_") -> propertyValue(key.removePrefix("RED_"), 1)
                else -> propertyMap.get(key)
            }
        }.joinToString(separator = "\t", postfix = "\t", transform = numberFormatter))

        val gameStats: Map<String, Number> = statisticKeys.map { it to statisticsToKeep.get(it)!!(game) }.toMap()

        fileWriter.write(statisticKeys.map(gameStats::get).joinToString(separator = "\t", postfix = "\n", transform = numberFormatter))
        fileWriter.flush()
        StatsCollator.addStatistics(gameStats)
    }
    fileWriter.close()
    println(StatsCollator.summaryString())
}