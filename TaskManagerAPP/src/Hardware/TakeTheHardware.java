package Hardware;

import java.io.*;
import java.nio.charset.StandardCharsets;//dosya ya da veri akışındaki karakterlerin doğru şekilde okunup yazılabilmesi için gerekli kodlamayı belirtir.

public class TakeTheHardware {

    private Process cppProcess;// Subprocess(bir alt süreç) c++ dosyasının çalıştığı bir işlemdir.
    private final String applicationsFilePath = "open_applications.txt";//Açık dosyaların verilerinin yazıldığı .txt dosyası
    private final String systemUsageFilePath = "system_usage.txt";//Bellek kullanımı verilerinin yazıldığı .txt dosyası

    //C++ .exe uygulamamızın çalıştırılması.
    public void startCppProgram() {
        try {

            //Bu sınıf, işletim sistemi üzerinde Java uygulamasından bağımsız olarak bir alt süreç oluşturur.
            cppProcess = new ProcessBuilder("GetTheHardware/x64/Debug/DonanimdanBilgileriCek.exe").start();
            /*
             Bu satırdaki kodumuz Java kullanılarak bir C++ programını çalıştırmak için kullanılan bir komut.
             ProcessBuilder sınıfı ile belirtilen C++ programı işletim sistemi üzerinde bir alt süreç (subprocess)
             olarak başlatılır
             */
        } catch (IOException e) {
            System.err.println("C++ programı başlatılırken bir sorunla karşılaşıldı: " + e.getMessage());
        }
    }

    //Uygulamanın kapatılması
    public void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Java uygulaması kapatılıyor. C++ işlemi sonlandırılıyor...");

            //Eğer cppProcess boş değilse (null değilse) ve hâlâ çalışıyorsa (isAlive()), süreç sonlandırılır.
            if (cppProcess != null && cppProcess.isAlive()) {
                cppProcess.destroy(); //sürecin çalışmasını durdurur ve kaynaklarını serbest bırakır.
            }

            deleteFile(applicationsFilePath);//Açık dosyalar .txt dosyasını siler.
            deleteFile(systemUsageFilePath);//Bellek kullanımı .txt dosyasını siler.
        }));
    }

    //Belirli bir dosyanın varlığını kontrol eder ve eğer dosya varsa onu silmeye çalışır.
    private void deleteFile(String filePath) {
        File file = new File(filePath);

        //dosyanın belirtilen yolda gerçekten var olup olmadığını kontrol eder.
        //Dosyayı silmeye çalışır ve işlem başarılı olursa true döner.
        if (file.exists() && file.delete()) {
            System.out.println("Dosya başarıyla silindi: " + filePath);
        } else {
            System.out.println("Dosya silinemedi veya bulunamadı: " + filePath);
        }
    }

    //Parametre olarak gönderilen bir dosya yolundaki dosyayı okumak,
    // içeriğini birleştirmek ve bir String olarak döndürmek için kullanılır.
    public String readFile(String filePath) {
        StringBuilder content = new StringBuilder();
        File file = new File(filePath);

        if (file.exists()) {
            //Satır satır okuma işlemi için kullanılır.(dosyanın içeriğini UTF-8 formatında okur)
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                    //Her okunan satır, StringBuilder'a eklenir
                }

            } catch (IOException e) {
                content.append("Dosya okunurken bir hatayla karşılaşıldı: ").append(e.getMessage());
            }
        } else {
            content.append(filePath).append(" dosyası bulunamadı.");
        }

        return content.toString();
    }

    //Parametredeki bir metin satırından (string) CPU kullanım oranını çıkarıp double değer olarak döndürür.
    public double extractCpuUsage(String line) {
        if (line.startsWith("CPU Kullanımı:")) {
            //System.out.println("line: -->"+line);       --> CPU Kullanımı: 8.04028%
            return Double.parseDouble(line.split(":")[1].replace("%", "").trim());
        }
        return -1; // CPU kullanım verisi yoksa -1 döner
    }

    //Parametredeki metin satırından (string) bellek (RAM) kullanım oranını çıkarıp bir double değer olarak döndürür.
    public double extractMemoryUsage(String line) {
        if (line.startsWith("Bellek Kullanımı:")) {
            //System.out.println("line: -->"+line);        -->Bellek Kullanımı: 69.1988% (11161 MB)
            String[] parts = line.split(":")[1].split("\\(");
            return Double.parseDouble(parts[0].replace("%", "").trim());
        }
        return -1; // Bellek kullanım verisi yoksa -1 döner
    }
}
