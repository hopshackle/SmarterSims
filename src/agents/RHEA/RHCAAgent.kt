package agents.RHEA

import agents.SimpleActionDoNothing
import ggi.*
import utilities.StatsCollator
import kotlin.math.min

data class RHCAAgent(
        val flipAtLeastOneValue: Boolean = true,
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
    private var currentPopulation = listOf<IntArray>()
    private var opponentPopulation = listOf<IntArray>()
    private var currentScores = mutableListOf<Double>()
    private var opponentScores = mutableListOf<Double>()
    private var currentParents = listOf<IntArray>()
    private var opponentParents = listOf<IntArray>()

    override fun getAction(gameState: ActionAbstractGameState, playerRef: Int): Action {
        val startTime = System.currentTimeMillis()
        // we have a time budget and an evaluations budget.
        // These are straightforward to apply for 1+1 ES, what do I do here?
        // On each iteration we first score the best members of the current population
        // then we breed them....so we can still do this within a time budget, but with a coarser-grain
        if (currentPopulation.isEmpty()) {
            currentPopulation = (0 until populationSize).map { randomPoint(gameState.nActions(), sequenceLength) }
            opponentPopulation = (0 until populationSize).map { randomPoint(gameState.nActions(), sequenceLength) }
        }

        if (useShiftBuffer) {
            val numberToShiftLeft = gameState.codonsPerAction()
            currentPopulation = currentPopulation.map { p -> shiftLeftAndRandomAppend(p, numberToShiftLeft, gameState.nActions()) }
            opponentPopulation = opponentPopulation.map { p -> shiftLeftAndRandomAppend(p, numberToShiftLeft, gameState.nActions()) }
        }

        var currentBest: IntArray
        var iterations = 0
        do {
            scoreCurrentPopulation(gameState, playerRef)
            currentBest = currentScores.zip(currentPopulation).maxBy { p -> p.first }?.second ?: currentPopulation[0]

            breedCurrentPopulation(gameState.nActions())

            iterations++

        } while (timeLimit + startTime > System.currentTimeMillis())

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
        val evals = min(evalsPerGeneration, populationSize)
        (0 until populationSize).forEach { i ->
            val opponentOrdering = (0 until populationSize).shuffled()
            (0 until evals).forEach {
                val j = opponentOrdering[it]
                val score = evaluateSequenceDelta(gameState.copy(), currentPopulation[i], playerId,
                        discountFactor, horizon, SimpleActionEvoAgentRollForward(opponentPopulation[j], horizon))
                currentScores[i] += score
                opponentScores[j] -= score // we assume zero-sum for convenience
                opponentTrials[j]++
            }
            opponentScores[i] = opponentScores[i] / evals
        }
        opponentScores = opponentScores.zip(opponentTrials).map { (score, n) -> score / n }.toMutableList()
    }

    private fun breedCurrentPopulation(nActions: Int) {
        val effectiveParentSize = min(parentSize, populationSize / 2)
        // firstly we populate the parents to keep
        currentParents = currentPopulation.zip(currentScores).sortedBy { p -> p.second }.take(effectiveParentSize).map { p -> p.first }
        opponentParents = opponentPopulation.zip(opponentScores).sortedBy { p -> p.second }.take(effectiveParentSize).map { p -> p.first }
        currentPopulation = currentParents.toList()
        opponentPopulation = opponentParents.toList()
        // and include them in the next generation

        // then we mutate them to get the next generation
        (effectiveParentSize until populationSize).forEach { i ->
            currentPopulation += (mutate(currentParents[RHEARandom.nextInt(effectiveParentSize)], probMutation, nActions, flipAtLeastOneValue = flipAtLeastOneValue))
            opponentPopulation += (mutate(opponentParents[RHEARandom.nextInt(effectiveParentSize)], probMutation, nActions, flipAtLeastOneValue = flipAtLeastOneValue))
        }
    }

    override fun getPlan(gameState: ActionAbstractGameState, playerRef: Int): List<Action> {
        return convertGenomeToActionList(currentParents[0], gameState.copy(), playerRef)
    }

    override fun getForwardModelInterface(): SimpleActionPlayerInterface {
        if (currentParents.isEmpty()) return SimpleActionDoNothing(1000)
        return SimpleActionEvoAgentRollForward(currentParents[0], horizon)
    }

    override fun backPropagate(finalScore: Double) {}

    override fun reset(): SimpleActionPlayerInterface {
        currentPopulation = listOf()
        opponentPopulation = listOf()
        currentScores = mutableListOf()
        opponentScores = mutableListOf()
        currentParents = listOf()
        opponentParents = listOf()
        return this
    }

    override fun getAgentType() = name

}