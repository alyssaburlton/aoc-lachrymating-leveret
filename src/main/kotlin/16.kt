data class Valve(val room: String, val flowRate: Int)
data class Person(val valve: Valve, val visitedValves: Set<Valve>)
data class VolcanoState(
    val me: Person,
    val elephant: Person?,
    val releasedValves: Set<Pair<Int, Valve>>
)

data class VolcanoStateHash(val personStates: Set<Valve?>, val released: Set<Pair<Int, Valve>>)

private const val NAIVE_SEARCH_MAX_TO_KEEP = 100

class Day16(mode: SolverMode) : Solver(16, mode) {
    private val valvesMap = readStringList(filename).map(::parseValves).let(::getRoutedValvesMap)
    private val releasableValves = valvesMap.keys.filter { it.flowRate > 0 }
    private val startingValve = valvesMap.keys.first { it.room == "AA" }

    override fun partA() =
        runSimulation(initialState(false), 29)

    override fun partB() =
        runSimulation(initialState(true), 25)

    private fun initialState(withElephant: Boolean) =
        VolcanoState(initialPerson(), if (withElephant) initialPerson() else null, emptySet())

    private fun initialPerson() = Person(startingValve, emptySet())

    private fun canReleaseValve(state: VolcanoState, valve: Valve) =
        valve.flowRate > 0 && !state.releasedValves.any { it.second == valve }

    /**
     * To leave a valve, there must be something higher scoring enough to be worth it.
     *
     * Compare the path for:
     *
     *  - Releasing ours now, then going to the highest other valve and releasing it
     *  - Going to the highest other valve, releasing it then returning to ours to release.
     *
     *  Look at direct neighbours, and then assume everything else is 2 steps away (if they're further we'd be more likely to want to release)
     */
    private fun mustReleaseValve(state: VolcanoState, valve: Valve, timeRemaining: Int) =
        mustReleaseForNeighbours(state, valve, timeRemaining) &&
                mustReleaseForFurtherValves(state, valve, timeRemaining)

    private fun mustReleaseForNeighbours(state: VolcanoState, valve: Valve, timeRemaining: Int): Boolean {
        val nextBestValve = state.releasableNeighbours(valve).maxOfOrNull { it.flowRate } ?: return true

        val releaseNowScore = (timeRemaining * valve.flowRate) + ((timeRemaining - 2) * nextBestValve)
        val releaseLaterScore = ((timeRemaining - 1) * nextBestValve) + ((timeRemaining - 3) * valve.flowRate)

        return releaseNowScore >= releaseLaterScore
    }

    private fun mustReleaseForFurtherValves(state: VolcanoState, valve: Valve, timeRemaining: Int): Boolean {
        val nonNeighbours = releasableValves(state) - valve - state.releasableNeighbours(valve)
        val nextBestValve = nonNeighbours.maxOfOrNull { it.flowRate } ?: return true

        val releaseNowScore = (timeRemaining * valve.flowRate) + ((timeRemaining - 3) * nextBestValve)
        val releaseLaterScore = ((timeRemaining - 2) * nextBestValve) + ((timeRemaining - 5) * valve.flowRate)

        return releaseNowScore >= releaseLaterScore
    }

    private fun releasableValves(state: VolcanoState) =
        releasableValves.filter { canReleaseValve(state, it) }

    private fun VolcanoState.releasableNeighbours(valve: Valve) =
        valvesMap.getValue(valve).filter { canReleaseValve(this, it) }.toSet()

    private fun countTotalReleased(state: VolcanoState) = state.releasedValves.sumOf {
        it.first * it.second.flowRate
    }

    private fun canSurpassMax(timeRemaining: Int, state: VolcanoState, currentMax: Int) =
        getTheoreticalMax(timeRemaining, state) > currentMax

    private fun getTheoreticalMax(timeRemaining: Int, currentState: VolcanoState): Int {
        val currentScore = countTotalReleased(currentState)

        val myReleaseTimes = getOptimisticReleaseTimes(timeRemaining, currentState, currentState.me.valve)
        val elephantReleaseTimes = getOptimisticReleaseTimes(timeRemaining, currentState, currentState.elephant?.valve)

        val times = (myReleaseTimes + elephantReleaseTimes).sortedDescending()

        return currentScore + releasableValves(currentState)
            .sortedByDescending { it.flowRate }
            .zip(times)
            .sumOf { it.first.flowRate * it.second }
    }

    private fun getOptimisticReleaseTimes(
        timeRemaining: Int,
        currentState: VolcanoState,
        currentValve: Valve?
    ): List<Int> {
        currentValve ?: return emptyList()

        val canReleaseNow = canReleaseValve(currentState, currentValve)
        val releasableNeighbours = currentState.releasableNeighbours(currentValve)

        val firstRelease =
            if (canReleaseNow) timeRemaining else if (releasableNeighbours.isNotEmpty()) timeRemaining - 1 else timeRemaining - 2
        return (firstRelease downTo 0 step 2).toList()
    }

