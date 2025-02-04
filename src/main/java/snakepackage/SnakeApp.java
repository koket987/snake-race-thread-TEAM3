package snakepackage;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import enums.GridSize;

public class SnakeApp {

    private static SnakeApp app;
    public static final int MAX_THREADS = 8;
    Snake[] snakes = new Snake[MAX_THREADS];
    private static final Cell[] spawn = {
            new Cell(1, (GridSize.GRID_HEIGHT / 2) / 2),
            new Cell(GridSize.GRID_WIDTH - 2, 3 * (GridSize.GRID_HEIGHT / 2) / 2),
            new Cell(3 * (GridSize.GRID_WIDTH / 2) / 2, 1),
            new Cell((GridSize.GRID_WIDTH / 2) / 2, GridSize.GRID_HEIGHT - 2),
            new Cell(1, 3 * (GridSize.GRID_HEIGHT / 2) / 2),
            new Cell(GridSize.GRID_WIDTH - 2, (GridSize.GRID_HEIGHT / 2) / 2),
            new Cell((GridSize.GRID_WIDTH / 2) / 2, 1),
            new Cell(3 * (GridSize.GRID_WIDTH / 2) / 2, GridSize.GRID_HEIGHT - 2)
    };
    private JFrame frame;
    private static Board board;
    private Thread[] threads = new Thread[MAX_THREADS];

    // Variables de control global para la pausa
    public static volatile boolean paused = false;
    public static final Object pauseLock = new Object();

    // Componentes de la interfaz para mostrar estadísticas
    private JLabel lblLongestSnake;
    private JLabel lblWorstSnake;

    // Botones de control
    private JButton btnStart;
    private JButton btnPause;
    private JButton btnResume;

    // Lista para registrar las serpientes muertas (para determinar la peor)
    private List<Snake> deadSnakes = Collections.synchronizedList(new ArrayList<Snake>());

    public SnakeApp() {
        Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
        frame = new JFrame("The Snake Race");
        frame.setLayout(new BorderLayout());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(GridSize.GRID_WIDTH * GridSize.WIDTH_BOX + 17,
                GridSize.GRID_HEIGHT * GridSize.HEIGH_BOX + 40);
        frame.setLocation(dimension.width / 2 - frame.getWidth() / 2,
                dimension.height / 2 - frame.getHeight() / 2);
        board = new Board();
        frame.add(board, BorderLayout.CENTER);

        // Panel de controles y estadísticas
        JPanel controlPanel = new JPanel(new FlowLayout());
        btnStart = new JButton("Iniciar");
        btnPause = new JButton("Pausar");
        btnResume = new JButton("Reanudar");
        lblLongestSnake = new JLabel("Serpiente viva más larga: N/A");
        lblWorstSnake = new JLabel("Peor serpiente: N/A");

        controlPanel.add(btnStart);
        controlPanel.add(btnPause);
        controlPanel.add(btnResume);
        controlPanel.add(lblLongestSnake);
        controlPanel.add(lblWorstSnake);
        frame.add(controlPanel, BorderLayout.SOUTH);

        // Configurar acciones de los botones
        btnStart.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startGame();
                btnStart.setEnabled(false);
            }
        });
        btnPause.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pauseGame();
                updateStatistics();
            }
        });
        btnResume.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resumeGame();
            }
        });
    }

    public static void main(String[] args) {
        app = new SnakeApp();
        app.frame.setVisible(true);
    }

    // Método para iniciar el juego (crea y arranca los hilos de las serpientes)
    private void startGame() {
        for (int i = 0; i < MAX_THREADS; i++) {
            try {
                if (snakes[i] == null) {
                    snakes[i] = new Snake(i + 1, spawn[i], i + 1);
                    snakes[i].addObserver(board);
                    // Registrar cuando una serpiente muere
                    snakes[i].setDeathListener(new Snake.DeathListener() {
                        @Override
                        public void snakeDied(Snake snake) {
                            synchronized (deadSnakes) {
                                deadSnakes.add(snake);
                            }
                        }
                    });
                }

                if (threads[i] == null) {
                    threads[i] = new Thread(snakes[i]);
                    threads[i].start();
                }
            } catch (Exception e) {
                System.err.println("Error al inicializar la serpiente " + i + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // Pausa el juego: se setea el flag global y los hilos entran en espera
    private void pauseGame() {
        paused = true;
    }

    // Reanuda el juego: se notifica a todos los hilos que estaban en espera
    private void resumeGame() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
    }

    // Actualiza las estadísticas en la interfaz
    private void updateStatistics() {
        // Determinar la serpiente viva más larga (mayor tamaño de cuerpo)
        Snake longestSnake = null;
        int maxSize = 0;
        for (Snake s : snakes) {
            if (!s.isSnakeEnd()) {
                int size = s.getBodySize();
                if (size > maxSize) {
                    maxSize = size;
                    longestSnake = s;
                }
            }
        }
        // Determinar la peor serpiente (la que murió primero)
        Snake worstSnake = null;
        long firstDeath = Long.MAX_VALUE;
        synchronized(deadSnakes) {
            for (Snake s : deadSnakes) {
                if (s.getDeathTime() > 0 && s.getDeathTime() < firstDeath) {
                    firstDeath = s.getDeathTime();
                    worstSnake = s;
                }
            }
        }
        lblLongestSnake.setText("Serpiente viva más larga: " +
                (longestSnake != null ? ("ID " + longestSnake.getIdt() + " (tamaño " + maxSize + ")") : "N/A"));
        lblWorstSnake.setText("Peor serpiente: " +
                (worstSnake != null ? ("ID " + worstSnake.getIdt()) : "N/A"));
    }

    public static SnakeApp getApp() {
        return app;
    }
}
