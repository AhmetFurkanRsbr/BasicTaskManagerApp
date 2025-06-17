package ProjectGUI;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class GraphPanel extends JPanel {

    private List<Double> cpuData;
    private List<Double> memoryData;

    public GraphPanel(List<Double> cpuData, List<Double> memoryData) {
        this.cpuData = cpuData;
        this.memoryData = memoryData;
        setPreferredSize(new Dimension(600, 300)); // Orta alanın boyutunu buradan kontrol edebiliriz
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Orta kısmı kareli yapıyoruz
        g.setColor(new Color(240, 240, 240));  // Soluk gri arka plan
        g.fillRect(0, 0, getWidth(), getHeight());

        // Grafik alanlarının alt kısmını kırmızı (CPU) ve mavi (Bellek) ile boyayacağız
        drawGrid(g);

        // Ekran yüksekliğinin ortasında CPU grafiğini başlatıyoruz
        int cpuGraphYOffset = getHeight() / 2 - 250; // CPU grafiği için ortadan başlama

        // CPU grafiğini üstte çizme
        drawTitle(g, "CPU Kullanımı", cpuGraphYOffset, Color.RED); // CPU başlığı
        drawGraph(g, cpuData, cpuGraphYOffset, Color.RED); // CPU grafiği
        fillGraphArea(g, cpuData, cpuGraphYOffset, Color.RED); // CPU altını renk ile doldurma

        // Bellek grafiğini CPU'nun hemen altında çizme
        int memoryGraphYOffset = cpuGraphYOffset + 330; // Bellek grafiği için yer ayıralım

        drawTitle(g, "Bellek Kullanımı", memoryGraphYOffset, Color.BLUE); // Bellek başlığı
        drawGraph(g, memoryData, memoryGraphYOffset, Color.BLUE); // Bellek grafiği
        fillGraphArea(g, memoryData, memoryGraphYOffset, Color.BLUE); // Bellek altını renk ile doldurma
    }

    private void drawTitle(Graphics g, String title, int yOffset, Color color) {
        g.setColor(color);
        g.setFont(new Font("Tahoma", Font.BOLD, 14));
        g.drawString(title, 10, yOffset + 15); // Başlık yerleşimi
    }

    private void drawGraph(Graphics g, List<Double> data, int yOffset, Color color) {
        if (data == null || data.isEmpty()) return;

        int graphWidth = getWidth(); // Grafik genişliği, panelin genişliği kadar olacak
        int graphHeight = 120; // Grafik yüksekliği
        g.setColor(color);

        int previousX = -1;
        int previousY = -1;
        for (int i = 0; i < data.size(); i++) {
            int x = (int) ((i / (double) data.size()) * graphWidth);
            int y = (int) ((1 - data.get(i) / 100.0) * graphHeight) + yOffset + 20; // Veriyi normalize et

            if (previousX != -1 && previousY != -1) {
                g.drawLine(previousX, previousY, x, y);
            }

            previousX = x;
            previousY = y;
        }
    }

    private void fillGraphArea(Graphics g, List<Double> data, int yOffset, Color color) {
        if (data == null || data.isEmpty()) return;

        int graphWidth = getWidth(); // Grafik genişliği, panelin genişliği kadar olacak
        int graphHeight = 120;
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 100)); // %40 opaklıkla

        int previousX = -1;
        int previousY = -1;
        for (int i = 0; i < data.size(); i++) {
            int x = (int) ((i / (double) data.size()) * graphWidth);
            int y = (int) ((1 - data.get(i) / 100.0) * graphHeight) + yOffset + 20;

            if (previousX != -1 && previousY != -1) {
                g.fillPolygon(new int[]{previousX, previousX, x, x},
                        new int[]{previousY, graphHeight + yOffset + 20, graphHeight + yOffset + 20, y},
                        4);
            }

            previousX = x;
            previousY = y;
        }
    }

    private void drawGrid(Graphics g) {
        g.setColor(new Color(0, 0, 0, 50)); // Kareli grid çizimi
        int gridSize = 20;
        for (int x = 0; x < getWidth(); x += gridSize) {
            g.drawLine(x, 0, x, getHeight());
        }
        for (int y = 0; y < getHeight(); y += gridSize) {
            g.drawLine(0, y, getWidth(), y);
        }
    }

    // Verileri güncellemek için bu metod kullanılır
    public void updateData(List<Double> newCpuData, List<Double> newMemoryData) {
        this.cpuData = newCpuData;
        this.memoryData = newMemoryData;
        repaint();
    }
}
