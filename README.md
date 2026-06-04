# ARSW - Laboratory #2

## Concurrent Snake Race (Java 21 - Virtual Threads)

**Author:** Eduardo Rico Duarte 

**Course:** Software Architectures (ARSW)

**Escuela Colombiana de Ingeniería Julio Garavito**

---

# Introduction

The objective of this laboratory was to analyze and correct different concurrency-related issues in Java. The work was divided into two parts:

1. Implementation of pause and resume mechanisms using `wait()` and `notifyAll()` in the PrimeFinder exercise.
2. Analysis and improvement of the Snake Race game by identifying race conditions, critical sections, and issues associated with the concurrent execution of multiple snakes.

---

# Part I - Wait / Notify in PrimeFinder

## Objective

Modify the PrimeFinder program so that every certain amount of time:

* All worker threads are paused.
* The total number of prime numbers found is displayed.
* The program waits for user input.
* All worker threads resume execution using monitor-based synchronization mechanisms.

## Synchronization Design

A single shared monitor was used by all worker threads.

When the control thread determines that a pause must occur:

1. It changes a shared flag (`paused = true`).
2. Each worker thread checks this condition before continuing its execution.
3. If the flag indicates a pause, the thread enters the waiting state using `wait()`.
4. When the user presses ENTER, the control thread changes the flag to `false` and executes `notifyAll()` on the same monitor.

This approach eliminates busy waiting because threads remain blocked without consuming CPU resources until they receive a resume signal.

## Preventing Lost Wakeups

To avoid lost notifications, the recommended monitor pattern was used:

```java
synchronized (pauseLock) {
    while (paused) {
        pauseLock.wait();
    }
}
```

The condition is verified inside a `while` loop, ensuring that a thread only continues when the condition has actually changed.

## Result

Threads can be paused and resumed correctly without busy waiting while maintaining safe synchronization through Java monitors.

---

# Part II - Snake Race

## 1. Concurrency Analysis

The game uses concurrency to provide autonomy to each snake.

Inside the `SnakeApp` class, a `SnakeRunner` object is created for each snake, and each one runs inside its own Java 21 virtual thread.

This allows every snake to move independently and concurrently with respect to the others.

### Potential Race Conditions Identified

A potential race condition was identified in the `Snake` class because:

* `SnakeRunner` threads continuously modify the snake's position.
* The graphical user interface simultaneously reads the same information to render the snake.

Without proper protection, inconsistent reads of the snake's body could occur.

### Potentially Unsafe Collections

The most sensitive data structure identified was:

```java
ArrayDeque<Position>
```

which is used to store the body of each snake.

This collection is not thread-safe and therefore requires synchronization when accessed by multiple threads.

The internal collections inside `Board` (`HashSet` and `HashMap`) were already protected through synchronization and defensive copies.

### Busy Waiting

No busy-waiting loops were found in the game.

Snake execution is controlled through:

```java
Thread.sleep(...)
```

which prevents unnecessary CPU consumption while threads are waiting.

---

## 2. Minimal Corrections and Critical Sections

### Problem Identified

The main concurrency issue was located in the `Snake` class.

The snake body could be read by the graphical interface while simultaneously being modified by the thread responsible for moving it.

### Implemented Solution

Only the methods that access or modify the internal state of a snake were synchronized:

```java
head()
snapshot()
advance()
turn()
length()
```

Example:

```java
public synchronized Deque<Position> snapshot() {
    return new ArrayDeque<>(body);
}
```

### Risk Addressed

Synchronization prevents:

* Inconsistent reads.
* Intermediate body states.
* Potential issues caused by concurrent modifications.

### Justification for Minimal Scope

The entire game was not locked.

Only the internal state of each snake was protected, preserving the highest possible degree of parallelism.

---

## Additional Functionality Implemented

To satisfy the requirements of the following sections, it was necessary to introduce a new feature: snake death.

In the original version of the game, when a snake collided with an obstacle it simply changed direction and continued moving.

The behavior was modified so that a snake:

* Dies when colliding with an obstacle.
* Dies when colliding with another snake.
* Disappears from the board after death.

To support this functionality, the following attributes were added:

```java
private volatile boolean alive;
private volatile long deathTimeMs;
```

along with the method:

```java
public synchronized void die()
```

which records the exact moment of death.

---
## 3. Safe Execution Control (UI)

### State Machine for the User Interface

