/*
 * Script desarrollado por: Jose Manuel Castillo Queh
 * Ingeniería en tecnologías de software - 5A
*/


import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;
import javax.swing.*;

public class MonteCarloPiConcurrent extends JPanel {

    // ====== Parámetros ======
    private static final int TOTAL_POINTS = 100_000;   // cambia para más precisión
    private static final int PREF_SIZE = 720;            // tamaño del lienzo
    private static final int UI_FOOTER = 110;            // espacio para info
    private static final int TICK_MS = 33;               // ~30 FPS
    private static final boolean SHOW_ANIMATION = false;  // animación en vivo

    // Concurrencia
    private static final int N_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors());
    private static final int BLOCK_SIZE = 4_000;         // puntos por bloque (trade-off animación vs overhead)

    // ====== Estado compartido ======
    private final double[] xs = new double[TOTAL_POINTS];
    private final double[] ys = new double[TOTAL_POINTS];
    private final boolean[] inside = new boolean[TOTAL_POINTS];

    // Contadores concurrentes
    private final AtomicInteger written = new AtomicInteger(0); // puntos ya escritos en arrays
    private final LongAdder hits = new LongAdder();             // aciertos (x^2 + y^2 <= 1)
    private final AtomicInteger nextIndex = new AtomicInteger(0); // asignador de bloques

    // UI / formateo
    private final DecimalFormat df = new DecimalFormat("0.000000");
    private volatile double piEstimate = 0.0;

    // Ejecutores
    private ExecutorService pool;
    private Timer repaintTimer;

    public MonteCarloPiConcurrent() {
        setBackground(Color.white);
        setPreferredSize(new Dimension(PREF_SIZE, PREF_SIZE + UI_FOOTER));
        setOpaque(true);

        // Tecla 'S' para guardar captura
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyChar() == 's' || e.getKeyChar() == 'S') saveSnapshot();
            }
        });

        // Lanzar cómputo concurrente
        startComputation();

        // Timer de repintado/estimación
        repaintTimer = new Timer(TICK_MS, e -> {
            int n = written.get();
            if (n > 0) {
                piEstimate = 4.0 * (hits.sum() / (double) n);
            }
            repaint();
            if (n >= TOTAL_POINTS) {
                repaintTimer.stop();
                System.out.println("Finalizado. π ≈ " + df.format(piEstimate));
            }
        });
        repaintTimer.start();
    }

    private void startComputation() {
        pool = Executors.newFixedThreadPool(N_THREADS, r -> {
            Thread t = new Thread(r, "mc-worker");
            t.setDaemon(true);
            return t;
        });

        // Enviar N_THREADS tareas idénticas (trabajo se reparte por bloques)
        for (int t = 0; t < N_THREADS; t++) {
            pool.submit(this::workerLoop);
        }

        // Cierre ordenado cuando terminen
        pool.submit(() -> {
            pool.shutdown();
            try {
                pool.awaitTermination(10, TimeUnit.MINUTES);
            } catch (InterruptedException ignored) {}
        });
    }

    private void workerLoop() {
        // Cada hilo reclama bloques de tamaño BLOCK_SIZE
        while (true) {
            int start = nextIndex.getAndAdd(BLOCK_SIZE);
            if (start >= TOTAL_POINTS) break;
            int end = Math.min(start + BLOCK_SIZE, TOTAL_POINTS);

            int localHits = 0;
            // ThreadLocalRandom para alto rendimiento
            java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();

            // Escribimos directamente en el segmento [start, end)
            for (int i = start; i < end; i++) {
                double x = rnd.nextDouble();
                double y = rnd.nextDouble();
                boolean in = (x * x + y * y) <= 1.0;
                xs[i] = x;
                ys[i] = y;
                inside[i] = in;
                if (in) localHits++;
            }

            hits.add(localHits);
            written.addAndGet(end - start);

            // Si la animación está activa, pequeños yields ayudan a la suavidad del EDT
            if (SHOW_ANIMATION) {
                LockSupport.parkNanos(200_000); // 0.2 ms aprox
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        int margin = 40;
        int drawSize = Math.min(w - 2 * margin, h - (UI_FOOTER + margin));
        int left = (w - drawSize) / 2;
        int top = margin;

        // Fondo del área de dibujo
        g2.setColor(new Color(245, 245, 245));
        g2.fillRoundRect(left - 10, top - 10, drawSize + 20, drawSize + 20, 16, 16);

        // Cuadrado [0,1]x[0,1]
        g2.setColor(Color.white);
        g2.fillRect(left, top, drawSize, drawSize);
        g2.setColor(new Color(200, 200, 200));
        g2.drawRect(left, top, drawSize, drawSize);

        // Cuarto de círculo
        g2.setColor(new Color(220, 235, 255));
        g2.fillArc(left, top, drawSize * 2, drawSize * 2, 90, 90);
        g2.setColor(new Color(120, 160, 255));
        g2.drawArc(left, top, drawSize * 2, drawSize * 2, 90, 90);

        // Ejes/etiquetas
        g2.setColor(new Color(160, 160, 160));
        g2.drawString("0", left - 12, top + drawSize + 12);
        g2.drawString("1", left + drawSize - 4, top + drawSize + 12);
        g2.drawString("1", left - 16, top + 4);

        // Dibujo de puntos ya generados
        int n = written.get();
        for (int i = 0; i < n; i++) {
            int px = left + (int) Math.round(xs[i] * drawSize);
            int py = top + drawSize - (int) Math.round(ys[i] * drawSize);
            g2.setColor(inside[i] ? new Color(30, 136, 229) : new Color(239, 83, 80));
            g2.fillRect(px, py, 2, 2);
        }

        // Panel de info
        int infoTop = top + drawSize + 28;
        g2.setColor(new Color(33, 33, 33));
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 18f));
        g2.drawString("π por Monte Carlo (Concurrente)", left, infoTop);

        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 14f));
        String s1 = "Puntos generados: " + n + " / " + TOTAL_POINTS + "   |   Hilos: " + N_THREADS + "   |   Bloque: " + BLOCK_SIZE;
        String s2 = "Aciertos: " + hits.sum();
        String s3 = n == 0 ? "π ≈ -" : ("π ≈ " + df.format(piEstimate) + "    (error = " + df.format(Math.abs(Math.PI - piEstimate)) + ")");
        String s4 = "Tecla 'S' para guardar captura PNG";

        g2.drawString(s1, left, infoTop + 24);
        g2.drawString(s2, left, infoTop + 44);
        g2.drawString(s3, left, infoTop + 64);
        g2.setColor(new Color(120, 120, 120));
        g2.drawString(s4, left, infoTop + 84);

        g2.dispose();
    }

    private void saveSnapshot() {
        String filename = "montecarlo_pi_concurrent.png";
        BufferedImage image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        paintAll(g2);
        g2.dispose();
        try {
            javax.imageio.ImageIO.write(image, "png", new java.io.File(filename));
            JOptionPane.showMessageDialog(this, "Imagen guardada: " + filename, "Listo", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al guardar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ====== Main ======
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Monte Carlo π (Concurrente)");
            MonteCarloPiConcurrent panel = new MonteCarloPiConcurrent();
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(panel);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
