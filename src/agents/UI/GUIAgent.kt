package agents.UI

import ggi.*
import groundWar.LandCombatGame
import groundWar.LaunchExpedition
import groundWar.numberToPlayerID
import java.lang.AssertionError
import java.util.*


class GUIAgent(val moveDetails: NextMoveContainer,
               val name: String = "Human"
) : SimpleActionPlayerInterface {

    override fun getAction(gameState: ActionAbstractGameState, playerRef: Int): Action {
        if (gameState is LandCombatGame) {
            return moveDetails.eligibleMoveFor(gameState, playerRef)
        } else
            throw AssertionError("Must have LandCombatGame as state")
    }

    override fun getLastPlan(): List<Action> {
        return emptyList()
    }

    override fun reset(): SimpleActionPlayerInterface {
        moveDetails.reset()
        return this
    }

    override fun getForwardModelInterface(): SimpleActionPlayerInterface {
        // we copy the queue, so any prepared actions will be executed
        return GUIAgent(moveDetails.copy(), this.name)
    }

    override fun backPropagate(finalScore: Double, finalTime: Int) {
        // Nothing to do
    }

    override fun getAgentType(): String {
        return "Human"
    }

}

interface NextMoveContainer {
    fun eligibleMoveFor(gameState: LandCombatGame, playerRef: Int): Action
    fun reset()
    fun copy(): NextMoveContainer
}

class QueueNextMove(val actionQueue: Queue<Triple<Int, Int, Double>> = LinkedList(), val minWait: Int = 10) : NextMoveContainer {
    // This provides a Queue that we can pop actions from

    override fun eligibleMoveFor(gameState: LandCombatGame, playerRef: Int): Action {
        if (!actionQueue.isEmpty()) {
            val inputData = actionQueue.poll()
            val proposedAction = LaunchExpedition(numberToPlayerID(playerRef), inputData.first, inputData.second, inputData.third, gameState.world.params.OODALoop[playerRef])
            if (proposedAction.isValid(gameState)) {
                return proposedAction
            }
        }
        return NoAction(playerRef, minWait)
    }

    override fun reset() {
        actionQueue.clear()
    }

    override fun copy(): NextMoveContainer {
        val qCopy: Queue<Triple<Int, Int, Double>> = LinkedList()
        actionQueue.forEach { i -> qCopy.add(i) }
        return QueueNextMove(qCopy, this.minWait)
    }

}
