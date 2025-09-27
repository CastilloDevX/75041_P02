/*
 * Script desarrollado por: Jose Manuel Castillo Queh
 * Ingeniería en tecnologías de software - 5A
*/


import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.util.Random;
import javax.swing.*;


public class MonteCarlo extends JPanel {

    // =============== Parámetros de simulación ===============
    // Total de puntos a lanzar
    private static final int TOTAL_POINTS = 100_000;
    private static final boolean ANIMATE = false;
    private static final int POINTS_PER_TICK = 2_000;
    private static final int TICK_MS = 30;

    private static final int PREF_SIZE = 500;

    // =============== Estado de la simulación ===============
    private final Random rng = new Random();
    private final double[] xs = new double[TOTAL_POINTS];
    private final double[] ys = new double[TOTAL_POINTS];
    private final boolean[] inside = new boolean[TOTAL_POINTS];

    private int generated = 0;     // puntos generados hasta ahora
    private int hits = 0;          // puntos dentro del cuarto de círculo
    private double piEstimate = 0; // estimación actual de π

    private final DecimalFormat df = new DecimalFormat("0.000000");
    private Timer timer;

    public MonteCarlo() {
        setBackground(Color.white);
        setOpaque(true);
        setPreferredSize(new Dimension(PREF_SIZE, PREF_SIZE + 100)); // espacio para UI

        if (ANIMATE) {
            timer = new Timer(TICK_MS, e -> step());
            timer.start();
        } else {
            // Genera todo de una vez y repinta
            while (generated < TOTAL_POINTS) {
                addRandomPoint();
            }
            updatePi();
        }

        // Permite guardar una imagen con la tecla 'S'
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyChar() == 's' || e.getKeyChar() == 'S') {
                    saveSnapshot();
                }
            }
        });
    }

    private void step() {
        int batch = Math.min(POINTS_PER_TICK, TOTAL_POINTS - generated);
        for (int i = 0; i < batch; i++) addRandomPoint();
        updatePi();
        repaint();

        if (generated >= TOTAL_POINTS && timer != null) {
            timer.stop();
            System.out.println("Estimación final de π = " + df.format(piEstimate));
        }
    }

    private void addRandomPoint() {
        double x = rng.nextDouble();
        double y = rng.nextDouble();
        boolean in = (x * x + y * y) <= 1.0;

        xs[generated] = x;
        ys[generated] = y;
        inside[generated] = in;

        if (in) hits++;
        generated++;
    }

    private void updatePi() {
        if (generated > 0) {
            piEstimate = 4.0 * hits / generated;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Render bonito :)
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Layout: parte superior = área de dibujo cuadrada; inferior =>> panel de texto
        int margin = 40;
        int drawSize = Math.min(w - 2 * margin, h - 160);
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

        // Cuarto de círculo de radio 1 (El cuadrante de positivos)
        g2.setColor(new Color(220, 235, 255));
        g2.fillArc(left, top, drawSize * 2, drawSize * 2, 90, 90); // cuarto superior-izquierdo
        g2.setColor(new Color(120, 160, 255));
        g2.drawArc(left, top, drawSize * 2, drawSize * 2, 90, 90);

        // Ejes/etiquetas simples
        g2.setColor(new Color(160, 160, 160));
        g2.drawString("0", left - 12, top + drawSize + 12);
        g2.drawString("1", left + drawSize - 4, top + drawSize + 12);
        g2.drawString("1", left - 16, top + 4);

        // Dibujo de puntos
        // Para grandes N, dibujar puntos como pixeles es más rápido
        for (int i = 0; i < generated; i++) {
            int px = left + (int) Math.round(xs[i] * drawSize);
            int py = top + drawSize - (int) Math.round(ys[i] * drawSize);

            if (inside[i]) {
                g2.setColor(new Color(30, 136, 229));  // azul para dentro
            } else {
                g2.setColor(new Color(239, 83, 80));   // rojo para fuera
            }
            g2.fillRect(px, py, 2, 2);
        }

        // Panel de info
        int infoTop = top + drawSize + 30;
        g2.setColor(new Color(33, 33, 33));
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 18f));
        g2.drawString("Estimación de π por Monte Carlo", left, infoTop);

        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 14f));
        String s1 = "Puntos generados: " + generated + " / " + TOTAL_POINTS;
        String s2 = "Aciertos (x² + y² ≤ 1): " + hits;
        String s3 = "π ≈ " + df.format(piEstimate) + "    (error = " +
                (generated == 0 ? "-" : df.format(Math.abs(Math.PI - piEstimate))) + ")";
        String s4 = "Controles: " + (ANIMATE ? "Animación ON" : "Animación OFF") + " | Guardar imagen: tecla 'S'";

        g2.drawString(s1, left, infoTop + 24);
        g2.drawString(s2, left, infoTop + 44);
        g2.drawString(s3, left, infoTop + 64);
        g2.setColor(new Color(120, 120, 120));
        g2.drawString(s4, left, infoTop + 84);

        g2.dispose();
    }

    private void saveSnapshot() {
        // Guarda una captura PNG del componente
        String filename = "montecarlo_pi.png";
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

    // =============== Arranque de la app ===============
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Monte Carlo π");
            MonteCarlo panel = new MonteCarlo();
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(panel);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);

            // También imprime en consola para referencia
            new Thread(() -> {
                // Si está animando, imprime cada cierto progreso
                int lastPrinted = 0;
                while (panel.generated < TOTAL_POINTS) {
                    int pct = (int) (100.0 * panel.generated / TOTAL_POINTS);
                    if (pct >= lastPrinted + 10) {
                        System.out.println("Progreso " + pct + "%, π ≈ " + panel.df.format(panel.piEstimate));
                        lastPrinted = pct;
                    }
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                System.out.println("Finalizado. π ≈ " + panel.df.format(panel.piEstimate));
            }).start();
        });
    }
}
