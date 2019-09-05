package groundWar

import ggi.Action
import ggi.ActionAbstractGameState
import agents.SimpleActionDoNothing
import ggi.SimpleActionPlayerInterface
import java.util.*
import kotlin.collections.HashMap


class EventQueue(val eventQueue: Queue<Event> = PriorityQueue()) : Queue<Event> by eventQueue {

    private val playerAgentMap = HashMap<Int, SimpleActionPlayerInterface>()
    val history = mutableListOf<Event>()

    var currentTime = 0
        set(time) {
            if (eventQueue.isNotEmpty() && eventQueue.peek().tick < time) {
                throw AssertionError("Cannot set time to later than an event in the queue")
            }
            field = time
        }

    fun registerAgent(player: Int, agent: SimpleActionPlayerInterface, currentTime: Int) {
        if (agent is SimpleActionDoNothing) {
            eventQueue.removeIf { e -> e.action is MakeDecision && e.action.playerRef == player }
        } else {
            playerAgentMap[player] = agent
            if (eventQueue.none { e -> e.action is MakeDecision && e.action.playerRef == player }) {
                eventQueue.add(Event(currentTime, MakeDecision(player, currentTime)))
            }
        }
    }

    fun getAgent(player: Int) = playerAgentMap[player] ?: SimpleActionDoNothing(1000)

    inline fun addAll(oldQueue: EventQueue, filterLogic: (Event) -> Boolean) {
        eventQueue.addAll(oldQueue.eventQueue.filter(filterLogic))
    }

    fun next(forwardTicks: Int, state: ActionAbstractGameState) {
        val timeToFinish = currentTime + forwardTicks
        do {
            // we may have multiple events triggering in the same tick
            val event = eventQueue.peek()
            if (event != null && event.tick < timeToFinish) {
                // the time has come to trigger it
                eventQueue.poll()
                if (event.tick < currentTime) {
                    throw AssertionError("Should not have an event on the queue that should have been processed in the past")
                }
                currentTime = event.tick
                val visibleTo = playerAgentMap.keys
                        .filterNot { a -> a == event.action.player }
                        .filter { a -> event.action.visibleTo(a, state) }
                event.action.apply(state)
                history.add(event)
                //           println("Triggered event: ${event} in Game $this")
                visibleTo.forEach {
                    interruptWait(it)
                }
            } else {
                currentTime = timeToFinish
            }
        } while (timeToFinish > currentTime && !state.isTerminal())
        state.sanityChecks()
    }

    private fun interruptWait(agent: Int) {
        val decision = this.firstOrNull { it.action.player == agent && it.action is MakeDecision }
        if (decision != null) {
            val md = decision.action as MakeDecision
            val earliestDecisionTime = if (md.minActivationTime > currentTime + 1) md.minActivationTime else currentTime + 1
            if (earliestDecisionTime < decision.tick) {
                // move MakeDecision
                this.remove(decision)
                this.add(Event(earliestDecisionTime, MakeDecision(agent, earliestDecisionTime)))
            }
        }
    }
}

data class MakeDecision(val playerRef: Int, val minActivationTime: Int) : Action {
    override val player = playerRef

    override fun apply(state: ActionAbstractGameState) {
        val agent = state.getAgent(playerRef)
        val perceivedState = state.copy(playerRef) as ActionAbstractGameState
        val action = agent.getAction(perceivedState, playerRef)
        state.planEvent(state.nTicks(), action)
        val (nextDecisionTime, earliestDecisionTime) = action.nextDecisionPoint(playerRef, state)
        when {
            nextDecisionTime == -1 -> throw AssertionError("No action from MakeDecision should have -1 as next point")
            nextDecisionTime <= state.nTicks() ->
                throw AssertionError("Next Decision point must be in the future")
            else -> state.planEvent(nextDecisionTime, MakeDecision(playerRef, earliestDecisionTime))
        }
    }

    // only visible to planning player
    override fun visibleTo(player: Int, state: ActionAbstractGameState) = player == playerRef
}