package ggi

data class NoAction(val playerRef: Int, val waitTime: Int) : Action {
    override val player = playerRef
    override fun apply(state: ActionAbstractGameState) {}
    // Do absolutely nothing

    override fun nextDecisionPoint(player: Int, state: ActionAbstractGameState) : Pair<Int, Int> {
        val nextTime = state.nTicks() + waitTime
        return Pair(nextTime, nextTime)
        // No interrupt possible on a NoAction
    }

    // only visible to planning player
    override fun visibleTo(player: Int, state: ActionAbstractGameState) = player == playerRef
}

data class InterruptibleWait(val playerRef: Int, val waitTime: Int) : Action {
    override val player = playerRef
    override fun apply(state: ActionAbstractGameState) {}
    // Do absolutely nothing

    override fun nextDecisionPoint(player: Int, state: ActionAbstractGameState) : Pair<Int, Int> {
        val nextTime = state.nTicks() + waitTime
        return Pair(nextTime, state.nTicks())
        // We can always interrupt a Wait
    }

    // only visible to planning player
    override fun visibleTo(player: Int, state: ActionAbstractGameState) = player == playerRef
}



interface ActionAbstractGameState : AbstractGameState {

    // we do not use one method of the super interface
    override fun next(actions: IntArray): AbstractGameState {
        throw AssertionError("Should use next() with List<Action>")
    }

    fun next(forwardTicks: Int): ActionAbstractGameState

    /** this method is shared with AbstractGameState, but defines the number of different
    values that each location (or base) on the genome can hold, so it is not strictly the number of 'actions'
    any more. TODO: Refactor the inheritance set up to remove this opportunity for misunderstanding
     **/
    override fun nActions(): Int

    /**
     * Unlike AbstractGameState, we now allow different players to have different score functions
     * so that things are not necessarily purely zero-sum
     */
    fun score(player: Int): Double

    override fun score(): Double {
        throw AssertionError("Please specify which player's perspective you mean")
    }

    fun playerCount(): Int

    fun possibleActions(player: Int, max: Int = Int.MAX_VALUE, filterType: String = ""): List<Action>

    fun translateGene(player: Int, gene: IntArray, filterType: String = ""): Action

    fun registerAgent(player: Int, agent: SimpleActionPlayerInterface)

    fun getAgent(player: Int): SimpleActionPlayerInterface

    fun planEvent(time: Int, action: Action)

    fun sanityChecks() {
        // default is nothing
    }
}

interface Action {
    val player: Int
    fun apply(state: ActionAbstractGameState)
    fun visibleTo(player: Int, state: ActionAbstractGameState): Boolean
    fun nextDecisionPoint(player: Int, state: ActionAbstractGameState): Pair<Int, Int> = Pair(-1, -1)
}

interface StateSummarizer {
    operator fun invoke(state: ActionAbstractGameState): String
}
