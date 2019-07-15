package ggi

import agents.SimpleEvoAgent
import groundWar.LandCombatGame
import kotlin.math.max

data class NoAction(val playerRef: Int, val waitTime: Int = 1) : Action {
    override fun apply(state: ActionAbstractGameState): Int {
        // Do absolutely nothing
        return state.nTicks() + waitTime
    }

    // only visible to planning player
    override fun visibleTo(player: Int, state: ActionAbstractGameState) = player == playerRef
}

class SimpleActionEvoAgent(val underlyingAgent: SimpleEvoAgent = SimpleEvoAgent(),
                           val opponentModel: SimpleActionPlayerInterface = SimpleActionDoNothing(underlyingAgent.horizon)
) : SimpleActionPlayerInterface {

    override fun reset(): SimpleActionPlayerInterface {
        underlyingAgent.reset()
        opponentModel.reset()
        return this
    }

    override fun getAgentType() = "SimpleActionEvoAgent: $underlyingAgent"

    override fun getAction(gameState: ActionAbstractGameState, playerRef: Int): Action {
        if (gameState is LandCombatGame) {
            val intPerAction = gameState.codonsPerAction()
            // the underlyingAgent does all the work on mutating the genome
            // we're just a wrapper for it
            opponentModel.getAction(gameState, 1 - playerRef)
            // this is just to give the opponent model some thinking time
            underlyingAgent.opponentModel = opponentModel.getForwardModelInterface()
            val gene = underlyingAgent.getActions(gameState, playerRef).sliceArray(0 until intPerAction)
            return gameState.translateGene(playerRef, gene)
        }
        throw AssertionError("Unexpected type of GameState $gameState")
    }

    override fun getForwardModelInterface(): SimpleActionPlayerInterface {
        return SimpleActionEvoAgentRollForward((underlyingAgent.buffer
                ?: intArrayOf()).copyOf(), underlyingAgent.horizon)
    }

    override fun getPlan(gameState: ActionAbstractGameState, playerRef: Int): List<Action> {
        val genome = underlyingAgent.buffer
        return convertGenomeToActionList(genome, gameState.copy(), playerRef)
    }

    override fun backPropagate(finalScore: Double) {}
}

fun convertGenomeToActionList(genome: IntArray?, gameState: AbstractGameState, playerRef: Int): List<Action> {
    val intPerAction = gameState.codonsPerAction()
    if (genome == null || genome.isEmpty()) return listOf()
    if (gameState is ActionAbstractGameState) {
        val retValue = (0 until (genome.size / intPerAction)).map { i ->
            val gene = genome.sliceArray(i * intPerAction until (i + 1) * intPerAction)
            val action = gameState.translateGene(playerRef, gene)
            val finishTime = action.apply(gameState)
            gameState.next(finishTime - gameState.nTicks())
            action
        }
        return retValue
    }
    return emptyList()
}

fun elapsedLengthOfPlan(genome: IntArray, gameState: AbstractGameState, playerRef: Int): Int {
    val intPerAction = gameState.codonsPerAction()
    val startingTime = gameState.nTicks()
    if (gameState is ActionAbstractGameState) {
        gameState.registerAgent(0, SimpleActionDoNothing(1000))
        gameState.registerAgent(1, SimpleActionDoNothing(1000))
        var i = 0
        do {
            val gene = genome.sliceArray(i * intPerAction until (i + 1) * intPerAction)
            val action = gameState.translateGene(playerRef, gene)
            val finishTime = action.apply(gameState)
            gameState.next(finishTime - gameState.nTicks())
            i++
        } while (i < genome.size / intPerAction)
    }
    return gameState.nTicks() - startingTime
}

/*
Will take actions using a specified genome...until the sequence runs out
 */
class SimpleActionEvoAgentRollForward(var genome: IntArray, val horizon: Int = 1) : SimpleActionPlayerInterface {

    override fun getAction(gameState: ActionAbstractGameState, playerRef: Int): Action {
        val intPerAction = gameState.codonsPerAction()
        if (genome.size >= intPerAction) {
            val gene = genome.sliceArray(0 until intPerAction)
            genome = genome.sliceArray(intPerAction until genome.size)
            return gameState.translateGene(playerRef, gene)
        } else {
            return NoAction(playerRef, horizon)
        }
    }

    override fun getPlan(gameState: ActionAbstractGameState, playerRef: Int): List<Action> {
        return convertGenomeToActionList(genome, gameState, playerRef)
    }

    override fun reset() = this

    override fun getAgentType() = "SimpleActionEvoAgentRollForward"

    override fun getForwardModelInterface(): SimpleActionPlayerInterface {
        return SimpleActionEvoAgentRollForward(genome.copyOf(), horizon)
    }

    override fun backPropagate(finalScore: Double) {}
}

class SimpleActionDoNothing(val defaultWait: Int) : SimpleActionPlayerInterface {
    override fun getAction(gameState: ActionAbstractGameState, playerRef: Int) = NoAction(playerRef, defaultWait)
    override fun getPlan(gameState: ActionAbstractGameState, playerRef: Int) = emptyList<Action>()
    override fun reset() = this
    override fun getAgentType() = "SimpleActionDoNothing"
    override fun getForwardModelInterface() = this
    override fun backPropagate(finalScore: Double) {}
    override fun toString() = "SimpleActionDoNothing"
}

object SimpleActionRandom : SimpleActionPlayerInterface {
    override fun getAction(gameState: ActionAbstractGameState, playerRef: Int): Action {
        val allActions = gameState.possibleActions(playerRef, 1)
        return allActions.random()
    }

    override fun getPlan(gameState: ActionAbstractGameState, playerRef: Int) = emptyList<Action>()
    override fun reset() = this
    override fun getAgentType() = "SimpleActionRandom"
    override fun getForwardModelInterface() = this
    override fun backPropagate(finalScore: Double) {}
    override fun toString() = "SimpleActionRandom"
}