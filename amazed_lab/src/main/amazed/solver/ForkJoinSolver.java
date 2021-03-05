package amazed.solver;

import amazed.maze.Maze;
import amazed.maze.Player;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * <code>ForkJoinSolver</code> implements a solver for
 * <code>Maze</code> objects using a fork/join multi-thread
 * depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */


public class ForkJoinSolver extends SequentialSolver {
    int originalStart = start;
    ConcurrentSkipListSet visited = new ConcurrentSkipListSet();

    private ForkJoinSolver(Maze maze, int start, int originalStart, Map<Integer, Integer> predecessor, ConcurrentSkipListSet<Integer> visited) {
        this(maze);
        this.originalStart = originalStart;
        super.start = start;
        super.predecessor = predecessor;
        this.visited = visited;

    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal.
     *
     * @param maze the maze to be searched
     */
    public ForkJoinSolver(Maze maze) {
        super(maze);
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal, forking after a given number of visited
     * nodes.
     *
     * @param maze      the maze to be searched
     * @param forkAfter the number of steps (visited nodes) after
     *                  which a parallel task is forked; if
     *                  <code>forkAfter &lt;= 0</code> the solver never
     *                  forks new tasks
     */
    public ForkJoinSolver(Maze maze, int forkAfter) {
        this(maze);
        this.forkAfter = forkAfter;
    }

    /**
     * Searches for and returns the path, as a list of node
     * identifiers, that goes from the start node to a goal node in
     * the maze. If such a path cannot be found (because there are no
     * goals, or all goals are unreacheable), the method returns
     * <code>null</code>.
     *
     * @return the list of node identifiers from the start node to a
     * goal node in the maze; <code>null</code> if such a path cannot
     * be found.
     */
    @Override
    public List<Integer> compute() {
        return parallelSearch();
    }

    private List<Integer> parallelSearch() {
        int player = maze.newPlayer(start);
        frontier.push(start);

        while (!frontier.empty()) {
            int current = frontier.pop();
            if (maze.hasGoal(current)) {
                // move player to goal
                maze.move(player, current);
                // search finished: reconstruct and return path
                return pathFromTo(originalStart, current);
            }


            int cnt = 0;
            // for every node nb adjacent to current that hasn't been visited

            for (int nb : maze.neighbors(current)) {
                // if nb has not been already visited add to cnt
                if (!this.visited.contains(nb)) {
                    cnt++;
                }
            }

            if (cnt == 0) {
                checkAndMove(current, player);
                return null;
            } else if (cnt == 1) {
                // for every node nb adjacent to current
                checkAndMove(current, player);
                for (int nb : maze.neighbors(current)) {
                    // if nb has not been already visited,
                    // nb can be reached from current (i.e., current is nb's predecessor)
                    if (!this.visited.contains(nb)) {
                        // add nb to the nodes to be processed
                        frontier.push(nb);
                        predecessor.put(nb, current);
                    }
                }
            } else {
                checkAndMove(current, player);
                List<ForkJoinSolver> allPlayers = new ArrayList<>();
                for (int nb : maze.neighbors(current)) {
                    if (!this.visited.contains(nb)) {
                        if(this.visited.add(nb)){
                        predecessor.put(nb, current);
                        ForkJoinSolver forkJoinSolver = new ForkJoinSolver(maze, nb, originalStart, predecessor, this.visited);
                        allPlayers.add(forkJoinSolver);
                        forkJoinSolver.fork();
                    }}
                }

                for (ForkJoinSolver subtask : allPlayers) {
                    List<Integer> path = subtask.join();
                    if (path != null) {
                        return path;
                    }
                }
            }
        }
        return null;
    }

    private void checkAndMove(int current, int player) {
        if (!this.visited.contains(current)) {
            if(this.visited.add(current)){
            maze.move(player, current);
            }
        }
    }
}