The original button was replaced with a state-driven workflow controlled by the variable `uiState`.

| State | Button Text | Action              |
| ----- | ----------- | ------------------- |
| 0     | Start       | Starts the game     |
| 1     | Pause       | Pauses the game     |
| 2     | Resume      | Continues execution |

This prevents invalid transitions and simplifies interface control.

### Statistics Displayed During Pause

When the game is paused, the following information is displayed:

* The longest living snake.
* The worst snake, defined as the first snake that died.

To make identification easier, each snake was assigned a fixed color:

* Green
* Blue
* Magenta
* Gold
* Red

The longest living snake is calculated using:

```java id="7b5f2r"
Comparator.comparingInt(Snake::length)
```

The first snake to die is calculated using:

```java id="a1d4rt"
Comparator.comparingLong(Snake::deathTimeMs)
```

### Visual Consistency (No Tearing)

One of the requirements was to ensure that the information displayed when pausing corresponds exactly to the state visible on the screen.

To achieve this:

1. `GameClock` uses an `AtomicReference<GameState>`.
2. No new repaint operations are scheduled while the game is in the `PAUSED` state.
3. `SwingUtilities.invokeLater()` is used to execute the final repaint and then calculate the statistics.

```java id="p9x4cs"
clock.pause();

SwingUtilities.invokeLater(() -> {
    gamePanel.repaint();
    updateStatsLabel();
});
```

This guarantees that the frozen frame and the displayed statistics belong to the same consistent game state.

---

## 4. Robustness Under Load

Tests were executed using a high number of snakes:

```bash id="m4j5qd"
mvn -q -DskipTests exec:java -Dsnakes=20
```

### ConcurrentModificationException

The getter methods of `Board` return defensive copies:

```java id="v8s6pl"
return new HashSet<>(mice);
return new HashMap<>(teleports);
```

The graphical interface never iterates directly over shared collections.

**Result:**

* No `ConcurrentModificationException` occurred.

### Inconsistent Reads

All accesses to the internal state of a snake are synchronized.

**Result:**

* No visual inconsistencies were observed.

### Deadlocks

The lock acquisition order is always consistent:

```text id="8nq3mz"
Board -> Snake
```

The following order never occurs:

```text id="m2t8ra"
Snake -> Board
```

**Result:**

* No deadlocks were detected.

### Turbo and Teleporters

The movement logic is protected inside:

```java id="z5w1cx"
public synchronized MoveResult step(...)
```

Only one snake can modify the board at a time.

**Result:**

* No race conditions involving turbo items or teleporters were observed.

### Item Generation on Snake Bodies

An additional issue was identified when running the game with a large number of snakes.

The original implementation could generate mice, obstacles, or turbo items on cells already occupied by snake bodies.

The method `randomEmpty()` was modified to exclude all positions occupied by living snakes.

**Result:**

* New items always appear on valid free cells.

---

# Results

After the implemented modifications, the game:

* Executes multiple snakes concurrently using Java 21 Virtual Threads.
* Does not present detected race conditions.
* Does not produce `ConcurrentModificationException`.
* Does not generate deadlocks.
* Allows starting, pausing, and resuming consistently.
* Displays correct statistics during pause.
* Supports high loads (`N >= 20`) without concurrency failures.

---

# Conclusions

* Synchronization should be applied only to the strictly necessary critical sections in order to preserve parallelism.
* Synchronizing the methods of the `Snake` class protected the shared state without introducing excessive locking.
* Defensive copies proved to be an effective strategy for avoiding concurrent modification problems during rendering.
* Proper coordination between the game clock and the graphical interface made it possible to achieve a visually consistent pause mechanism.
* Testing with a high number of snakes confirmed the robustness and stability of the implemented solution under concurrent execution.

---

# References

1. Benavides Navarro, L. D., & Gualtero Martínez, R. H. (2024). *Concurrency and Threads in Java and Go* [Course slides].

2. OpenAI. (2026). *ChatGPT (GPT-5.5 version) [Large Language Model]*. https://chatgpt.com/ (Used primarily as a support tool).

3. Oracle. (2024). *Java tutorials: Concurrency*. Oracle Documentation. https://docs.oracle.com/javase/tutorial/essential/concurrency/

4. OpenJDK. (2024). *Project Loom: Virtual threads and structured concurrency*. OpenJDK. https://openjdk.org/projects/loom/