    private fun findNaiveMaximum(initialState: VolcanoState, startingTime: Int) =
        exploreRecursively(listOf(initialState), 0, startingTime) { states, _, _ ->
            states.sortedByDescending(::countTotalReleased).take(NAIVE_SEARCH_MAX_TO_KEEP)
        }

    private fun runSimulation(startingState: VolcanoState, startingTime: Int) =
        exploreRecursively(
            listOf(startingState),
            findNaiveMaximum(startingState, startingTime),
            startingTime
        ) { states, highScore, timeRemaining ->
            states.filter { canSurpassMax(timeRemaining, it, highScore) }
        }

    private tailrec fun exploreRecursively(
        states: List<VolcanoState>,
        highScore: Int,
        timeRemaining: Int,
        stateReducer: (List<VolcanoState>, Int, Int) -> List<VolcanoState>,
    ): Int {
        if (timeRemaining == 0 || states.isEmpty()) {
            return highScore
        }

        val newStates = takeMoves(timeRemaining, states)
        val newHighScore = maxOf(highScore, newStates.maxOfOrNull(::countTotalReleased) ?: 0)
        return exploreRecursively(
            stateReducer(newStates, newHighScore, timeRemaining),
            newHighScore,
            timeRemaining - 1,
            stateReducer
        )
    }

    private fun VolcanoState.toDistinctHash() =
        VolcanoStateHash(setOf(me.valve, elephant?.valve), releasedValves)

    private fun takeMoves(timeRemaining: Int, states: List<VolcanoState>) =
        states.flatMap { takeAllPossibleMoves(timeRemaining, it) }.distinctBy { it.toDistinctHash() }

    private fun releasedAllValves(state: VolcanoState) = state.releasedValves.size == releasableValves.size

    private fun takeAllPossibleMoves(timeRemaining: Int, state: VolcanoState): List<VolcanoState> {
        if (releasedAllValves(state)) {
            return emptyList()
        }

        val myMoves = getValidMoves(state, timeRemaining, state.me, ::updateMyState)
        return state.elephant?.let { elephant ->
            myMoves.flatMap {
                getValidMoves(
                    it,
                    timeRemaining,
                    elephant,
                    ::updateElephantState
                )
            }
        } ?: myMoves
    }

    private fun getValidMoves(
        state: VolcanoState,
        timeRemaining: Int,
        currentPerson: Person,
        personUpdater: (VolcanoState, Valve) -> VolcanoState
    ): List<VolcanoState> {
        val movements = valvesMap
            .getValue(currentPerson.valve)
            .filterNot(currentPerson.visitedValves::contains)
            .map { newValve -> personUpdater(state, newValve) }

        return if (!canReleaseValve(state, currentPerson.valve)) {
            movements
        } else {
            val newReleased = state.releasedValves.plus(Pair(timeRemaining, currentPerson.valve))
            val releasedState = personUpdater(state.copy(releasedValves = newReleased), currentPerson.valve)

            if (mustReleaseValve(state, currentPerson.valve, timeRemaining)) {
                listOf(releasedState)
            } else {
                movements + releasedState
            }
        }
    }

    private fun updateMyState(state: VolcanoState, myValve: Valve): VolcanoState {
        val newVisited = if (myValve == state.me.valve) emptySet() else state.me.visitedValves + state.me.valve
        return state.copy(me = Person(myValve, newVisited))
    }

    private fun updateElephantState(state: VolcanoState, elephantValve: Valve): VolcanoState {
        val newVisited =
            if (elephantValve == state.elephant!!.valve) emptySet() else state.elephant.visitedValves + state.elephant.valve
        return state.copy(elephant = Person(elephantValve, newVisited))
    }

    private fun parseValves(line: String): Pair<Valve, List<String>> {
        val match =
            Regex("Valve ([A-Z]+) has flow rate=(\\d+); tunnels? leads? to valves? ([A-Z ,]+)").find(line)!!

        val (room, flowRate, others) = match.destructured.toList()
        val valve = Valve(room, flowRate.toInt())
        val otherValves = others.split(", ")
        return valve to otherValves
    }

    private fun getRoutedValvesMap(parsed: List<Pair<Valve, List<String>>>): Map<Valve, List<Valve>> {
        val pairs = parsed.map { (valve, others) ->
            val otherValves = others.map { otherRoom -> parsed.first { it.first.room == otherRoom }.first }
            valve to otherValves
        }

        return pairs.toMap()
    }
}