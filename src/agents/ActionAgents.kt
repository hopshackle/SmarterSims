package agents

import ggi.*

class SimpleActionDoNothing(val defaultWait: Int) : SimpleActionPlayerInterface {
    override fun getAction(gameState: ActionAbstractGameState, playerRef: Int) = NoAction(playerRef, defaultWait)
    override fun getLastPlan() = emptyList<Action>()
    override fun reset() = this
    override fun getAgentType() = "SimpleActionDoNothing"
    override fun getForwardModelInterface() = this
    override fun backPropagate(finalScore: Double) {}
    override fun toString() = "SimpleActionDoNothing"
}

object SimpleActionRandom : SimpleActionPlayerInterface {
    override fun getAction(gameState: ActionAbstractGameState, playerRef: Int): Action {
        val allActions = gameState.possibleActions(playerRef, 10)
        return allActions.random()
    }

    override fun getLastPlan() = emptyList<Action>()
    override fun reset() = this
    override fun getAgentType() = "SimpleActionRandom"
    override fun getForwardModelInterface() = this
    override fun backPropagate(finalScore: Double) {}
    override fun toString() = "SimpleActionRandom"
}