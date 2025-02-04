package snakepackage;

import java.util.LinkedList;
import java.util.Observable;
import java.util.Random;
import enums.Direction;
import enums.GridSize;

public class Snake extends Observable implements Runnable {

    private int idt;
    private Cell head;
    private Cell newCell;
    private final LinkedList<Cell> snakeBody = new LinkedList<Cell>();
    private Cell start = null;

    private boolean snakeEnd = false;

    private int direction = Direction.NO_DIRECTION;
    private final int INIT_SIZE = 3;

    private boolean hasTurbo = false;
    private int jumps = 0;
    private boolean isSelected = false;
    private int growing = 0;
    public boolean goal = false;

    // Campo para registrar la hora de muerte (en milisegundos)
    private long deathTime = 0;
    private boolean selected;

    // Interfaz para notificar la muerte
    public interface DeathListener {
        void snakeDied(Snake snake);
    }
    private DeathListener deathListener;

    public void setDeathListener(DeathListener listener) {
        this.deathListener = listener;
    }

    public Snake(int idt, Cell head, int direction) {
        this.idt = idt;
        this.direction = direction;
        generateSnake(head);
    }

    public boolean isSnakeEnd() {
        return snakeEnd;
    }

    // Devuelve el tamaño actual del cuerpo de la serpiente
    public int getBodySize() {
        synchronized(snakeBody) {
            return snakeBody.size();
        }
    }

    public int getIdt() {
        return idt;
    }

    public long getDeathTime() {
        return deathTime;
    }

    private void generateSnake(Cell head) {
        start = head;
        synchronized(snakeBody) {
            snakeBody.add(head);
        }
        growing = INIT_SIZE - 1;
    }

