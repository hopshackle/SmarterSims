package groundWar.fatigue

import groundWar.*
import kotlin.math.*

fun newFatigue(rate: Double, currentTime: Int, force: Force): Force {
    val elapsedTime = currentTime - force.timeStamp
    val f = max(force.fatigue + rate * elapsedTime, 0.0)
    return force.copy(fatigue = f, timeStamp = currentTime)
}