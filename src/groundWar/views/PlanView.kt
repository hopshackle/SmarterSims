package groundWar.views

import ggi.*
import agents.RHEA.*
import groundWar.LandCombatGame
import groundWar.numberToPlayerID
import javax.swing.*

class PlanView(val agent: SimpleActionPlayerInterface, val game: LandCombatGame, val playerRef: Int) : JTextArea(10, 60) {

    fun refresh() {
        val actionList = agent.getLastPlan()
        //  text = "Agent: ${numberToPlayerID(playerRef)}\t${agent.getAgentType()}\n${actionList.joinToString("\n")}\n"
        text = "Agent: ${numberToPlayerID(playerRef)}\t${agent.getAgentType().take(40)}\n"
        append("${actionList.joinToString("\n")}\n")

    }
}