    @Override
    public void run() {
        while (!snakeEnd) {
            // Verificar el estado global de pausa (evitando espera activa)
            synchronized(SnakeApp.pauseLock) {
                while (SnakeApp.paused) {
                    try {
                        SnakeApp.pauseLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

            snakeCalc();

            // Notificar cambios a la GUI
            setChanged();
            notifyObservers();

            try {
                if (hasTurbo) {
                    Thread.sleep(500 / 3);
                } else {
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Registrar el instante de muerte si no se ha registrado aún
        if (deathTime == 0) {
            deathTime = System.currentTimeMillis();
            if (deathListener != null) {
                deathListener.snakeDied(this);
            }
        }

        // Se puede incluir una corrección de dirección si fuera necesaria
        fixDirection(head);
    }

    private void snakeCalc() {
        synchronized(snakeBody) {
            head = snakeBody.peekFirst();
        }
        newCell = head;

        newCell = changeDirection(newCell);

        randomMovement(newCell);

        checkIfFood(newCell);
        checkIfJumpPad(newCell);
        checkIfTurboBoost(newCell);
        checkIfBarrier(newCell);

        synchronized(snakeBody) {
            snakeBody.push(newCell);
            if (growing <= 0) {
                Cell tail = snakeBody.pollLast();
                Board.gameboard[tail.getX()][tail.getY()].freeCell();
            } else {
                growing--;
            }
        }
    }

    private void checkIfBarrier(Cell newCell) {
        if (Board.gameboard[newCell.getX()][newCell.getY()].isBarrier()) {
            System.out.println("[" + idt + "] " + "CRASHED AGAINST BARRIER " + newCell.toString());
            snakeEnd = true;
        }
    }

    private Cell fixDirection(Cell newCell) {
        if (direction == Direction.LEFT && head.getX() + 1 < GridSize.GRID_WIDTH) {
            newCell = Board.gameboard[head.getX() + 1][head.getY()];
        } else if (direction == Direction.RIGHT && head.getX() - 1 >= 0) {
            newCell = Board.gameboard[head.getX() - 1][head.getY()];
        } else if (direction == Direction.UP && head.getY() + 1 < GridSize.GRID_HEIGHT) {
            newCell = Board.gameboard[head.getX()][head.getY() + 1];
        } else if (direction == Direction.DOWN && head.getY() - 1 >= 0) {
            newCell = Board.gameboard[head.getX()][head.getY() - 1];
        }
        randomMovement(newCell);
        return newCell;
    }

    private boolean checkIfOwnBody(Cell newCell) {
        synchronized(snakeBody) {
            for (Cell c : snakeBody) {
                if (newCell.getX() == c.getX() && newCell.getY() == c.getY()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void randomMovement(Cell newCell) {
        Random random = new Random();
        int tmp = random.nextInt(4) + 1;
        if (tmp == Direction.LEFT && direction != Direction.RIGHT) {
            direction = tmp;
        } else if (tmp == Direction.UP && direction != Direction.DOWN) {
            direction = tmp;
        } else if (tmp == Direction.DOWN && direction != Direction.UP) {
            direction = tmp;
        } else if (tmp == Direction.RIGHT && direction != Direction.LEFT) {
            direction = tmp;
        }
    }

    private void checkIfTurboBoost(Cell newCell) {
        if (Board.gameboard[newCell.getX()][newCell.getY()].isTurbo_boost()) {
            for (int i = 0; i < Board.NR_TURBO_BOOSTS; i++) {
                if (Board.turbo_boosts[i] == newCell) {
                    Board.turbo_boosts[i].setTurbo_boost(false);
                    Board.turbo_boosts[i] = new Cell(-5, -5);
                    hasTurbo = true;
                }
            }
            System.out.println("[" + idt + "] " + "GETTING TURBO BOOST " + newCell.toString());
        }
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    private void checkIfJumpPad(Cell newCell) {
        if (Board.gameboard[newCell.getX()][newCell.getY()].isJump_pad()) {
            for (int i = 0; i < Board.NR_JUMP_PADS; i++) {
                if (Board.jump_pads[i] == newCell) {
                    Board.jump_pads[i].setJump_pad(false);
                    Board.jump_pads[i] = new Cell(-5, -5);
                    this.jumps++;
                }
            }
            System.out.println("[" + idt + "] " + "GETTING JUMP PAD " + newCell.toString());
        }
    }

    private void checkIfFood(Cell newCell) {
        Random random = new Random();
        if (Board.gameboard[newCell.getX()][newCell.getY()].isFood()) {
            growing += 3;
            int x = random.nextInt(GridSize.GRID_HEIGHT);
            int y = random.nextInt(GridSize.GRID_WIDTH);
            System.out.println("[" + idt + "] " + "EATING " + newCell.toString());
            for (int i = 0; i < Board.NR_FOOD; i++) {
                if (Board.food[i].getX() == newCell.getX() && Board.food[i].getY() == newCell.getY()) {
                    Board.gameboard[Board.food[i].getX()][Board.food[i].getY()].setFood(false);
                    while (Board.gameboard[x][y].hasElements()) {
                        x = random.nextInt(GridSize.GRID_HEIGHT);
                        y = random.nextInt(GridSize.GRID_WIDTH);
                    }
                    Board.food[i] = new Cell(x, y);
                    Board.gameboard[x][y].setFood(true);
                }
            }
        }
    }

    private Cell changeDirection(Cell newCell) {
        while (direction == Direction.UP && (newCell.getY() - 1) < 0) {
            if ((head.getX() - 1) < 0) {
                direction = Direction.RIGHT;
            } else if ((head.getX() + 1) == GridSize.GRID_WIDTH) {
                direction = Direction.LEFT;
            } else {
                randomMovement(newCell);
            }
        }
        while (direction == Direction.DOWN && (head.getY() + 1) == GridSize.GRID_HEIGHT) {
            if ((head.getX() - 1) < 0) {
                direction = Direction.RIGHT;
            } else if ((head.getX() + 1) == GridSize.GRID_WIDTH) {
                direction = Direction.LEFT;
            } else {
                randomMovement(newCell);
            }
        }
        while (direction == Direction.LEFT && (head.getX() - 1) < 0) {
            if ((newCell.getY() - 1) < 0) {
                direction = Direction.DOWN;
            } else if ((head.getY() + 1) == GridSize.GRID_HEIGHT) {
                direction = Direction.UP;
            } else {
                randomMovement(newCell);
            }
        }
        while (direction == Direction.RIGHT && (head.getX() + 1) == GridSize.GRID_WIDTH) {
            if ((newCell.getY() - 1) < 0) {
                direction = Direction.DOWN;
            } else if ((head.getY() + 1) == GridSize.GRID_HEIGHT) {
                direction = Direction.UP;
            } else {
                randomMovement(newCell);
            }
        }
        switch (direction) {
            case Direction.UP:
                newCell = Board.gameboard[head.getX()][head.getY() - 1];
                break;
            case Direction.DOWN:
                newCell = Board.gameboard[head.getX()][head.getY() + 1];
                break;
            case Direction.LEFT:
                newCell = Board.gameboard[head.getX() - 1][head.getY()];
                break;
            case Direction.RIGHT:
                newCell = Board.gameboard[head.getX() + 1][head.getY()];
                break;
        }
        return newCell;
    }

    public void searchObjective(Cell objective) {
        Random random = new Random();
        if (direction == Direction.LEFT || direction == Direction.RIGHT) {
            if (direction == Direction.LEFT) {
                if (head.getY() > objective.getY()) {
                    direction = Direction.UP;
                } else if (head.getY() < objective.getY()) {
                    direction = Direction.DOWN;
                } else if (head.getY() == objective.getY() && head.getX() < objective.getX()) {
                    direction = random.nextInt(2) + 3;
                }
            } else if (direction == Direction.RIGHT) {
                if (head.getY() > objective.getY()) {
                    direction = Direction.UP;
                } else if (head.getY() < objective.getY()) {
                    direction = Direction.DOWN;
                } else if (head.getY() == objective.getY() && head.getX() > objective.getX()) {
                    direction = random.nextInt(2) + 3;
                }
            }
        } else if (direction == Direction.UP || direction == Direction.DOWN) {
            if (direction == Direction.UP) {
                if (head.getX() > objective.getX()) {
                    direction = Direction.LEFT;
                } else if (head.getX() < objective.getX()) {
                    direction = Direction.RIGHT;
                } else if (head.getX() == objective.getX() && head.getY() < objective.getY()) {
                    direction = random.nextInt(2) + 1;
                }
            } else if (direction == Direction.DOWN) {
                if (head.getX() > objective.getX()) {
                    direction = Direction.LEFT;
                } else if (head.getX() < objective.getX()) {
                    direction = Direction.RIGHT;
                } else if (head.getX() == objective.getX() && head.getY() > objective.getY()) {
                    direction = random.nextInt(2) + 1;
                }
            }
        }
    }

    public LinkedList<Cell> getBody() {
        // Si se necesita acceso concurrente, se debe sincronizar este método
        synchronized(snakeBody) {
            return new LinkedList<Cell>(snakeBody);
        }
    }
}
