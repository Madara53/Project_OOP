import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

public class MyFrame extends JPanel {

    Player p = new Player(100, 100);          // สร้างตัวละครที่ตำแหน่ง (100, 100)

    public MyFrame() {
        setPreferredSize(new Dimension(400, 300));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        p.draw((Graphics2D) g);               // สั่งให้ตัวละครวาดตัวเองลงบนพื้นที่นี้
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("เกมของฉัน");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new MyFrame());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}