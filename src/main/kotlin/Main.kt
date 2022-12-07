val solvers = listOf(
    Day1(),
    Day2(),
    Day3(),
    Day4(),
    Day5(),
    Day6(),
    Day7()
)

fun main() {
    solvers.forEach { solver ->
        println("${solver.day}A: ${solver.partA()}")
        println("${solver.day}B: ${solver.partB()}")
        println()
    }
}