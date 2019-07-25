package agents

import ggi.AbstractGameState
import ggi.*
import utilities.StatsCollator
import kotlin.random.Random

data class RHCAAgent(
        val flipAtLeastOneValue: Boolean = true,
        // var expectedMutations: Double = 10.0,
        val probMutation: Double = 0.2,
        val sequenceLength: Int = 200,
        val evalsPerGeneration: Int = 5,
        val populationSize: Int = 20,
        val parentSize: Int = 2,
        val timeLimit: Int = 1000,
        val useShiftBuffer: Boolean = true,
        val discountFactor: Double = 1.0,
        val horizon: Int = 1,
        val name: String = "RHCAAgent"
) : SimpleActionPlayerInterface {

    // we are going to need a set of populations for both us and the opponent
    // for the moment we will assume a 2-player environment for GroundWar
    // we will also assume the same EA parameters for both us and opponent
    private var currentPopulation: List<IntArray> = (0 until populationSize).map { randomPoint(sequenceLength) }
    private var opponentPopulation: List<IntArray> = (0 until populationSize).map { randomPoint(sequenceLength) }
    private var currentScores = mutableListOf<Double>()
    private var opponentScores = mutableListOf<Double>()
    private var currentParents = listOf<IntArray>()
    private var opponentParents = listOf<IntArray>()
    internal val random = Random(System.currentTimeMillis())

    override fun getAction(gameState: ActionAbstractGameState, playerRef: Int): Action {
        val startTime = System.currentTimeMillis()
        // we have a time budget and an evaluations budget.
        // These are straightforward to apply for 1+1 ES, what do I do here?
        // On each iteration we first score the best members of the current population
        // then we breed them....so we can still do this within a time budget, but with a coarser-grain

        if (useShiftBuffer) {
            val numberToShiftLeft = gameState.codonsPerAction()
            currentPopulation = currentPopulation.map { p -> shiftLeftAndRandomAppend(p, numberToShiftLeft, gameState.nActions(), random) }
            opponentPopulation = opponentPopulation.map { p -> shiftLeftAndRandomAppend(p, numberToShiftLeft, gameState.nActions(), random) }
        }

        var currentBest: IntArray
        var iterations = 0
        do {
            scoreCurrentPopulation(gameState, playerRef)
            currentBest = currentScores.zip(currentPopulation).maxBy { p -> p.first }?.second ?: currentPopulation[0]

            breedCurrentPopulation()

            iterations++

        } while (timeLimit + startTime < System.currentTimeMillis())

        StatsCollator.addStatistics("${name}_Time", System.currentTimeMillis() - startTime)
        StatsCollator.addStatistics("${name}_Evals", iterations)
        StatsCollator.addStatistics("${name}_HorizonUsed", elapsedLengthOfPlan(currentBest, gameState.copy(), playerRef))

        return gameState.translateGene(playerRef, currentBest)
    }

    private fun scoreCurrentPopulation(gameState: ActionAbstractGameState, playerId: Int) {
        // we have a time budget to score the members of the population
        // for each currentPopulation, we sample N members (without replacement) from the
        // opponentPopulation
        // We store the result in currentScores and opponentScores
        opponentScores = MutableList(populationSize) { 0.0 }
        currentScores = MutableList(populationSize) { 0.0 }
        val opponentTrials = MutableList(populationSize) { 0 }
        (0 until populationSize).forEach { i ->
            val opponentOrdering = (0 until populationSize).shuffled()
            (0 until evalsPerGeneration).forEach {
                val j = opponentOrdering[it]
                val score = evaluateSequenceDelta(gameState.copy(), currentPopulation[i], playerId,
                        discountFactor, horizon, SimpleActionEvoAgentRollForward(opponentPopulation[j], horizon))
                currentScores[i] += score
                opponentScores[j] -= score // we assume zero-sum for convenience
                opponentTrials[j]++
            }
            opponentScores[i] = opponentScores[i] / evalsPerGeneration
        }
        opponentScores = opponentScores.zip(opponentTrials).map { (score, n) -> score / n }.toMutableList()
    }

    private fun breedCurrentPopulation() {
        // firstly we populate the parents to keep
        // then we mutate them to get the next generation
        val mut = mutate(solution, probMutation, gameState.nActions())
        val mutScore = evalSeq(gameState.copy(), mut, playerId)
        if (mutScore >= curScore) {
            curScore = mutScore
            solution = mut
            //        println(String.format("Player %d finds better score of %.1f with %s", playerId, mutScore, solution.joinToString("")))
        }
        solutions.add(solution)
    }

    private fun randomPoint(nValues: Int): IntArray {
        val p = IntArray(sequenceLength)
        for (i in p.indices) {
            p[i] = random.nextInt(nValues)
        }
        return p
    }


    override fun getPlan(gameState: ActionAbstractGameState, playerRef: Int): List<Action> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getForwardModelInterface(): SimpleActionPlayerInterface {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun backPropagate(finalScore: Double) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun reset(): SimpleActionPlayerInterface {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAgentType() = name

}