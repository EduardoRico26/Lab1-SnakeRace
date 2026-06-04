# ARSW - Laboratorio #1

## Snake Race Concurrente (Java 21 - Virtual Threads)

**Autor:** Eduardo Rico Duarte 

**Curso:** Arquitecturas de Software (ARSW)

**Escuela Colombiana de Ingeniería Julio Garavito**

---

# Introducción

El objetivo de este laboratorio fue analizar y corregir diferentes problemas relacionados con concurrencia en Java. Para ello se trabajó en dos partes:

1. Implementación de mecanismos de pausa y reanudación utilizando `wait()` y `notifyAll()` en el ejercicio PrimeFinder.
2. Análisis y mejora del juego Snake Race, identificando posibles condiciones de carrera, regiones críticas y problemas asociados a la ejecución concurrente de múltiples serpientes.

---

# Parte I - Wait / Notify en PrimeFinder

## Objetivo

Modificar el programa PrimeFinder para que cada cierto tiempo:

* Se pausen todos los hilos trabajadores.
* Se muestre la cantidad de números primos encontrados.
* El programa espere la entrada del usuario.
* Se reanuden todos los hilos utilizando mecanismos de sincronización basados en monitores.

## Diseño de sincronización

Se utilizó un único monitor compartido entre todos los hilos trabajadores.

Cuando el hilo de control determina que debe realizar una pausa:

1. Cambia una bandera compartida (`paused=true`).
2. Cada hilo trabajador verifica dicha condición antes de continuar procesando números.
3. Si la bandera indica pausa, el hilo entra en estado de espera mediante `wait()`.
4. Cuando el usuario presiona ENTER, el hilo de control cambia la bandera a `false` y ejecuta `notifyAll()` sobre el mismo monitor.

De esta manera no existe espera activa (busy waiting), ya que los hilos permanecen bloqueados sin consumir CPU hasta recibir la señal de reanudación.

## Prevención de Lost Wakeups

Para evitar pérdidas de notificaciones se utilizó el patrón recomendado:

```java
synchronized (pauseLock) {
    while (paused) {
        pauseLock.wait();
    }
}
```

La condición se verifica mediante un ciclo `while`, garantizando que un hilo solo continúe cuando la condición realmente haya cambiado.

## Resultado

Los hilos pueden pausarse y reanudarse correctamente sin utilizar espera activa y manteniendo sincronización segura mediante monitores de Java.

---

# Parte II - Snake Race Concurrente

## 1. Análisis de concurrencia

El juego utiliza concurrencia para otorgar autonomía a cada serpiente.

En la clase `SnakeApp` se crea un objeto `SnakeRunner` por cada serpiente y cada uno se ejecuta en un hilo virtual independiente de Java 21.

Gracias a esto, cada serpiente se mueve de forma concurrente respecto a las demás.

### Posibles condiciones de carrera identificadas

Se identificó una posible condición de carrera en la clase `Snake`, debido a que:

* Los hilos `SnakeRunner` modifican constantemente la posición de la serpiente.
* El hilo de la interfaz gráfica consulta simultáneamente dicha información para dibujarla.

Sin protección adecuada podían producirse lecturas inconsistentes del cuerpo de la serpiente.

### Colecciones potencialmente inseguras

La estructura más sensible encontrada fue:

```java
ArrayDeque<Position>
```

utilizada para almacenar el cuerpo de cada serpiente.

Esta colección no es thread-safe y requiere sincronización cuando es utilizada por varios hilos.

Las colecciones internas de `Board` (`HashSet` y `HashMap`) ya estaban protegidas mediante sincronización y además retornaban copias defensivas.

### Espera activa

No se encontraron ciclos de espera activa dentro del juego.

La ejecución de las serpientes se controla mediante:

```java
Thread.sleep(...)
```

por lo que los hilos no consumen CPU innecesariamente mientras esperan.

---

## 2. Correcciones mínimas y regiones críticas

### Problema identificado

El principal problema de concurrencia se encontraba en la clase `Snake`.

El cuerpo de la serpiente podía ser leído por la interfaz gráfica al mismo tiempo que era modificado por el hilo encargado de moverla.

### Solución implementada

Se sincronizaron únicamente los métodos que acceden o modifican el estado interno de la serpiente:

```java
head()
snapshot()
advance()
turn()
length()
```

Ejemplo:

```java
public synchronized Deque<Position> snapshot() {
    return new ArrayDeque<>(body);
}
```

### Riesgo resuelto

La sincronización evita:

* Lecturas inconsistentes.
* Estados intermedios del cuerpo.
* Posibles excepciones por modificaciones concurrentes.

### Justificación del alcance mínimo

No se bloquearon componentes completos del juego.

Únicamente se protegió el estado interno de cada serpiente, manteniendo el nivel de paralelismo más alto posible.

---

## Funcionalidad adicional implementada

Para poder cumplir los requisitos de los puntos posteriores fue necesario incorporar una nueva funcionalidad: la muerte de las serpientes.

En la versión original, una serpiente que chocaba contra un obstáculo simplemente cambiaba de dirección.

Se modificó el comportamiento para que:

