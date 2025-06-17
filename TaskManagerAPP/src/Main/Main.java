package Main;

import ProjectGUI.ProjeGUI;
import javax.swing.*;


public class Main {

    public static void main(String[] args) {
        /*
         GUI işlemlerinin doğru thread'de çalışmasını sağlamak için kullanılır ve bu,
         Swing uygulamalarında kritik bir kuraldır.
        */
        SwingUtilities.invokeLater(ProjeGUI::new);
    }
}
