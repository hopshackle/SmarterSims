package agents.RHEA

import agents.SimpleActionDoNothing
import ggi.*
import groundWar.LandCombatGame
import groundWar.MakeDecision
import org.json.JSONObject
import utilities.EntityLog

class SimpleActionEvoAgent(val underlyingAgent: SimpleEvoAgent = SimpleEvoAgent(),
                           val opponentModel: SimpleActionPlayerInterface = SimpleActionDoNothing(underlyingAgent.horizon)
) : SimpleActionPlayerInterface {

    var currentPlan = emptyList<Action>()

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

            val genome = underlyingAgent.getActions(gameState, playerRef)
            if (genome.size < intPerAction)
                return NoAction(playerRef, 1000)
            val gene = genome.sliceArray(0 until intPerAction)
            val chosen = gameState.translateGene(playerRef, gene, underlyingAgent.actionFilter)
            currentPlan = convertGenomeToActionList(genome, gameState.copy(), playerRef, underlyingAgent.actionFilter)
            return chosen
        }
        throw AssertionError("Unexpected type of GameState $gameState")
    }

    override fun getForwardModelInterface(): SimpleActionPlayerInterface {
        return SimpleActionEvoAgentRollForward((underlyingAgent.buffer
                ?: intArrayOf()).copyOf(), underlyingAgent.horizon, underlyingAgent.actionFilter)
    }

    override fun getLastPlan(): List<Action> {
        return currentPlan
    }

    override fun backPropagate(finalScore: Double, finalTime: Int) {}

}

fun convertGenomeToActionList(genome: IntArray?, gameState: AbstractGameState, playerRef: Int, actionFilter: String): List<Action> {
    val intPerAction = gameState.codonsPerAction()
    if (genome == null || genome.isEmpty()) return listOf()
    if (gameState is ActionAbstractGameState) {
        gameState.registerAgent(0, SimpleActionDoNothing(1000))
        gameState.registerAgent(1, SimpleActionDoNothing(1000))
        val retValue = (0 until (genome.size / intPerAction)).map { i ->
            val gene = genome.sliceArray(i * intPerAction until (i + 1) * intPerAction)
            val action = gameState.translateGene(playerRef, gene, actionFilter)
            val finishTime = action.nextDecisionPoint(playerRef, gameState)
            action.apply(gameState)
            gameState.next(finishTime.first - gameState.nTicks())
            action
        }
        return retValue
    }
    return emptyList()
}

fun elapsedLengthOfPlan(genome: IntArray, gameState: AbstractGameState, playerRef: Int, actionFilter: String): Int {
    val intPerAction = gameState.codonsPerAction()
    val startingTime = gameState.nTicks()
    if (genome.size < intPerAction) return 0
    if (gameState is LandCombatGame) {
        gameState.eventQueue.removeIf { it.action is MakeDecision }
        var i = 0
        do {
            val gene = genome.sliceArray(i * intPerAction until (i + 1) * intPerAction)
            val action = gameState.translateGene(playerRef, gene, actionFilter)
            val nextDecisionTime = action.nextDecisionPoint(playerRef, gameState)
            gameState.planEvent(gameState.nTicks(), action)
            gameState.next(nextDecisionTime.first - gameState.nTicks())
            i++
        } while (i < genome.size / intPerAction)
    }
    return gameState.nTicks() - startingTime
}

/*
Will take actions using a specified genome...until the sequence runs out
 */
class SimpleActionEvoAgentRollForward(var genome: IntArray, val horizon: Int = 1, val actionFilter: String)  : SimpleActionPlayerInterface {

    val scoreByTime = mutableListOf<Pair<Int, Double>>()

    override fun getAction(gameState: ActionAbstractGameState, playerRef: Int): Action {
        val intPerAction = gameState.codonsPerAction()
        scoreByTime.add(Pair(gameState.nTicks(), gameState.score(playerRef)))
        if (genome.size >= intPerAction) {
            val gene = genome.sliceArray(0 until intPerAction)
            genome = genome.sliceArray(intPerAction until genome.size)
            val retValue = gameState.translateGene(playerRef, gene, actionFilter)
            if (retValue is InterruptibleWait) {
                return NoAction(retValue.playerRef, retValue.waitTime)
            }
            return retValue
        } else {
            return NoAction(playerRef, horizon)
        }
    }

    override fun getLastPlan(): List<Action> {
        TODO("Not implemented")
    }

    override fun reset() = this

    override fun getAgentType() = "SimpleActionEvoAgentRollForward"

    override fun getForwardModelInterface(): SimpleActionPlayerInterface {
        return SimpleActionEvoAgentRollForward(genome.copyOf(), horizon, actionFilter)
    }

    override fun backPropagate(finalScore: Double, finalTime: Int) {}
}