* Muera al colisionar contra un obstáculo.
* Muera al colisionar contra otra serpiente.
* Desaparezca visualmente del tablero.

Para ello se agregaron los atributos:

```java
private volatile boolean alive;
private volatile long deathTimeMs;
```

y el método:

```java
public synchronized void die()
```

que registra el instante exacto de la muerte.

---

## 3. Control de ejecución seguro (UI)

### Máquina de estados de la interfaz

El botón original fue reemplazado por un flujo de estados controlado mediante `uiState`.

| Estado | Botón    | Acción                |
| ------ | -------- | --------------------- |
| 0      | Iniciar  | Inicia el juego       |
| 1      | Pausar   | Pausa el juego        |
| 2      | Reanudar | Continúa la ejecución |

Esto evita transiciones inválidas y simplifica el control de la interfaz.

### Estadísticas durante la pausa

Al pausar el juego se muestra:

* La serpiente viva más larga.
* La primera serpiente en morir.

Para identificar fácilmente las serpientes se asignó un color fijo a cada una:

* Verde
* Azul
* Magenta
* Dorada
* Roja

La serpiente viva más larga se calcula utilizando:

```java
Comparator.comparingInt(Snake::length)
```

La primera serpiente en morir se calcula utilizando:

```java
Comparator.comparingLong(Snake::deathTimeMs)
```

### Consistencia visual (Sin Tearing)

Se buscó garantizar que la información mostrada al pausar corresponda exactamente al estado visible en pantalla.

Para ello:

1. `GameClock` utiliza un `AtomicReference<GameState>`.
2. No se generan nuevos repaints cuando el estado es `PAUSED`.
3. Se utiliza `SwingUtilities.invokeLater()` para ejecutar el último repaint y posteriormente calcular las estadísticas.

```java
clock.pause();

SwingUtilities.invokeLater(() -> {
    gamePanel.repaint();
    updateStatsLabel();
});
```

De esta manera el frame congelado y las estadísticas pertenecen al mismo estado consistente del juego.

---

## 4. Robustez bajo carga

Las pruebas se realizaron aumentando el número de serpientes mediante:

```bash
mvn -q -DskipTests exec:java -Dsnakes=20
```

### ConcurrentModificationException

Los métodos de consulta de `Board` retornan copias defensivas:

```java
return new HashSet<>(mice);
return new HashMap<>(teleports);
```

La interfaz gráfica nunca itera directamente sobre las estructuras compartidas.

Resultado:

* No se presentaron `ConcurrentModificationException`.

### Lecturas inconsistentes

Todos los accesos al estado interno de una serpiente se encuentran sincronizados.

Resultado:

* No se observaron inconsistencias visuales.

### Deadlocks

El orden de adquisición de locks es siempre consistente:

```text
Board -> Snake
```

Nunca ocurre:

```text
Snake -> Board
```

Resultado:

* No se detectaron deadlocks.

### Turbo y Teletransportadores

La lógica de movimiento se encuentra protegida dentro de:

```java
public synchronized MoveResult step(...)
```

Solo una serpiente puede modificar el tablero a la vez.

Resultado:

* No se presentaron carreras relacionadas con turbo o teletransportadores.

### Aparición de elementos sobre serpientes

Se identificó un problema adicional cuando el número de serpientes era elevado.

La implementación original podía generar ratones, obstáculos o turbo sobre cuerpos ya ocupados.

Se modificó `randomEmpty()` para excluir todas las posiciones ocupadas por serpientes vivas.

Resultado:

* Los elementos siempre aparecen en celdas libres.

---

# Resultados obtenidos

Después de las modificaciones realizadas, el juego:

* Ejecuta múltiples serpientes concurrentemente utilizando Virtual Threads.
* No presenta condiciones de carrera detectadas.
* No presenta `ConcurrentModificationException`.
* No presenta deadlocks.
* Permite iniciar, pausar y reanudar de forma consistente.
* Muestra estadísticas correctas durante la pausa.
* Soporta cargas altas (`N >= 20`) sin fallos de concurrencia.

---

# Conclusiones

* La sincronización debe aplicarse únicamente sobre las regiones críticas estrictamente necesarias para no afectar el paralelismo.
* El uso de métodos sincronizados en `Snake` permitió proteger el estado compartido sin introducir bloqueos excesivos.
* Las copias defensivas son una estrategia efectiva para evitar modificaciones concurrentes durante la renderización.
* La coordinación entre el reloj del juego y la interfaz gráfica permitió obtener una pausa consistente visualmente.
* Las pruebas con un número elevado de serpientes confirmaron la robustez de la solución implementada.

# References

1. Benavides Navarro, L. D., & Gualtero Martínez, R. H. (2024). *Concurrency and Threads in Java and Go* [Course slides].

2. OpenAI. (2026). *ChatGPT* (GPT-5.5 version) [Large Language Model]. https://chatgpt.com/ (Used primarily as a support tool.)

3. Oracle. (2024). Java tutorials: Concurrency. Oracle Documentation. https://docs.oracle.com/javase/tutorial/essential/concurrency/

4. OpenJDK. (2024). Project Loom: Virtual threads and structured concurrency. OpenJDK. https://openjdk.org/projects/loom/

