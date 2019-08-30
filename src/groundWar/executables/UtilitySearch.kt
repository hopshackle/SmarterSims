package groundWar.executables

import agents.*
import evodef.EvolutionLogger
import ggi.*
import groundWar.*
import ntbea.*
import utilities.StatsCollator
import java.io.*
import java.lang.AssertionError
import kotlin.math.*
import kotlin.streams.*

fun main(args: Array<String>) {
    // arguments are:
    // 0 : agent file to use
    // 1 : 3-Tuples boolean
    // 2: number of trials
    // 3: STD/EXP_MEAN/EXP_FULL

    if (args.size < 4) throw AssertionError("Must specify at least three parameters: Agent file, 3-Tuples?, trials, STD/EXP_MEAN/EXP_FULL:nn/EXP_SQRT:nn")
    val totalRuns = args[2].split("|")[0].toInt()
    val reportEvery = args[2].split("|").getOrNull(1)?.toInt() ?: totalRuns
    val nTupleSystem = when {
        args[3] == "STD" -> NTupleSystem()
        args[3] == "EXP_MEAN" -> NTupleSystemExp(30, expWeightExplore = false)
        args[3].startsWith("EXP_FULL") -> {
            val minWeight = args[3].split(":")[1].toDouble()
            NTupleSystemExp(30, minWeight = minWeight)
        }
        args[3].startsWith("EXP_SQRT:") -> {
            val minWeight = args[3].split(":")[1].toDouble()
            NTupleSystemExp(30, minWeight = minWeight, exploreWithSqrt = true)
        }
        args[3].startsWith("EXP_SQRT") -> {
            NTupleSystemExp(30, expWeightExplore = false, exploreWithSqrt = true)
        }
        else -> throw AssertionError("Unknown NTuple parameter: " + args[3])
    }
    val use3Tuples = args[1] == "true"
    nTupleSystem.use3Tuple = use3Tuples

    val agentParams = {
        val fileAsLines = BufferedReader(FileReader(args[0])).lines().toList()
        createAgentParamsFromString(fileAsLines)
    }.invoke()
    val searchSpace = UtilitySearchSpace(agentParams)

    val params = if (args.size > 4) {
        val fileAsLines = BufferedReader(FileReader(args[4])).lines().toList()
        createIntervalParamsFromString(fileAsLines).sampleParams()
    } else EventGameParams()

    val stateSpaceSize = (0 until searchSpace.nDims()).fold(1, { acc, i -> acc * searchSpace.nValues(i) })
    val twoTupleSize = (0 until searchSpace.nDims() - 1).map { i ->
        searchSpace.nValues(i) * (i + 1 until searchSpace.nDims()).map(searchSpace::nValues).sum()
    }.sum()
    val threeTupleSize = (0 until searchSpace.nDims() - 2).map { i ->
        searchSpace.nValues(i) * (i + 1 until searchSpace.nDims()).map { j ->
            searchSpace.nValues(j) * (j + 1 until searchSpace.nDims()).map(searchSpace::nValues).sum()
        }.sum()
    }.sum()

    val ntbea = NTupleBanditEA(100.0, min(50.0, stateSpaceSize * 0.01).toInt())

    ntbea.banditLandscapeModel = nTupleSystem
    ntbea.banditLandscapeModel.searchSpace = searchSpace
    ntbea.resetModelEachRun = false

    val logger = EvolutionLogger()
    println("Search space consists of $stateSpaceSize states and $twoTupleSize possible 2-Tuples" +
            "${if (use3Tuples) " and $threeTupleSize possible 3-Tuples" else ""}:")
    (0 until searchSpace.nDims()).forEach {
        println(String.format("%20s has %d values %s", searchSpace.name(it), searchSpace.nValues(it),
                (0 until searchSpace.nValues(it)).map { i -> searchSpace.value(it, i) }.joinToString()))
    }
    repeat(totalRuns / reportEvery) {
        ntbea.runTrial(GroundWarEvaluator(searchSpace, params, logger, null), reportEvery)
        // tuples gets cleared out
        println("Current best sampled point (using mean estimate): " + nTupleSystem.bestOfSampled.joinToString() +
                String.format(", %.3g", nTupleSystem.getMeanEstimate(nTupleSystem.bestOfSampled)))
        // println("Current best predicted point (using mean estimate): " + nTupleSystem.bestSolution.joinToString() +
        //         String.format(", %.3g", nTupleSystem.getMeanEstimate(nTupleSystem.bestSolution)))
        val tuplesExploredBySize = (1..searchSpace.nDims()).map { size ->
            nTupleSystem.tuples.filter { it.tuple.size == size }
                    .map { it.ntMap.size }.sum()
        }
        println("Tuples explored by size: ${tuplesExploredBySize.joinToString()}")
        println("Summary of 1-tuple statistics after ${nTupleSystem.numberOfSamples()} samples:")
        nTupleSystem.tuples.withIndex().take(searchSpace.nDims()).forEach { (i, t) ->
            t.ntMap.toSortedMap().forEach { (k, v) ->
                println(String.format("\t%20s\t%s\t%d trials\t mean %.3g +/- %.2g", searchSpace.name(i), k, v.n(), v.mean(), v.stdErr()))
            }
        }
        println("\nSummary of 10 most tried full-tuple statistics:")
        nTupleSystem.tuples.find { it.tuple.size == searchSpace.nDims() }
                ?.ntMap?.map { (k, v) -> k to v }?.sortedByDescending { (_, v) -> v.n() }?.take(10)?.forEach { (k, v) ->
            println(String.format("\t%s\t%d trials\t mean %.3g +/- %.2g\t(NTuple estimate: %.3g)", k, v.n(), v.mean(), v.stdErr(), nTupleSystem.getMeanEstimate(k.v)))
        }
        println("\nSummary of 5 highest valued full-tuple statistics:")
        nTupleSystem.tuples.find { it.tuple.size == searchSpace.nDims() }
                ?.ntMap?.map { (k, v) -> k to v }?.sortedByDescending { (_, v) -> v.mean() }?.take(3)?.forEach { (k, v) ->
            println(String.format("\t%s\t%d trials\t mean %.3g +/- %.2g\t(NTuple estimate: %.3g)", k, v.n(), v.mean(), v.stdErr(), nTupleSystem.getMeanEstimate(k.v)))
        }
        println("")
    }
}


class UtilitySearchSpace(val agentParams: AgentParams) : HopshackleSearchSpace() {
    override val names: Array<String>
        get() = arrayOf("visibilityNode", "visibilityArc", "theirCity", "ownForce", "theirForce")
    override val values: Array<Array<*>>
        get() = arrayOf(
                arrayOf(0.0, 0.1, 0.5, 1.0, 5.0),
                arrayOf(0.0, 0.1, 0.5, 1.0, 5.0),
                arrayOf(0.0, -1.0, -5.0, -10.0),
                arrayOf(0.0, 0.1, 0.5, 1.0, 3.0),
                arrayOf(0.0, -0.1, -0.5, -1.0, -3.0)
        )

    fun getScoreFunction(settings: IntArray): (LandCombatGame, Int) -> Double {
        return compositeScoreFunction(
                visibilityScore(values[0][settings[0]] as Double, values[1][settings[1]] as Double),
                simpleScoreFunction(5.0, values[3][settings[3]] as Double,
                        values[2][settings[2]] as Double, values[4][settings[4]] as Double)
        )

    }
}