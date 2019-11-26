package agents.RHEA

import agents.DoNothingAgent
import agents.SimpleActionDoNothing
import ggi.*
import groundWar.LandCombatGame
import utilities.EntityLog
import utilities.StatsCollator
import kotlin.math.pow
import kotlin.random.Random

internal var RHEARandom = Random(System.currentTimeMillis())

fun evaluateSequenceDelta(gameState: AbstractGameState,
                          seq: IntArray,
                          playerId: Int,
                          discountFactor: Double,
                          horizon: Int,
                          opponentModel: SimplePlayerInterface = DoNothingAgent()): Double {
    val intPerAction = gameState.codonsPerAction()
    val actions = IntArray(2 * intPerAction)
    var runningScore = if (gameState is ActionAbstractGameState) gameState.score(playerId) else gameState.score()
    var runningTime = gameState.nTicks()

    fun discount(results: List<Pair<Int, Double>>): Double {
        var discount = 1.0
        var delta = 0.0
        results.forEach { (time, score) ->
            val tickDelta = score - runningScore
            val timeDelta = time - runningTime
            discount *= discountFactor.pow(timeDelta)
            runningScore = score
            delta += tickDelta * discount
        }
        return delta
    }

    val retValue = if (gameState is ActionAbstractGameState) {
        val mutatedAgent = SimpleActionEvoAgentRollForward(seq, horizon)
        gameState.registerAgent(playerId, mutatedAgent)
        if (opponentModel is SimpleActionPlayerInterface)
            gameState.registerAgent(1 - playerId, opponentModel)
        else
            gameState.registerAgent(1 - playerId, SimpleActionDoNothing(horizon))

        // to apply a discount here, can I record the score at each point using the childAgent?
        gameState.next(if (horizon > 0) horizon else seq.size)
        discount(mutatedAgent.scoreByTime + Pair(gameState.nTicks(), gameState.score(playerId)))
    } else {
        val scoreByTime = mutableListOf<Pair<Int, Double>>()
        for (action in seq) {
            actions[playerId * intPerAction] = action
            actions[(1 - playerId) * intPerAction] = opponentModel.getAction(gameState, 1 - playerId)
            gameState.next(actions)
            scoreByTime.add(Pair(gameState.nTicks(), if (playerId == 0) gameState.score() else -gameState.score()))
        }
        discount(scoreByTime)
    }
    return retValue
}

fun shiftLeftAndRandomAppend(startingArray: IntArray, nShift: Int, nActions: Int): IntArray {
    val p = IntArray(startingArray.size)
    for (i in 0 until p.size)
        p[i] = when {
            i >= p.size - nShift -> RHEARandom.nextInt(nActions)
            else -> startingArray[i + nShift]
        }
    return p
}

fun mutate(v: IntArray, mutProb: Double, nActions: Int,
           useMutationTransducer: Boolean = false, repeatProb: Double = 0.5,
           flipAtLeastOneValue: Boolean = false): IntArray {

    if (useMutationTransducer) {
        // build it dynamically in case any of the params have changed
        val mt = MutationTransducer(mutProb, repeatProb)
        return mt.mutate(v, nActions)
    }

    val n = v.size
    val x = IntArray(n)
    // pointwise probability of additional mutations
    // choose element of vector to mutate
    var ix = RHEARandom.nextInt(n)
    if (!flipAtLeastOneValue) {
        // setting this to -1 means it will never match the first clause in the if statement in the loop
        // leaving it at the randomly chosen value ensures that at least one bit (or more generally value) is always flipped
        ix = -1
    }
    // copy all the values faithfully apart from the chosen one
    for (i in 0 until n) {
        if (i == ix || RHEARandom.nextDouble() < mutProb) {
            x[i] = mutateValue(v[i], nActions)
        } else {
            x[i] = v[i]
        }
    }
    return x
}

fun mutateValue(cur: Int, nPossible: Int): Int {
    // the range is nPossible-1, since we
    // selecting the current value is not allowed
    // therefore we add 1 if the randomly chosen
    // value is greater than or equal to the current value
    if (nPossible <= 1) return cur
    val rx = RHEARandom.nextInt(nPossible - 1)
    return if (rx >= cur) rx + 1 else rx
}

fun randomPoint(nValues: Int, sequenceLength: Int): IntArray {
    val p = IntArray(sequenceLength)
    for (i in p.indices) {
        p[i] = RHEARandom.nextInt(nValues)
    }
    return p
}

