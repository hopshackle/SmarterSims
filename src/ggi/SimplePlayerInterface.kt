package ggi

interface SimplePlayerInterface {
    fun getAction(gameState: AbstractGameState, playerRef: Int): Int
    fun reset(): SimplePlayerInterface
    fun getAgentType(): String
}

interface SimpleActionPlayerInterface: SimplePlayerInterface {
    override fun getAction(gameState: AbstractGameState, playerRef: Int): Int {
        throw AssertionError("Should not use")
    }
    fun getAction(gameState: ActionAbstractGameState, playerRef: Int): Action
    fun getLastPlan(): List<Action>
    override fun reset(): SimpleActionPlayerInterface
    fun getForwardModelInterface(): SimpleActionPlayerInterface
    fun backPropagate(finalScore: Double)
}
