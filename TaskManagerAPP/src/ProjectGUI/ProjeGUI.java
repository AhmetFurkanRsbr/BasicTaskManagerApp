package ProjectGUI;

import Hardware.TakeTheHardware;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

public class ProjeGUI {

    private final String applicationsFilePath = "open_applications.txt";
    private JTextArea openApplicationsArea;
    private JTextArea systemUsageArea;
    private GraphPanel graphPanel;  // GraphPanel nesnesi
    private List<Double> cpuData;
    private List<Double> memoryData;

    private final TakeTheHardware hardware;

    public ProjeGUI() {
        hardware = new TakeTheHardware();
        JFrame frame = new JFrame("Task Manager App");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocation((Toolkit.getDefaultToolkit().getScreenSize().width - frame.getSize().width )/2,(Toolkit.getDefaultToolkit().getScreenSize().height - frame.getSize().height )/2);


        // Uygulamanın iconu
        ImageIcon icon = new ImageIcon("Images/taskManagerImage.png");
        Image image = icon.getImage();
        frame.setIconImage(image);

        // Layout: GridBagLayout kullanarak paneli düzenliyoruz
        frame.setLayout(new BorderLayout());

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                hardware.addShutdownHook();
            }
        });

        // Açık dosyalar Paneli
        openApplicationsArea = new JTextArea();
        openApplicationsArea.setEditable(false);
        openApplicationsArea.setFont(new Font("Tahoma", Font.PLAIN, 14));
        JScrollPane openAppsScrollPane = new JScrollPane(openApplicationsArea);
        openAppsScrollPane.setBorder(BorderFactory.createTitledBorder("Açık Dosyalar"));

        // Bellek kullanımı Panel
        systemUsageArea = new JTextArea();
        systemUsageArea.setEditable(false);
        systemUsageArea.setFont(new Font("Tahoma", Font.PLAIN, 17));
        JScrollPane systemUsageScrollPane = new JScrollPane(systemUsageArea);
        systemUsageScrollPane.setBorder(BorderFactory.createTitledBorder("Bellek Kullanımı"));

        // Graph Panel
        graphPanel = new GraphPanel(cpuData, memoryData);  // GraphPanel'i ekleyelim
        graphPanel.setPreferredSize(new Dimension(600, 300)); // Orta kısım için sabit boyut

        // Sol ve Sağ Paneller
        frame.add(openAppsScrollPane, BorderLayout.WEST);  // Sol kısım
        frame.add(graphPanel, BorderLayout.CENTER);  // Orta kısım

        // Sağ kısmı ortalamak için GridBagLayout kullanacağız
        JPanel rightPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;  // İlk sütun
        gbc.gridy = 0;  // İlk satır
        gbc.anchor = GridBagConstraints.CENTER;  // Dikeyde ortalamak için
        gbc.weighty = 1.0;  // Yönetime göre dikeyde esneme sağlıyoruz
        rightPanel.add(systemUsageScrollPane, gbc); // "Bellek Kullanımı" paneli

        frame.add(rightPanel, BorderLayout.EAST); // Sağ kısım

        cpuData = new ArrayList<>();
        memoryData = new ArrayList<>();

        frame.setVisible(true);

        hardware.startCppProgram(); // C++ programını başlat

        // UI Güncelleme
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                updateUI();
            }
        }, 0, 1000); // 1 saniyede bir güncelle

        // File Watcher
        startFileWatcher();
    }

    public void updateUI() {
        updateOpenApplications();
        updateSystemUsage();
        graphPanel.updateData(cpuData, memoryData);  // Grafik verilerini güncelle
    }

    private void updateOpenApplications() {
        String content = hardware.readFile("open_applications.txt");
        if (!openApplicationsArea.getText().equals(content)) {
            openApplicationsArea.setText(content);
        }
    }

    private void updateSystemUsage() {
        String content = hardware.readFile("system_usage.txt");
        if (!systemUsageArea.getText().equals(content)) {
            systemUsageArea.setText(content);
        }

        // CPU ve bellek kullanımını ayrıştıralım ve listeye ekleyelim
        for (String line : content.split("\n")) {
            double cpuUsage = hardware.extractCpuUsage(line);
            double memoryUsage = hardware.extractMemoryUsage(line);

            if (cpuUsage >= 0) {
                cpuData.add(cpuUsage);
            }
            if (memoryUsage >= 0) {
                memoryData.add(memoryUsage);
            }
        }

        // Veri sayısını sınırlayalım (örneğin son 100 veri)
        if (cpuData.size() > 100) cpuData = cpuData.subList(cpuData.size() - 100, cpuData.size());
        if (memoryData.size() > 100) memoryData = memoryData.subList(memoryData.size() - 100, memoryData.size());
    }

    public void startFileWatcher() {
        Thread fileWatcherThread = new Thread(() -> {
            try {

                Path path = Paths.get(applicationsFilePath).getParent();
                WatchService watchService = FileSystems.getDefault().newWatchService();
                path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);


                while (true) {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            // When file changes, update the UI
                            updateOpenApplications();
                            updateSystemUsage();
                            graphPanel.repaint();
                        }
                    }
                    key.reset();
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                try {
                    File dosya = new File("open_applications.txt");
                    if (dosya.createNewFile()) {
                        System.out.println("Dosya oluşturuldu: " + dosya.getName());
                    } else {
                        System.out.println("Dosya zaten mevcut.");
                    }
                } catch (IOException e2) {
                    System.out.println("Bir hata oluştu.");
                    e2.printStackTrace();
                }
                try {
                    File dosya = new File("system_usage.txt");
                    if (dosya.createNewFile()) {
                        System.out.println("Dosya oluşturuldu: " + dosya.getName());
                    } else {
                        System.out.println("Dosya zaten mevcut.");
                    }
                } catch (IOException e3) {
                    System.out.println("Bir hata oluştu.");
                    e3.printStackTrace();
                }
                startFileWatcher();
            }
        });
        fileWatcherThread.setDaemon(true);
        fileWatcherThread.start();
    }


}
