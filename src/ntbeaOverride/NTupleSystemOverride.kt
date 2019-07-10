package ntbeaOverride

import evodef.*
import ntbea.NTupleSystem

class NTupleSystemOverride : NTupleSystem() {

    override fun setSearchSpace(searchSpace: SearchSpace?): BanditLandscapeModel {
        if (searchSpace == this.searchSpace) return this
        return super.setSearchSpace(searchSpace)
    }
}