package agents.RHEA

import agents.DoNothingAgent
import agents.SimpleActionDoNothing
import ggi.*
import utilities.StatsCollator
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
    var discount = 1.0
    var delta = 0.0

    fun discount(nextScore: Double) {
        val tickDelta = nextScore - runningScore
        runningScore = nextScore
        delta += tickDelta * discount
        discount *= discountFactor
    }

    if (gameState is ActionAbstractGameState) {
        val mutatedAgent = SimpleActionEvoAgentRollForward(seq, horizon)
        gameState.registerAgent(playerId, mutatedAgent)
        if (opponentModel is SimpleActionPlayerInterface)
            gameState.registerAgent(1 - playerId, opponentModel)
        else
            gameState.registerAgent(1 - playerId, SimpleActionDoNothing(horizon))
        if (discountFactor < 1.0) {
            throw AssertionError("Discount not currently implemented for ActionAbstractGameState")
            // TODO: need to get vector of future rewards back, along with the times at which they occur to calculate this
        }
        gameState.next(if (horizon > 0) horizon else seq.size)
        discount(gameState.score(playerId))
        return delta
    } else {
        for (action in seq) {
            actions[playerId * intPerAction] = action
            actions[(1 - playerId) * intPerAction] = opponentModel.getAction(gameState, 1 - playerId)
            gameState.next(actions)
            discount(gameState.score())
        }
    }
    return if (playerId == 0)
        delta
    else
        -delta
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
        var timeLimit: Int = 1000,
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
        //       println(String.format("Player %d starting score to beat is %.1f", playerId, startScore))
        var iterations = 0
        do {
            // evaluate the current one
            val mut = mutate(solution, probMutation, gameState.nActions(), useMutationTransducer, repeatProb, flipAtLeastOneValue)
            val mutScore = evalSeq(gameState.copy(), mut, playerId)
            if (mutScore >= curScore) {
                curScore = mutScore
                solution = mut
                //        println(String.format("Player %d finds better score of %.1f with %s", playerId, mutScore, solution.joinToString("")))
            }
            iterations++
        } while (iterations < nEvals && System.currentTimeMillis() - startTime < timeLimit)
        StatsCollator.addStatistics("${name}_ToGene", solution[1])
        StatsCollator.addStatistics("${name}_ProportionsGene", solution[2])
        StatsCollator.addStatistics("${name}_WaitGene", solution[3])
        StatsCollator.addStatistics("${name}_Time", System.currentTimeMillis() - startTime)
        StatsCollator.addStatistics("${name}_Evals", iterations)
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