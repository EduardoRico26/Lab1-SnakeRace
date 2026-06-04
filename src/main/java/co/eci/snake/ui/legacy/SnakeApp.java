package co.eci.snake.ui.legacy;

import co.eci.snake.concurrency.SnakeRunner;
import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameClock;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public final class SnakeApp extends JFrame {

  private final Board board;
  private final GamePanel gamePanel;
  private final JButton actionButton;
  private final JLabel statsLabel;
  private final GameClock clock;
  private final List<Snake> snakes = new ArrayList<>();

  // 0 = nunca iniciado, 1 = corriendo, 2 = pausado
  private int uiState = 0;

  public SnakeApp() {
    super("The Snake Race");
    this.board = new Board(35, 28);

    int N = Integer.getInteger("snakes", 2);
    for (int i = 0; i < N; i++) {
      int x = 2 + (i * 3) % board.width();
      int y = 2 + (i * 2) % board.height();
      var dir = Direction.values()[i % Direction.values().length];
      snakes.add(Snake.of(x, y, dir));
    }

    this.gamePanel    = new GamePanel(board, () -> snakes);
    this.actionButton = new JButton("Iniciar");
    this.statsLabel   = new JLabel(" ", SwingConstants.CENTER);
    statsLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
    statsLabel.setForeground(new Color(40, 40, 120));

    JPanel south = new JPanel(new BorderLayout(4, 4));
    south.add(actionButton, BorderLayout.NORTH);
    south.add(statsLabel,   BorderLayout.CENTER);

    setLayout(new BorderLayout());
    add(gamePanel, BorderLayout.CENTER);
    add(south,     BorderLayout.SOUTH);

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    pack();
    setLocationRelativeTo(null);

    this.clock = new GameClock(60, () -> SwingUtilities.invokeLater(gamePanel::repaint));

    var exec = Executors.newVirtualThreadPerTaskExecutor();
    snakes.forEach(s -> exec.submit(new SnakeRunner(s, board, snakes)));

    actionButton.addActionListener((ActionEvent e) -> handleButton());

    setVisible(true);
  }

  private void handleButton() {
    switch (uiState) {
      case 0 -> {
        uiState = 1;
        actionButton.setText("Pausar");
        statsLabel.setText(" ");
        clock.start();
      }
      case 1 -> {
        clock.pause();
        // invokeLater encola el repaint+stats DESPUÉS de cualquier repaint
        // ya pendiente en la EDT, garantizando un frame coherente sin tearing
        SwingUtilities.invokeLater(() -> {
          gamePanel.repaint();
          updateStatsLabel();
        });
        uiState = 2;
        actionButton.setText("Reanudar");
      }
      case 2 -> {
        statsLabel.setText(" ");
        uiState = 1;
        actionButton.setText("Pausar");
        clock.resume();
      }
    }
  }

  private void updateStatsLabel() {
    String[] colorNames = { "Verde", "Azul", "Magenta", "Dorada", "Roja" };

    Snake longest = snakes.stream()
        .filter(Snake::isAlive)
        .max(Comparator.comparingInt(Snake::length))
        .orElse(null);

    Snake worst = snakes.stream()
        .filter(s -> !s.isAlive())
        .min(Comparator.comparingLong(Snake::deathTimeMs))
        .orElse(null);

    String longestTxt = (longest == null)
        ? "ninguna viva"
        : "Serpiente " + colorNames[snakes.indexOf(longest) % colorNames.length]
          + " (longitud " + longest.length() + ")";

    String worstTxt = (worst == null)
        ? "ninguna muerta aún"
        : "Serpiente " + colorNames[snakes.indexOf(worst) % colorNames.length]
          + " (murió primero)";

    statsLabel.setText(
        "<html><center>PAUSADO &nbsp;|&nbsp; "
        + " Más larga: <b>" + longestTxt + "</b>"
        + " &nbsp;|&nbsp; "
        + " Peor: <b>" + worstTxt + "</b>"
        + "</center></html>");
  }

  public static final class GamePanel extends JPanel {
    private final Board board;
    private final Supplier snakesSupplier;
    private final int cell = 20;

    @FunctionalInterface
    public interface Supplier { List<Snake> get(); }

    public GamePanel(Board board, Supplier snakesSupplier) {
      this.board          = board;
      this.snakesSupplier = snakesSupplier;
      setPreferredSize(new Dimension(board.width() * cell + 1, board.height() * cell + 40));
      setBackground(Color.WHITE);
    }

    private static final Color[] COLORS = {
      new Color(0, 170,   0),
      new Color(0, 120, 220),
      new Color(200,   0, 200),
      new Color(220, 160,   0),
      new Color(200,  50,  50),
    };

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      var g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      g2.setColor(new Color(220, 220, 220));
      for (int x = 0; x <= board.width();  x++) g2.drawLine(x * cell, 0, x * cell, board.height() * cell);
      for (int y = 0; y <= board.height(); y++) g2.drawLine(0, y * cell, board.width() * cell, y * cell);

      g2.setColor(new Color(255, 102, 0));
      for (var p : board.obstacles()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillRect(x + 2, y + 2, cell - 4, cell - 4);
        g2.setColor(Color.RED);
        g2.drawLine(x + 4, y + 4,  x + cell - 6, y + 4);
        g2.drawLine(x + 4, y + 8,  x + cell - 6, y + 8);
        g2.drawLine(x + 4, y + 12, x + cell - 6, y + 12);
        g2.setColor(new Color(255, 102, 0));
      }

      g2.setColor(Color.BLACK);
      for (var p : board.mice()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillOval(x + 4, y + 4, cell - 8, cell - 8);
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 8, y + 8, cell - 16, cell - 16);
        g2.setColor(Color.BLACK);
      }

      Map<Position, Position> tp = board.teleports();
      g2.setColor(Color.RED);
      for (var entry : tp.entrySet()) {
        Position from = entry.getKey();
        int x = from.x() * cell, y = from.y() * cell;
        int[] xs = { x + 4, x + cell - 4, x + cell - 10, x + cell - 10, x + 4 };
        int[] ys = { y + cell / 2, y + cell / 2, y + 4, y + cell - 4, y + cell / 2 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      g2.setColor(Color.BLACK);
      for (var p : board.turbo()) {
        int x = p.x() * cell, y = p.y() * cell;
        int[] xs = { x + 8, x + 12, x + 10, x + 14, x + 6, x + 10 };
        int[] ys = { y + 2,  y + 2,  y + 8,  y + 8,  y + 16, y + 10 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      var snakes = snakesSupplier.get();
      for (int idx = 0; idx < snakes.size(); idx++) {
        Snake s = snakes.get(idx);
        if (!s.isAlive()) continue;
        Color base = COLORS[idx % COLORS.length];
        var body = s.snapshot().toArray(new Position[0]);
        for (int i = 0; i < body.length; i++) {
          var p = body[i];
          int shade = Math.max(0, 40 - i * 4);
          g2.setColor(new Color(
            Math.min(255, base.getRed()   + shade),
            Math.min(255, base.getGreen() + shade),
            Math.min(255, base.getBlue()  + shade)));
          g2.fillRect(p.x() * cell + 2, p.y() * cell + 2, cell - 4, cell - 4);
        }
      }
      g2.dispose();
    }
  }

  public static void launch() {
    SwingUtilities.invokeLater(SnakeApp::new);
  }
}