data class SimpleEvoAgent(
        var flipAtLeastOneValue: Boolean = true,
        // var expectedMutations: Double = 10.0,
        var probMutation: Double = 0.2,
        var sequenceLength: Int = 200,
        var nEvals: Int = 20,
        var resample: Int = 1,
        var timeLimit: Int = 1000,
        var tickBudget: Int = 0,
        var useShiftBuffer: Boolean = true,
        var useMutationTransducer: Boolean = false,
        var repeatProb: Double = 0.5,  // only used with mutation transducer
        var discountFactor: Double? = null,
        val horizon: Int = 1,
        var opponentModel: SimplePlayerInterface = DoNothingAgent(),
        val name: String = "EA"
) : SimplePlayerInterface {
    override fun getAgentType(): String {
        return "SimpleEvoAgent"
    }

    // these are all the parameters that control the agend
    internal var buffer: IntArray? = null // randomPoint(sequenceLength)
    private lateinit var debugLog: EntityLog

    var debug = false
        set(value) {
            if (value) {
                debugLog = EntityLog("SimpleEvoAgent_$name")
            } else {
                debugLog.close()
            }
            field = value
        }

    // SimplePlayerInterface opponentModel = new RandomAgent();
    override fun reset(): SimplePlayerInterface {
        buffer = null
        return this
    }

    var x: Int? = 1

    fun getActions(gameState: AbstractGameState, playerId: Int): IntArray {
        val startTime = System.currentTimeMillis()
        var solution = buffer ?: randomPoint(gameState.nActions(), sequenceLength)
        if (useShiftBuffer) {
            val numberToShiftLeft = gameState.codonsPerAction()
            solution = shiftLeftAndRandomAppend(solution, numberToShiftLeft, gameState.nActions())
        } else {
            // System.out.println("New random solution with nActions = " + gameState.nActions())
            solution = randomPoint(gameState.nActions(), sequenceLength)
        }
        val startScore: Double = evalSeq(gameState.copy(), solution, playerId)
        var curScore = startScore
        var iterations = 0
        if (debug) {
            debugLog.log("Starting State at time ${gameState.nTicks()}:")
            debugLog.log((gameState as LandCombatGame).world.cities.joinToString("\n") { c -> "\t${c.name}\t${c.owner}\t${c.pop}" })
            debugLog.log(gameState.world.currentTransits.joinToString("\n") { it.toString() })
            debugLog.log("\n")
            debugLog.log(String.format("Player %d starting score to beat is %.1f with %s (%s)", playerId, startScore, solution.joinToString(""),
                    gameState.translateGene(playerId, solution)))
        }

        var ticksUsed = if (tickBudget > 0) 0 else -1
        do {
            // evaluate the current one
            val mut = mutate(solution, probMutation, gameState.nActions(), useMutationTransducer, repeatProb, flipAtLeastOneValue)
            val mutScore = (0 until resample).map {
                val rolloutGame = gameState.copy()
                val gameResult = evalSeq(rolloutGame, mut, playerId)
                if (tickBudget > 0) ticksUsed += rolloutGame.nTicks() - gameState.nTicks()
                gameResult
            }.average()
            if (debug) debugLog.log(String.format("\t%3d: Gets score of %.1f with %s (%s)", iterations, mutScore, mut.joinToString(""),
                    (gameState as LandCombatGame).translateGene(playerId, mut)))
            if (mutScore >= curScore) {
                curScore = mutScore
                solution = mut
                if (debug) debugLog.log(String.format("Player %d finds better score of %.1f with %s (%s)", playerId, mutScore, solution.joinToString(""),
                        (gameState as LandCombatGame).translateGene(playerId, solution)))
            }
           iterations += resample
        } while (ticksUsed < tickBudget && iterations < nEvals && System.currentTimeMillis() - startTime < timeLimit)
        if (debug) debugLog.flush()
        if (solution.size < 3)
            throw AssertionError("Solution is too short")
        StatsCollator.addStatistics("${name}_ToGene", solution[1])
        StatsCollator.addStatistics("${name}_ProportionsGene", solution[2])
        StatsCollator.addStatistics("${name}_Time", System.currentTimeMillis() - startTime)
        StatsCollator.addStatistics("${name}_Evals", iterations)
        StatsCollator.addStatistics("${name}_Ticks", ticksUsed)
        StatsCollator.addStatistics("${name}_HorizonUsed", elapsedLengthOfPlan(solution, gameState.copy(), playerId))
        buffer = solution
        return solution
    }


    private fun evalSeq(gameState: AbstractGameState, seq: IntArray, playerId: Int): Double {
        return evaluateSequenceDelta(gameState, seq, playerId, discountFactor
                ?: 1.0, this.horizon, opponentModel)
    }


    override fun toString(): String {
        return "SEA: $nEvals : $sequenceLength : $opponentModel"
    }

    override fun getAction(gameState: AbstractGameState, playerRef: Int): Int {
        return getActions(gameState, playerRef)[0]
    }

}
