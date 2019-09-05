package groundWar.fatigue

import groundWar.*
import kotlin.math.*

interface FatigueModel {
    abstract fun rest(currentTime: Int, force: Force): Force
    abstract fun move(currentTime: Int, force: Force): Force
}

class LinearFatigue(val rate: Double) : FatigueModel {
    override fun move(currentTime: Int, force: Force): Force {
        return helper(1.0, currentTime, force)
    }

    override fun rest(currentTime: Int, force: Force): Force {
        return helper(-1.0, currentTime, force)
    }

    private fun helper(factor: Double, currentTime: Int, force: Force): Force {
        val elapsedTime = currentTime - force.timeStamp
        val f = max(force.fatigue + factor * rate * elapsedTime, 0.0)
        return force.copy(fatigue = f, timeStamp = currentTime)
    }
}