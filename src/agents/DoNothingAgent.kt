package agents

import ggi.AbstractGameState
import ggi.SimplePlayerInterface


data class DoNothingAgent (var action: Int = 0) : SimplePlayerInterface {
    override fun getAgentType(): String {
        return "DoNothingAgent"
    }

    override fun getAction(gameState: AbstractGameState, playerRef: Int): Int {
        // return zero without knowing what this will do
        return action
    }

    override fun reset(): SimplePlayerInterface {
        return this
    }

}
