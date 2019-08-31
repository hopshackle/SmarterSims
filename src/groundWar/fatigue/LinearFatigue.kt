package groundWar.fatigue

import groundWar.*
import kotlin.math.*

fun newFatigue(rate: Double, elapsedTime: Long, currentTime: Long, force: Force): Force {
    val f = max(force.fatigue - rate * elapsedTime, 0.0)
    return force.copy(fatigue = f, timeStamp = currentTime)
}